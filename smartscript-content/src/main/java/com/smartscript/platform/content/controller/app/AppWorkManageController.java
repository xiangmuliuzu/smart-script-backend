package com.smartscript.platform.content.controller.app;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppDraftWorkItem;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppReviewStatusDto;
import com.smartscript.platform.content.dto.AppUploadResult;
import com.smartscript.platform.content.dto.AppWorkCreateRequest;
import com.smartscript.platform.content.dto.AppWorkUpdateRequest;
import com.smartscript.platform.content.dto.AppWorkVersionDto;
import com.smartscript.platform.content.exception.AppContentUploadException;
import com.smartscript.platform.content.service.AppContentFileUploadService;
import com.smartscript.platform.content.service.IAppWorkManageService;

/**
 * App 上传与创作（B 模块，接口文档 2.9.1~2.9.9）。
 *
 * 鉴权：全部为 App 私有接口，需 App Access Token。其中 GET 类（草稿箱 / 版本列表 / 审核状态）
 * 与既有书城公开 GET 同前缀，已在 {@code AppAuthSecurityConfig} 的 authenticated 规则中**前置**声明，
 * 避免被 {@code /api/v1/content/works/**} 的游客白名单误放。
 *
 * 路径映射：文档写的是 /api/upload、/api/works...，B 模块既有接口统一收拢在 App 凭证域
 * /api/v1/content/** 下（见 AppAuthSecurityConfig.APP_PATH_PREFIXES），故落此前缀：
 *   2.9.1 POST   /api/v1/content/upload
 *   2.9.2 POST   /api/v1/content/works
 *   2.9.3 PUT    /api/v1/content/works/{workId}
 *   2.9.4 DELETE /api/v1/content/works/{workId}
 *   2.9.5 GET    /api/v1/content/works/drafts
 *   2.9.6 GET    /api/v1/content/works/{workId}/review-status
 *   2.9.7 GET    /api/v1/content/works/{workId}/versions
 *   2.9.8 POST   /api/v1/content/works/{workId}/versions
 *   2.9.9 GET    /api/v1/content/works/versions/{versionId}
 *
 * 字段口径：POST body 兼容文档的 snake_case（category_id / version_desc）与模块内既有 camelCase
 * （categoryId / versionDesc）两种写法；出参沿用 B 模块 App 契约的 camelCase（与 workId/accessScope 等一致）。
 *
 * 归属：一律由服务层取当前身份，不接收请求体 userId；非本人作品按「不存在」（404）处理，
 * 不区分「不存在」与「无权」，避免通过错误信息探测他人作品。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content")
public class AppWorkManageController
{
    /** 作品/版本不存在或非本人 */
    private static final int CODE_NOT_FOUND = 404;

    /** 入参非法 */
    private static final int CODE_BAD_REQUEST = 400;

    /** 服务端失败 */
    private static final int CODE_SYSTEM_ERROR = 500;

    /** 作品标题长度上限（与 sys_work.title varchar(255) 留有冗余，按业务约定收紧到 100） */
    private static final int TITLE_MAX_LENGTH = 100;

    /** 草稿箱默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final IAppWorkManageService workManageService;

    private final AppContentFileUploadService uploadService;

    public AppWorkManageController(IAppWorkManageService workManageService,
            AppContentFileUploadService uploadService)
    {
        this.workManageService = workManageService;
        this.uploadService = uploadService;
    }

    /**
     * 文件上传（2.9.1）
     *
     * @param file 文件（必填）
     * @param type 用途（可选：cover 图片 / script 剧本文档；其它值按图片+文档白名单）
     * @return data = {fileId(=null), url, fileName}；空文件/类型不符 400，超限 413
     */
    @PostMapping("/upload")
    public AppApiResponse<AppUploadResult> upload(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "type", required = false) String type)
    {
        try
        {
            return AppApiResponse.ok(uploadService.upload(file, type));
        }
        catch (AppContentUploadException e)
        {
            // 本模块上传异常就地收敛为 App 信封，不落到若依全局处理器（会包成 HTTP 200 + code 500）
            return AppApiResponse.fail(e.getCode(), e.getMessage());
        }
    }

    /**
     * 创建作品（2.9.2）
     *
     * 校验：title 非空且不超过 100；category_id 必填正整数（落库 genre_id，该列 NOT NULL）；
     * price 非负（缺省 0.00）。
     *
     * @param request body（兼容 category_id / categoryId 两种写法）
     * @return data = {workId, message}
     */
    @PostMapping("/works")
    public AppApiResponse<Map<String, Object>> create(@RequestBody(required = false) Map<String, Object> request)
    {
        String title = trimToNull(asString(request, "title"));
        if (title == null)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "title 不能为空");
        }
        if (title.length() > TITLE_MAX_LENGTH)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "title 长度不能超过 " + TITLE_MAX_LENGTH);
        }
        BigDecimal price = asDecimal(request, "price");
        if (price != null && price.signum() < 0)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "price 不能为负数");
        }
        // category_id（genre_id）NOT NULL：缺省则拒绝，不编造默认分类
        Integer categoryId = asInteger(request, "category_id", "categoryId");
        if (categoryId == null || categoryId <= 0)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "category_id 不能为空且必须为正整数");
        }
        AppWorkCreateRequest createRequest = new AppWorkCreateRequest();
        createRequest.setTitle(title);
        createRequest.setCategoryId(categoryId);
        createRequest.setDescription(trimToNull(asString(request, "description")));
        createRequest.setCover(trimToNull(asString(request, "cover")));
        createRequest.setPrice(price);
        Long workId = workManageService.createWork(createRequest);
        if (workId == null)
        {
            return AppApiResponse.fail(CODE_SYSTEM_ERROR, "创建失败，请稍后重试");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("workId", workId);
        data.put("message", "创建成功");
        return AppApiResponse.ok(data);
    }

    /**
     * 更新作品（2.9.3，仅 title/description/price）
     *
     * @param workId  作品ID
     * @param request body（兼容 category_id 之外的 description 等；title/description/price 均可选）
     * @return data = {message}；非本人或不存在 404，price 为负 400
     */
    @PutMapping("/works/{workId}")
    public AppApiResponse<Map<String, Object>> update(
            @PathVariable Long workId,
            @RequestBody(required = false) Map<String, Object> request)
    {
        BigDecimal price = asDecimal(request, "price");
        if (price != null && price.signum() < 0)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "price 不能为负数");
        }
        String title = trimToNull(asString(request, "title"));
        if (title != null && title.length() > TITLE_MAX_LENGTH)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "title 长度不能超过 " + TITLE_MAX_LENGTH);
        }
        AppWorkUpdateRequest updateRequest = new AppWorkUpdateRequest();
        updateRequest.setTitle(title);
        updateRequest.setDescription(trimToNull(asString(request, "description")));
        updateRequest.setPrice(price);
        if (!workManageService.updateWork(workId, updateRequest))
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或无权操作");
        }
        return AppApiResponse.ok(Map.of("message", "更新成功"));
    }

    /**
     * 删除作品（2.9.4，逻辑删除）
     *
     * @param workId 作品ID
     * @return data = {message}；非本人或不存在 404
     */
    @DeleteMapping("/works/{workId}")
    public AppApiResponse<Map<String, Object>> delete(@PathVariable Long workId)
    {
        if (!workManageService.deleteWork(workId))
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或无权操作");
        }
        return AppApiResponse.ok(Map.of("message", "删除成功"));
    }

    /**
     * 草稿箱列表（2.9.5，分页）
     *
     * @param page     页码（可选，默认 1）
     * @param pageSize 每页条数（可选，默认 20，上限 50）
     * @return data = {total, list}
     */
    @GetMapping("/works/drafts")
    public AppApiResponse<AppPageResult<AppDraftWorkItem>> drafts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize)
    {
        int pageNum = page == null ? 1 : page;
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        return AppApiResponse.ok(workManageService.pageDrafts(pageNum, size));
    }

    /**
     * 审核状态查询（2.9.6）
     *
     * @param workId 作品ID
     * @return data = {status, reviewResult, reviewComment}；非本人或不存在 404
     */
    @GetMapping("/works/{workId}/review-status")
    public AppApiResponse<AppReviewStatusDto> reviewStatus(@PathVariable Long workId)
    {
        AppReviewStatusDto dto = workManageService.getReviewStatus(workId);
        if (dto == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或无权查看");
        }
        return AppApiResponse.ok(dto);
    }

    /**
     * 作品版本列表（2.9.7，不含 content 全文）
     *
     * @param workId 作品ID
     * @return data = {list}；非本人或不存在 404
     */
    @GetMapping("/works/{workId}/versions")
    public AppApiResponse<Map<String, Object>> versions(@PathVariable Long workId)
    {
        List<AppWorkVersionDto> list = workManageService.listVersions(workId);
        if (list == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或无权查看");
        }
        return AppApiResponse.ok(Map.of("list", list));
    }

    /**
     * 创建作品版本（2.9.8）
     *
     * version_no 按作品内自增，新版本置为当前版本。
     *
     * @param workId 作品ID
     * @param body   body（兼容 version_desc / versionDesc / changeLog）
     * @return data = {versionId, message}；非本人或不存在 404
     */
    @PostMapping("/works/{workId}/versions")
    public AppApiResponse<Map<String, Object>> createVersion(
            @PathVariable Long workId,
            @RequestBody(required = false) Map<String, Object> body)
    {
        String versionDesc = asString(body, "version_desc");
        if (versionDesc == null)
        {
            versionDesc = asString(body, "versionDesc");
        }
        if (versionDesc == null)
        {
            versionDesc = asString(body, "changeLog");
        }
        String content = asString(body, "content");
        Long versionId = workManageService.createVersion(workId, versionDesc, content);
        if (versionId == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或无权操作");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("versionId", versionId);
        data.put("message", "版本创建成功");
        return AppApiResponse.ok(data);
    }

    /**
     * 作品版本详情（2.9.9，含 content）
     *
     * @param versionId 版本ID
     * @return data = 版本详情；版本不存在或所属作品非本人 404
     */
    @GetMapping("/works/versions/{versionId}")
    public AppApiResponse<AppWorkVersionDto> versionDetail(@PathVariable Long versionId)
    {
        AppWorkVersionDto dto = workManageService.getVersionDetail(versionId);
        if (dto == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "版本不存在或无权查看");
        }
        return AppApiResponse.ok(dto);
    }

    /**
     * 从请求体取字符串值（请求体可为 null）。
     */
    private static String asString(Map<String, Object> body, String key)
    {
        if (body == null)
        {
            return null;
        }
        Object raw = body.get(key);
        return raw instanceof String text ? text : (raw == null ? null : String.valueOf(raw));
    }

    /**
     * 从请求体取整数，按 keys 顺序尝试（兼容 snake_case 与 camelCase）。
     */
    private static Integer asInteger(Map<String, Object> body, String... keys)
    {
        if (body == null)
        {
            return null;
        }
        for (String key : keys)
        {
            Object raw = body.get(key);
            if (raw instanceof Number number)
            {
                return number.intValue();
            }
            if (raw instanceof String text && !text.isBlank())
            {
                try
                {
                    return Integer.valueOf(text.trim());
                }
                catch (NumberFormatException ignored)
                {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 从请求体取价格（BigDecimal），兼容数字与数字字符串。
     */
    private static BigDecimal asDecimal(Map<String, Object> body, String key)
    {
        if (body == null)
        {
            return null;
        }
        Object raw = body.get(key);
        if (raw instanceof Number number)
        {
            return new BigDecimal(number.toString());
        }
        if (raw instanceof String text && !text.isBlank())
        {
            try
            {
                return new BigDecimal(text.trim());
            }
            catch (NumberFormatException ignored)
            {
                return null;
            }
        }
        return null;
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}