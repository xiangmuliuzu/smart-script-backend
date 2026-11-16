package com.smartscript.platform.content.controller.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.domain.SysPromotionApplication;
import com.smartscript.platform.content.dto.PromotionApplyRequest;
import com.smartscript.platform.content.service.IPromotionService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * App 作品推广中心
 *
 * 全部接口需 App Access Token（落入 /api/v1/content/** 的 authenticated 规则）。
 * 归属一律取当前登录身份。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content/promotion")
public class AppPromotionController
{
    private static final int BAD_REQUEST = 400;

    @Autowired
    private IPromotionService promotionService;

    @Autowired
    private IdentityProvider identityProvider;

    /** 可用坑位（含当前投放与价格） */
    @GetMapping("/slots")
    public AppApiResponse<Map<String, Object>> slots(
            @RequestParam(defaultValue = "hot") String slotType)
    {
        try
        {
            com.smartscript.platform.content.dto.PromotionSlotPageVo page =
                    promotionService.getAppSlots(slotType);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("slotType", slotType);
            data.put("slots", page.getSlots());
            data.put("overview", page.getOverview());
            return AppApiResponse.ok(data);
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return AppApiResponse.fail(BAD_REQUEST, e.getMessage());
        }
    }

    /** 提交推广申请 */
    @PostMapping("/applications")
    public AppApiResponse<Map<String, Object>> apply(@RequestBody PromotionApplyRequest request)
    {
        try
        {
            Long applyId = promotionService.apply(request, identityProvider.currentUserId());
            SysPromotionApplication app = promotionService.selectMyApplication(applyId,
                    identityProvider.currentUserId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("applyId", applyId);
            data.put("application", app);
            return AppApiResponse.ok(data);
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return AppApiResponse.fail(BAD_REQUEST, e.getMessage());
        }
    }

    /** 我的推广列表 */
    @GetMapping("/applications/my")
    public AppApiResponse<Map<String, Object>> my(
            @RequestParam(required = false) String viewStatus)
    {
        List<SysPromotionApplication> list = promotionService.selectMyApplications(
                identityProvider.currentUserId(), viewStatus);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", list.size());
        data.put("list", list);
        return AppApiResponse.ok(data);
    }

    /** 我的推广详情 */
    @GetMapping("/applications/{applyId}")
    public AppApiResponse<SysPromotionApplication> detail(@PathVariable Long applyId)
    {
        SysPromotionApplication app = promotionService.selectMyApplication(applyId,
                identityProvider.currentUserId());
        if (app == null)
        {
            return AppApiResponse.fail(404, "推广申请不存在");
        }
        return AppApiResponse.ok(app);
    }

    /** 取消申请 */
    @PostMapping("/applications/{applyId}/cancel")
    public AppApiResponse<Void> cancel(@PathVariable Long applyId)
    {
        try
        {
            promotionService.cancel(applyId, identityProvider.currentUserId());
            return AppApiResponse.ok();
        }
        catch (IllegalArgumentException e)
        {
            return AppApiResponse.fail(404, e.getMessage());
        }
        catch (IllegalStateException e)
        {
            return AppApiResponse.fail(BAD_REQUEST, e.getMessage());
        }
    }
}
