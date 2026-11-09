package com.smartscript.platform.content.controller.app;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppShelfWorkDto;
import com.smartscript.platform.content.service.ContentWorkService;
import com.smartscript.platform.content.service.IAppBookshelfService;

/**
 * App 书架（B 模块书城，接口文档 2.7.12 表2-92）。
 *
 * 鉴权：App 私有接口，需 App Access Token（未登记白名单，走 App 凭证域 authenticated）。
 * 归属一律取当前登录身份，不接收 userId 入参，避免读写他人书架。
 *
 * 语义调整：书架 = 「已购买 / 已获授权」的作品库，不再支持手工加入或移出普通作品，
 * 故 POST / DELETE 已下线；列表由订单完成态与生效授权派生，与章节解锁同一口径。
 *
 * 响应形状：GET 保留身份摘要块（identity / ownerUserId / downloadable / realNameRequired），
 * 追加 total / list 两个分页字段（list 元素含阅读进度）；
 * GET /{workId} 返回 {authorized}（供详情页回显「已获授权 · 可读全文」）；
 * PUT /{workId}/progress 记录阅读进度（首次阅读自动建进度行）。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content/shelf")
public class AppBookshelfController
{
    /** 入参非法 */
    private static final int CODE_BAD_REQUEST = 400;

    /** 列表默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final IAppBookshelfService bookshelfService;

    private final ContentWorkService contentWorkService;

    public AppBookshelfController(IAppBookshelfService bookshelfService, ContentWorkService contentWorkService)
    {
        this.bookshelfService = bookshelfService;
        this.contentWorkService = contentWorkService;
    }

    /**
     * 我的书架列表（2.7.12 GET，分页）
     *
     * @param page     页码（可选，默认 1）
     * @param pageSize 每页条数（可选，默认 20，上限 50）
     * @return data = {identity, ownerUserId, downloadable, realNameRequired, total, list}
     */
    @GetMapping
    public AppApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize)
    {
        int pageNum = page == null ? 1 : page;
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        Map<String, Object> data = contentWorkService.identityBlock();
        AppPageResult<AppShelfWorkDto> pageResult = bookshelfService.pageShelf(pageNum, size);
        data.put("total", pageResult.getTotal());
        data.put("list", pageResult.getList());
        return AppApiResponse.ok(data);
    }

    /**
     * 是否已获该作品授权（购买或生效授权，供详情页回显）
     *
     * @param workId 作品ID
     * @return data = {authorized}
     */
    @GetMapping("/{workId}")
    public AppApiResponse<Map<String, Object>> status(@PathVariable Long workId)
    {
        return AppApiResponse.ok(Map.of("authorized", bookshelfService.isAuthorized(workId)));
    }

    /**
     * 记录阅读进度（文档未单列，按 sys_bookshelf_record 的进度列补齐）
     *
     * 阅读页每次载入成功后上报当前章节。首次阅读自动建立进度行（upsert），
     * 不再要求作品预先在书架中（书架已改为购买派生，不再有「加入」动作）。
     *
     * @param workId 作品ID
     * @param body   请求体 {chapterId}
     * @return data = {message}；chapterId 缺失/非正整数按 400
     */
    @PutMapping("/{workId}/progress")
    public AppApiResponse<Map<String, Object>> saveProgress(
            @PathVariable Long workId,
            @RequestBody(required = false) Map<String, Object> body)
    {
        Long chapterId = parseChapterId(body);
        if (chapterId == null || chapterId <= 0)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "章节参数不正确，请刷新后重试");
        }
        bookshelfService.saveProgress(workId, chapterId);
        return AppApiResponse.ok(Map.of("message", "已记录阅读进度"));
    }

    /**
     * 解析请求体里的 chapterId，兼容 JSON 数字与数字字符串两种写法。
     *
     * @param body 请求体（可为 null）
     * @return 章节ID；缺失或非数字返回 null
     */
    private static Long parseChapterId(Map<String, Object> body)
    {
        if (body == null)
        {
            return null;
        }
        Object raw = body.get("chapterId");
        if (raw instanceof Number number)
        {
            return number.longValue();
        }
        if (raw instanceof String text)
        {
            try
            {
                return Long.parseLong(text.trim());
            }
            catch (NumberFormatException ignored)
            {
                return null;
            }
        }
        return null;
    }
}