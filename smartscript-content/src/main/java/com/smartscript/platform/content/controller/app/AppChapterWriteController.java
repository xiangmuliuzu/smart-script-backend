package com.smartscript.platform.content.controller.app;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppChapterCreateRequest;
import com.smartscript.platform.content.dto.AppChapterUpdateRequest;
import com.smartscript.platform.content.service.IAppChapterAuthorService;

/**
 * App 章节编辑（B 模块上传与创作；接口文档未定义章节 CRUD 规格，按模块约定补齐，已获授权）。
 *
 * 鉴权：全部为 App 私有接口，需 App Access Token。
 *   - POST /works/{workId}/chapters：/works/** 的公开放行是 GET-only，POST 自动走 authenticated；
 *   - PUT/DELETE /chapters/{chapterId}：/chapters/** 的公开放行同样是 GET-only，自动走 authenticated。
 * 因此无需改动 AppAuthSecurityConfig。
 *
 * 路径映射：与既有 App 接口一致，收拢在 App 凭证域 /api/v1/content/** 下：
 *   POST   /api/v1/content/works/{workId}/chapters
 *   PUT    /api/v1/content/chapters/{chapterId}
 *   DELETE /api/v1/content/chapters/{chapterId}
 *
 * 归属：作品作者必须为当前登录身份，非本人按 403 拒绝、作品/章节不存在按 404；
 * word_count 由后端按正文长度计算，不接收请求体入参。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content")
public class AppChapterWriteController
{
    /** 作品/章节不存在或已删除 */
    private static final int CODE_NOT_FOUND = 404;

    /** 非本人作品，无权操作 */
    private static final int CODE_FORBIDDEN = 403;

    /** 入参非法（标题缺失/超长、章节序号缺失/重复、标记值非法、无可更新字段） */
    private static final int CODE_BAD_REQUEST = 400;

    /** 服务端失败 */
    private static final int CODE_SYSTEM_ERROR = 500;

    /** 章节标题长度上限（与 sys_work_chapter.chapter_title varchar(100) 一致） */
    private static final int TITLE_MAX_LENGTH = 100;

    /** tinyint 标记合法取值 */
    private static final String FLAG_OFF = "0";

    private static final String FLAG_ON = "1";

    private final IAppChapterAuthorService chapterService;

    public AppChapterWriteController(IAppChapterAuthorService chapterService)
    {
        this.chapterService = chapterService;
    }

    /**
     * 作者视角章节列表（不过滤 status，隐藏章节也在列；供 App 章节管理页使用）
     *
     * 说明：路径落在 /works/** 的公开 GET 白名单内，但业务层仍强制校验作者归属，
     * 非本人/游客不会拿到任何章节数据（游客无身份按 404，他人作品按 403）。
     *
     * @param workId 作品ID
     * @return data = {list}；非本人 403，作品不存在/已删除 404
     */
    @GetMapping("/works/{workId}/chapters/manage")
    public AppApiResponse<Map<String, Object>> listForAuthor(@PathVariable Long workId)
    {
        int access = chapterService.resolveWorkAccess(workId);
        if (access == IAppChapterAuthorService.ACCESS_NOT_FOUND)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在");
        }
        if (access == IAppChapterAuthorService.ACCESS_DENIED)
        {
            return AppApiResponse.fail(CODE_FORBIDDEN, "无权操作该作品");
        }
        return AppApiResponse.ok(Map.of("list", chapterService.listAuthorChapters(workId)));
    }

    /**
     * 新增章节
     *
     * @param workId  作品ID
     * @param request body {chapterNo 必填正整数, chapterTitle 必填≤100, content 可选, isFree 可选 "0"/"1"}
     * @return data = {chapterId, message}；非本人 403，作品不存在 404，章节序号重复或入参非法 400
     */
    @PostMapping("/works/{workId}/chapters")
    public AppApiResponse<Map<String, Object>> create(
            @PathVariable Long workId,
            @RequestBody(required = false) AppChapterCreateRequest request)
    {
        int access = chapterService.resolveWorkAccess(workId);
        if (access == IAppChapterAuthorService.ACCESS_NOT_FOUND)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在");
        }
        if (access == IAppChapterAuthorService.ACCESS_DENIED)
        {
            return AppApiResponse.fail(CODE_FORBIDDEN, "无权操作该作品");
        }
        if (request == null || request.getChapterNo() == null || request.getChapterNo() <= 0)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "chapterNo 不能为空且必须为正整数");
        }
        String title = trimToNull(request.getChapterTitle());
        if (title == null)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "chapterTitle 不能为空");
        }
        if (title.length() > TITLE_MAX_LENGTH)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "chapterTitle 长度不能超过 " + TITLE_MAX_LENGTH);
        }
        if (!isValidFlag(request.getIsFree()))
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "isFree 只能为 \"0\" 或 \"1\"");
        }
        if (chapterService.chapterNoExists(workId, request.getChapterNo()))
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "章节序号已存在");
        }
        AppChapterCreateRequest createRequest = new AppChapterCreateRequest();
        createRequest.setChapterNo(request.getChapterNo());
        createRequest.setChapterTitle(title);
        createRequest.setContent(request.getContent());
        // 缺省（null/空）交由服务层补默认值 "0"
        createRequest.setIsFree(trimToNull(request.getIsFree()));
        Long chapterId = chapterService.createChapter(workId, createRequest);
        if (chapterId == null)
        {
            return AppApiResponse.fail(CODE_SYSTEM_ERROR, "新增章节失败，请稍后重试");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("chapterId", chapterId);
        data.put("message", "新增成功");
        return AppApiResponse.ok(data);
    }

    /**
     * 更新章节（chapterTitle/content/isFree/status 均可选，为 null 不更新）
     *
     * @param chapterId 章节ID
     * @param request   body 至少含一个待更新字段
     * @return data = {message}；非本人 403，章节不存在 404，入参非法/无可更新字段 400
     */
    @PutMapping("/chapters/{chapterId}")
    public AppApiResponse<Map<String, Object>> update(
            @PathVariable Long chapterId,
            @RequestBody(required = false) AppChapterUpdateRequest request)
    {
        int access = chapterService.resolveChapterAccess(chapterId);
        if (access == IAppChapterAuthorService.ACCESS_NOT_FOUND)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "章节不存在");
        }
        if (access == IAppChapterAuthorService.ACCESS_DENIED)
        {
            return AppApiResponse.fail(CODE_FORBIDDEN, "无权操作该章节");
        }
        String rawIsFree = request == null ? null : request.getIsFree();
        String rawStatus = request == null ? null : request.getStatus();
        if (!isValidFlag(rawIsFree) || !isValidFlag(rawStatus))
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "isFree/status 只能为 \"0\" 或 \"1\"");
        }
        String title = trimToNull(request == null ? null : request.getChapterTitle());
        String content = request == null ? null : request.getContent();
        // null=未提交（不更新）
        String isFree = trimToNull(rawIsFree);
        String status = trimToNull(rawStatus);
        if (title != null && title.length() > TITLE_MAX_LENGTH)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "chapterTitle 长度不能超过 " + TITLE_MAX_LENGTH);
        }
        if (title == null && content == null && isFree == null && status == null)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "至少需要提供一个待更新字段");
        }
        AppChapterUpdateRequest updateRequest = new AppChapterUpdateRequest();
        updateRequest.setChapterTitle(title);
        updateRequest.setContent(content);
        updateRequest.setIsFree(isFree);
        updateRequest.setStatus(status);
        if (!chapterService.updateChapter(chapterId, updateRequest))
        {
            return AppApiResponse.fail(CODE_SYSTEM_ERROR, "更新章节失败，请稍后重试");
        }
        return AppApiResponse.ok(Map.of("message", "更新成功"));
    }

    /**
     * 删除章节（物理删除）
     *
     * @param chapterId 章节ID
     * @return data = {message}；非本人 403，章节不存在 404
     */
    @DeleteMapping("/chapters/{chapterId}")
    public AppApiResponse<Map<String, Object>> delete(@PathVariable Long chapterId)
    {
        int access = chapterService.resolveChapterAccess(chapterId);
        if (access == IAppChapterAuthorService.ACCESS_NOT_FOUND)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "章节不存在");
        }
        if (access == IAppChapterAuthorService.ACCESS_DENIED)
        {
            return AppApiResponse.fail(CODE_FORBIDDEN, "无权操作该章节");
        }
        if (!chapterService.deleteChapter(chapterId))
        {
            return AppApiResponse.fail(CODE_SYSTEM_ERROR, "删除章节失败，请稍后重试");
        }
        return AppApiResponse.ok(Map.of("message", "删除成功"));
    }

    /**
     * tinyint 标记是否合法：缺省（null/空串，表示不提交该字段）或 "0"/"1" 均合法，其它非法。
     */
    private static boolean isValidFlag(String value)
    {
        if (value == null || value.isBlank())
        {
            return true;
        }
        return FLAG_OFF.equals(value.trim()) || FLAG_ON.equals(value.trim());
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