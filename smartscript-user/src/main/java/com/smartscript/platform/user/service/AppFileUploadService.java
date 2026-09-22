package com.smartscript.platform.user.service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
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
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.exception.AppAuthException;

/**
 * A5 App 域图片上传（头像等）。
 *
 * 为什么不是直接用平台原生的 {@code POST /common/upload}：
 *   1. 该端点属于若依 PC 凭证链，App Access Token 在其上无法通过鉴权
 *      （实测 HTTP 200 + body.code=401，见 a5-evidence/30-verify-upload.log）；
 *   2. 它的响应是若依 AjaxResult（url 在顶层），与 App 的
 *      {@code {code,message,data}} 信封不一致，App 客户端按契约只能读到 data。
 * 把 /common/upload 整体移入 App 域会破坏 PC 管理端上传，因此这里提供 App 域专用端点，
 * 复用若依的存储与校验实现（同一上传目录、同一套扩展名与文件名长度校验），
 * 只把「鉴权域」和「响应信封」收敛到 App 契约。
 *
 * 强制边界：
 *   - 只允许图片扩展名（bmp/gif/jpg/jpeg/png）；
 *   - 先校验大小再落盘，超限返回 400 而不是依赖容器异常；
 *   - 不把底层异常文案与原始文件名写入日志。
 */
@Service
public class AppFileUploadService
{
    private static final Logger log = LoggerFactory.getLogger(AppFileUploadService.class);

    /** 头像大小上限：小于容器 10MB 上限，避免走到容器层才被拒。 */
    public static final long MAX_AVATAR_BYTES = 5 * 1024 * 1024L;

    private final ServerConfig serverConfig;

    public AppFileUploadService(ServerConfig serverConfig)
    {
        this.serverConfig = serverConfig;
    }

    /**
     * 保存图片并返回可直接展示与保存的地址。
     *
     * 站点根地址取自当前请求（[ServerConfig#getUrl]），
     * 这样模拟器/真机拿到的地址与其实际访问的后端一致，不写死主机名。
     */
    public Map<String, Object> uploadImage(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "文件不能为空");
        }
        if (file.getSize() > MAX_AVATAR_BYTES)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "图片不能超过 5MB");
        }
        final String storedPath;
        try
        {
            // 复用若依上传实现：同一上传目录与同一套扩展名/文件名长度校验
            storedPath = FileUploadUtils.upload(RuoYiConfig.getUploadPath(), file, MimeTypeUtils.IMAGE_EXTENSION);
        }
        catch (InvalidExtensionException | FileSizeLimitExceededException | FileNameLengthLimitExceededException e)
        {
            // 这三类是调用方可纠正的输入问题，归 400；只回通用文案，不透出内部细节
            log.warn("a5-avatar upload rejected: {}", e.getClass().getSimpleName());
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "图片格式或大小不符合要求");
        }
        catch (IOException e)
        {
            // 落盘失败属于服务端问题，归 500，交由统一错误处理器收敛为系统错误
            log.warn("a5-avatar upload failed: {}", e.getClass().getSimpleName());
            throw new AppAuthException(AppAuthErrorCodes.SYSTEM_ERROR, 500, "上传失败，请稍后重试");
        }
        // storedPath 形如 /profile/upload/2026/09/22/xxx.png
        String url = serverConfig.getUrl() + storedPath;
        log.info("a5-avatar uploaded path={}", storedPath);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", url);
        result.put("path", storedPath);
        return result;
    }
}
