package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysPromotionApplication;
import com.smartscript.platform.content.dto.PromotionApplyRequest;
import com.smartscript.platform.content.dto.PromotionGrantRequest;
import com.smartscript.platform.content.dto.PromotionPriceRequest;
import com.smartscript.platform.content.dto.PromotionSlotPageVo;

/**
 * 作品推广（付费推荐）服务
 *
 * 管理端：坑位总览、价格配置、申请审核、免费直上、下架。
 * App 端：可用坑位、提交申请、我的推广、取消。
 *
 * @author xiangsipeng
 */
public interface IPromotionService
{
    /** 坑位总览（管理端，含营收与待审数） */
    PromotionSlotPageVo getSlotOverview(String slotType);

    /** 坑位总览（App 端，不含营收） */
    PromotionSlotPageVo getAppSlots(String slotType);

    /** 管理端申请列表（分页由调用方 PageHelper 驱动） */
    List<SysPromotionApplication> selectApplicationList(SysPromotionApplication query);

    /** 申请详情（管理端） */
    SysPromotionApplication selectApplicationById(Long applyId);

    /** 审核：通过则锁坑 48h，驳回需原因 */
    int audit(Long applyId, boolean approved, String rejectReason, String operator);

    /** 免费直上（admin_grant，不收费） */
    Long grant(PromotionGrantRequest request, String operator);

    /** 下架 / 取消投放 */
    int offline(Long applyId, String operator);

    /** 位次单价与启停配置 */
    int updatePrices(PromotionPriceRequest request);

    /** App：提交推广申请 */
    Long apply(PromotionApplyRequest request, Long authorId);

    /** App：我的推广列表 */
    List<SysPromotionApplication> selectMyApplications(Long authorId, String viewStatus);

    /** App：我的推广详情 */
    SysPromotionApplication selectMyApplication(Long applyId, Long authorId);

    /** App：取消申请（待审核 / 待支付可取消） */
    int cancel(Long applyId, Long authorId);
}
