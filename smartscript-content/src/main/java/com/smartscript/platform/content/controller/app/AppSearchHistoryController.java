package com.smartscript.platform.content.controller.app;

import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppSearchHistoryListDto;
import com.smartscript.platform.content.dto.AppSearchHistoryRequest;
import com.smartscript.platform.content.service.IAppSearchHistoryService;

/**
 * App 搜索历史（B 模块书城，接口文档 2.7.4 表2-84 / 2.7.5 表2-85 / 2.7.6 表2-86）。
 *
 * 鉴权：App 私有接口，需 App Access Token（未登记白名单，走 App 凭证域 authenticated）。
 * 归属一律取当前登录身份，请求体不接受 userId，避免读写他人历史。
 *
 * 路径说明：文档写的是 /api/search/history，B 模块既有接口统一收拢在 App 凭证域
 * /api/v1/content/** 下（见 AppAuthSecurityConfig.APP_PATH_PREFIXES），故落此前缀。
 *
 * POST 为文档外的补充接口：契约只定义了读列表/删单条/清空，缺少写入则列表永远为空，
 * 按 sys_search_history 表结构补齐，字段口径见 {@link AppSearchHistoryRequest}。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content/search/history")
public class AppSearchHistoryController
{
    /** 入参非法 */
    private static final int CODE_BAD_REQUEST = 400;

    /** 历史记录不存在或不属于当前用户 */
    private static final int CODE_NOT_FOUND = 404;

    /** 关键词长度上限（sys_search_history.keyword 为 varchar(100)） */
    private static final int KEYWORD_MAX_LENGTH = 100;

    private final IAppSearchHistoryService searchHistoryService;

    public AppSearchHistoryController(IAppSearchHistoryService searchHistoryService)
    {
        this.searchHistoryService = searchHistoryService;
    }

    /**
     * 搜索历史列表（2.7.4）
     *
     * @return data = {list:[{id, keyword, searchCount, lastSearchAt}]}，按最近搜索时间倒序
     */
    @GetMapping
    public AppApiResponse<AppSearchHistoryListDto> list()
    {
        return AppApiResponse.ok(searchHistoryService.listHistory());
    }

    /**
     * 记录搜索历史（文档未定义，补充接口）
     *
     * 同关键词已存在则累加次数并刷新时间，否则新增。
     *
     * @param request 请求体 {keyword}
     * @return data = {message}
     */
    @PostMapping
    public AppApiResponse<Map<String, Object>> record(@RequestBody(required = false) AppSearchHistoryRequest request)
    {
        String keyword = request == null || request.getKeyword() == null ? "" : request.getKeyword().trim();
        if (keyword.isEmpty())
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "搜索关键词不能为空");
        }
        if (keyword.length() > KEYWORD_MAX_LENGTH)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "搜索关键词长度不能超过" + KEYWORD_MAX_LENGTH);
        }
        searchHistoryService.recordHistory(keyword);
        return AppApiResponse.ok(Map.of("message", "记录成功"));
    }

    /**
     * 删除单条搜索历史（2.7.5）
     *
     * @param id 历史记录ID
     * @return data = {message}；记录不存在或不属于当前用户按 404 拒绝
     */
    @DeleteMapping("/{id}")
    public AppApiResponse<Map<String, Object>> remove(@PathVariable Long id)
    {
        if (!searchHistoryService.removeHistory(id))
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "搜索历史不存在");
        }
        return AppApiResponse.ok(Map.of("message", "删除成功"));
    }

    /**
     * 清空搜索历史（2.7.6）
     *
     * @return data = {message}；无历史时同样返回成功（幂等），message 说明无历史可清
     */
    @DeleteMapping
    public AppApiResponse<Map<String, Object>> clear()
    {
        int removed = searchHistoryService.clearHistory();
        return AppApiResponse.ok(Map.of("message", removed > 0 ? "清空成功" : "暂无搜索历史"));
    }
}