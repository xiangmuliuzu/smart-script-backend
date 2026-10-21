package com.ruoyi.web.controller.content;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ruoyi.common.utils.SecurityUtils;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.CopyrightAssetDto;
import com.smartscript.platform.content.dto.CopyrightAssetQuery;
import com.smartscript.platform.content.service.ISysWorkService;
import com.smartscript.platform.identity.IdentityProvider;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.service.TradeOrderService;

/**
 * 创作者侧版权资产（User Web / 后续 App 共用）。
 * 复用 Admin 同一套作品上下架字段与授权历史查询，强制按当前用户过滤。
 * 不改动 {@code /api/v1/admin/copyright/assets*}。
 */
@RestController
@RequestMapping("/api/v1/content/copyright/assets")
public class CreatorCopyrightAssetsController
{
    private static final int CODE_UNAUTHORIZED = 401;
    private static final int CODE_BAD_REQUEST = 400;
    private static final int CODE_NOT_FOUND = 404;

    @Autowired
    private ISysWorkService workService;

    @Autowired
    private TradeOrderService orderService;

    @Autowired
    private IdentityProvider identityProvider;

    /** 我的已发布版权资产列表。 */
    @GetMapping
    public AppApiResponse<AppPageResult<CopyrightAssetDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize)
    {
        Long userId = currentUserId();
        if (userId == null)
        {
            return AppApiResponse.fail(CODE_UNAUTHORIZED, "请先登录");
        }

        CopyrightAssetQuery query = new CopyrightAssetQuery();
        query.setAuthorId(userId);
        query.setOnlyPublished(Boolean.TRUE);
        query.setKeyword(normalizeKeyword(keyword));
        query.setStatus(normalizeStatus(status));

        int safePageNum = pageNum != null && pageNum > 0 ? pageNum : 1;
        int safePageSize = pageSize != null && pageSize > 0 ? Math.min(pageSize, 50) : 10;
        PageHelper.startPage(safePageNum, safePageSize);
        List<CopyrightAssetDto> list = workService.selectCopyrightAssetList(query);
        PageInfo<CopyrightAssetDto> page = new PageInfo<>(list);
        return AppApiResponse.ok(AppPageResult.of(page.getTotal(), list));
    }

    /** 我的版权资产详情。 */
    @GetMapping("/{workId}")
    public AppApiResponse<CopyrightAssetDto> detail(@PathVariable Long workId)
    {
        Long userId = currentUserId();
        if (userId == null)
        {
            return AppApiResponse.fail(CODE_UNAUTHORIZED, "请先登录");
        }
        CopyrightAssetDto asset = workService.selectCopyrightAssetById(workId, userId, Boolean.TRUE);
        if (asset == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "版权资产不存在或无权限查看");
        }
        return AppApiResponse.ok(asset);
    }

    /** 授权历史（仅本人已发布作品）。 */
    @GetMapping("/{workId}/authorization-history")
    public AppApiResponse<AppPageResult<SysOrder>> authorizationHistory(
            @PathVariable Long workId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize)
    {
        Long userId = currentUserId();
        if (userId == null)
        {
            return AppApiResponse.fail(CODE_UNAUTHORIZED, "请先登录");
        }
        if (workService.selectCopyrightAssetById(workId, userId, Boolean.TRUE) == null)
        {
            return AppApiResponse.ok(AppPageResult.of(0, Collections.emptyList()));
        }

        int safePageNum = pageNum != null && pageNum > 0 ? pageNum : 1;
        int safePageSize = pageSize != null && pageSize > 0 ? Math.min(pageSize, 50) : 10;
        PageHelper.startPage(safePageNum, safePageSize);
        List<SysOrder> list = orderService.selectAuthorizationHistoryByWorkId(workId);
        PageInfo<SysOrder> page = new PageInfo<>(list);
        return AppApiResponse.ok(AppPageResult.of(page.getTotal(), list));
    }

    /** 上下架：写全局 sys_work.status，与 Admin 一致。 */
    @PutMapping("/{workId}/status")
    public AppApiResponse<Void> updateStatus(@PathVariable Long workId, @RequestBody Map<String, String> body)
    {
        Long userId = currentUserId();
        if (userId == null)
        {
            return AppApiResponse.fail(CODE_UNAUTHORIZED, "请先登录");
        }
        String status = body == null ? null : body.get("status");
        if (!"on_shelf".equals(status) && !"off_shelf".equals(status))
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "上架状态无效");
        }
        if (workService.selectCopyrightAssetById(workId, userId, Boolean.TRUE) == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "版权资产不存在或无权限操作");
        }
        String updateBy;
        try
        {
            updateBy = SecurityUtils.getUsername();
        }
        catch (Exception ex)
        {
            updateBy = String.valueOf(userId);
        }
        int rows = workService.updateCopyrightAssetStatus(workId, status, updateBy, userId);
        if (rows <= 0)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "资产不存在、已删除或状态未变化");
        }
        return AppApiResponse.ok();
    }

    private Long currentUserId()
    {
        Long userId = identityProvider.currentUserId();
        if (userId != null)
        {
            return userId;
        }
        try
        {
            return SecurityUtils.getUserId();
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    private static String normalizeKeyword(String keyword)
    {
        String value = StringUtils.trimToNull(keyword);
        return value == null ? null : StringUtils.left(value, 100);
    }

    private static String normalizeStatus(String status)
    {
        if ("on_shelf".equals(status) || "off_shelf".equals(status))
        {
            return status;
        }
        return null;
    }
}
