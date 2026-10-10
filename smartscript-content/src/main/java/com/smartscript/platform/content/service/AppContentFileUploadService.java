package com.smartscript.platform.content.service;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.file.FileNameLengthLimitExceededException;
import com.ruoyi.common.exception.file.FileSizeLimitExceededException;
import com.ruoyi.common.exception.file.InvalidExtensionException;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;
import com.ruoyi.framework.config.ServerConfig;
import com.smartscript.platform.content.dto.AppUploadResult;
import com.smartscript.platform.content.exception.AppContentUploadException;

/**
 * App 内容域文件上传（B 模块，接口文档 2.9.1）。
 *
 * 为什么不是直接用平台原生的 POST /common/upload：
 *   1. 该端点属若依 PC 凭证链，App Access Token 在其上无法通过鉴权；
 *   2. 其响应为若依 AjaxResult（url 在顶层），与 App 的 {code,message,data} 信封不一致。
 * 本类复用若依的存储与扩展名校验实现（同一上传目录），只把「鉴权域」与「响应信封」
 * 收敛到 App 契约（与 A5 的 AppFileUploadService 同一思路，但放行剧本文档类扩展名）。
 *
 * 边界：
 *   - 按 type 收敛扩展名白名单（cover=图片、script=剧本文档、其它=图片+文档），不接受视频；
 *   - 不落 sys_work_file：该表 work_id 为 NOT NULL，创建作品前无从关联，故 fileId 恒为 null；
 *   - 不把底层异常文案与原始文件名写入日志。
 *
 * @author xiangsipeng
 */
@Service
public class AppContentFileUploadService
{
    private static final Logger log = LoggerFactory.getLogger(AppContentFileUploadService.class);

    /** 上传大小上限：与容器 max-file-size 一致（10MB），超限统一 413。 */
    public static final long MAX_UPLOAD_BYTES = 10 * 1024 * 1024L;

    /** 入参非法 */
    private static final int CODE_BAD_REQUEST = 400;

    /** 内容超限 */
    private static final int CODE_PAYLOAD_TOO_LARGE = 413;

    /** 服务端失败 */
    private static final int CODE_SYSTEM_ERROR = 500;

    /** type：封面（图片） */
    private static final String TYPE_COVER = "cover";

    /** type：剧本（文档） */
    private static final String TYPE_SCRIPT = "script";

    /** 剧本文档扩展名白名单 */
    private static final String[] SCRIPT_EXTENSION = { "txt", "doc", "docx", "pdf" };

    /** 缺省/未知 type 时的合并白名单（图片+文档） */
    private static final String[] DEFAULT_EXTENSION = {
            "bmp", "gif", "jpg", "jpeg", "png", "txt", "doc", "docx", "pdf" };

    private final ServerConfig serverConfig;

    public AppContentFileUploadService(ServerConfig serverConfig)
    {
        this.serverConfig = serverConfig;
    }

    /**
     * 保存上传文件并返回可访问地址。
     *
     * @param file 上传文件（必填）
     * @param type 文件用途（可选：cover/script；其它值按默认白名单处理）
     * @return {fileId=null, url, fileName}
     */
    public AppUploadResult upload(MultipartFile file, String type)
    {
        if (file == null || file.isEmpty())
        {
            throw new AppContentUploadException(CODE_BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES)
        {
            throw new AppContentUploadException(CODE_PAYLOAD_TOO_LARGE, "上传内容超出大小上限");
        }
        final String storedPath;
        try
        {
            // 复用若依上传实现：同一上传目录 + 同一套扩展名/文件名长度校验
            storedPath = FileUploadUtils.upload(RuoYiConfig.getUploadPath(), file, extensionsOf(type));
        }
        catch (InvalidExtensionException | FileSizeLimitExceededException | FileNameLengthLimitExceededException e)
        {
            // 这三类属调用方可纠正的输入问题，归 400；只回通用文案，不透出内部细节
            log.warn("app-content upload rejected: {}", e.getClass().getSimpleName());
            throw new AppContentUploadException(CODE_BAD_REQUEST, "文件类型或大小不符合要求");
        }
        catch (IOException e)
        {
            log.warn("app-content upload failed: {}", e.getClass().getSimpleName());
            throw new AppContentUploadException(CODE_SYSTEM_ERROR, "上传失败，请稍后重试");
        }
        String url = serverConfig.getUrl() + storedPath;
        log.info("app-content uploaded path={}", storedPath);
        AppUploadResult result = new AppUploadResult();
        // 无通用文件表可落 file_id，明确置 null，由客户端以 url 为准
        result.setFileId(null);
        result.setUrl(url);
        result.setFileName(file.getOriginalFilename());
        return result;
    }

    /**
     * 按 type 收敛扩展名白名单；未知或空 type 用图片+文档合并白名单。
     */
    private static String[] extensionsOf(String type)
    {
        if (type == null || type.isEmpty())
        {
            return DEFAULT_EXTENSION;
        }
        return switch (type)
        {
            case TYPE_COVER -> MimeTypeUtils.IMAGE_EXTENSION;
            case TYPE_SCRIPT -> SCRIPT_EXTENSION;
            default -> DEFAULT_EXTENSION;
        };
    }
}