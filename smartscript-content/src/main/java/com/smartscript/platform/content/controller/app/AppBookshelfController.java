package com.smartscript.platform.content.controller.app;

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
 * 路径说明：文档写的是 /api/bookshelf，B 模块既有接口统一收拢在 App 凭证域
 * /api/v1/content/** 下（见 AppAuthSecurityConfig.APP_PATH_PREFIXES），故落此前缀；
 * 并就地接管原 A6 示例入口 /api/v1/content/shelf（其示例作品数据由 {@link ContentWorkService}
 * 的示例仓储提供，本批替换为真实书架分页数据）。
 *
 * 响应形状：GET 保留 A6 身份摘要块（identity / ownerUserId / downloadable / realNameRequired），
 * 追加 total / list 两个真实分页字段（list 元素含本书架阅读进度）；POST / DELETE 返回 {message}；
 * GET /{workId} 返回 {onShelf}（文档未单列书架态查询，按 sys_bookshelf_record 表补齐，供详情页回显）；
 * PUT /{workId}/progress 记录阅读进度（文档未单列，按同表 last_read_chapter_id / last_read_at 补齐）。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content/shelf")
public class AppBookshelfController
{
    /** 作品不存在或未上架 */
    private static final int CODE_NOT_FOUND = 404;

    /** 入参非法（add_source 超长等） */
    private static final int CODE_BAD_REQUEST = 400;

    /** 列表默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 加入来源列 sys_bookshelf_record.add_source 的 varchar(20) 上限 */
    private static final int ADD_SOURCE_MAX_LENGTH = 20;

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
     * 加入书架（2.7.12 POST，幂等：重复加入不产生重复记录）
     *
     * @param workId    作品ID
     * @param shelfType 加入来源（可选，接口文档 shelf_type，透传落 add_source，缺省 app）
     * @return data = {message}；作品不存在/已删除/未上架按 404 拒绝
     */
    @PostMapping("/{workId}")
    public AppApiResponse<Map<String, Object>> add(
            @PathVariable Long workId,
            @RequestParam(required = false) String shelfType)
    {
        if (shelfType != null && shelfType.trim().length() > ADD_SOURCE_MAX_LENGTH)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "shelf_type 长度不能超过 " + ADD_SOURCE_MAX_LENGTH);
        }
        if (!bookshelfService.addShelf(workId, shelfType))
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或已下架");
        }
        return AppApiResponse.ok(Map.of("message", "已加入书架"));
    }

    /**
     * 移出书架（2.7.12 DELETE，幂等）
     *
     * 不在书架时同样返回成功：详情页/书架页可能连点，报错无意义。
     *
     * @param workId 作品ID
     * @return data = {message}
     */
    @DeleteMapping("/{workId}")
    public AppApiResponse<Map<String, Object>> remove(@PathVariable Long workId)
    {
        bookshelfService.removeShelf(workId);
        return AppApiResponse.ok(Map.of("message", "已移出书架"));
    }

    /**
     * 是否已在书架（文档未定义，补充接口）
     *
     * @param workId 作品ID
     * @return data = {onShelf}，供详情页回显书架态
     */
    @GetMapping("/{workId}")
    public AppApiResponse<Map<String, Object>> status(@PathVariable Long workId)
    {
        return AppApiResponse.ok(Map.of("onShelf", bookshelfService.isOnShelf(workId)));
    }

    /**
     * 记录阅读进度（文档未单列，按 sys_bookshelf_record 的进度列补齐）
     *
     * 阅读页每次载入成功后上报当前章节。只更新已存在的书架行：
     * 不在书架时按 404 拒绝，不隐式加入书架（加入书架是独立的显式动作）。
     *
     * @param workId 作品ID
     * @param body   请求体 {chapterId}
     * @return data = {message}；chapterId 缺失/非正整数按 400，不在书架按 404
     */
    @PutMapping("/{workId}/progress")
    public AppApiResponse<Map<String, Object>> saveProgress(
            @PathVariable Long workId,
            @RequestBody(required = false) Map<String, Object> body)
    {
        Long chapterId = parseChapterId(body);
        if (chapterId == null || chapterId <= 0)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "chapterId 不能为空且必须为正整数");
        }
        if (!bookshelfService.saveProgress(workId, chapterId))
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "该作品不在书架中");
        }
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