package com.smartscript.platform.content.service;

import java.util.List;
import java.util.Map;
import com.smartscript.platform.content.dto.copyright.AppCopyrightApplyRequest;
import com.smartscript.platform.content.dto.copyright.AppCopyrightFeeQuoteDto;
import com.smartscript.platform.content.dto.copyright.AppCopyrightPayOrderDto;
import com.smartscript.platform.content.dto.copyright.AppCopyrightResubmitRequest;

/**
 * 版权确权缴费：询价、建待支付单、支付成功后创建申请并提交第三方。
 */
public interface ICopyrightPayOrderService
{
    AppCopyrightFeeQuoteDto quoteFee(Long userId, Long workId, String copyrightCenter);

    AppCopyrightPayOrderDto createPayOrder(Long userId, AppCopyrightApplyRequest request);

    AppCopyrightPayOrderDto createResubmitPayOrder(Long userId, AppCopyrightResubmitRequest request);

    AppCopyrightPayOrderDto getPayOrder(Long payOrderId, Long userId);

    /** 当前用户未关闭的待支付单（unpaid/paying）。 */
    List<AppCopyrightPayOrderDto> listOpenPayOrders(Long userId, String workName);

    /**
     * 临时直付（跳过支付宝沙箱）：标记已付 → 建申请 → 提交第三方。
     */
    Map<String, Object> directPay(Long payOrderId, Long userId);

    /** 支付成功回调：建申请 + 提交第三方（幂等）。 */
    Map<String, Object> fulfillPaidOrder(Long payOrderId, Long userId, String tradeNo);

    /** 关闭超时未支付单。 */
    void closeExpiredOrders();
}
