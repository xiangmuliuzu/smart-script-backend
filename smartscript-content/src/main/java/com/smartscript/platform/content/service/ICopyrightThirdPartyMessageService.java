package com.smartscript.platform.content.service;

import java.util.List;
import java.util.Map;
import com.smartscript.platform.content.dto.copyright.AppCopyrightSupplementRequest;
import com.smartscript.platform.content.dto.copyright.CopyrightThirdPartyMessageRequest;

/**
 * 版权第三方消息接收与补正处理。
 */
public interface ICopyrightThirdPartyMessageService
{
    /** 代理回调：落库并驱动申请状态（幂等）。 */
    Map<String, Object> receiveMessage(CopyrightThirdPartyMessageRequest request);

    /** Mock：对指定申请推送一条补正要求。 */
    Map<String, Object> mockPushSupplement(Long applyId, String content);

    /** 用户提交补正（不缴费）。 */
    Map<String, Object> submitSupplement(Long userId, Long applyId, AppCopyrightSupplementRequest request);

    /** 关闭超时未补的补正单，申请置为逾期待驳回。 */
    int closeOverdueSupplements();

    /** 申请关联的第三方消息（详情页）。 */
    List<Map<String, Object>> listMessagesByApplyId(Long applyId);

    /**
     * Admin 消息抽屉：最近第三方消息（可按外部申请号/中心侧申请过滤）。
     * @param limit 条数上限（默认 50，最大 200）
     */
    List<Map<String, Object>> listRecentMessages(Integer limit);

    /** 当前打开的补正单。 */
    Map<String, Object> getOpenSupplement(Long applyId);

    /** 申请下全部补正单（含已提交/逾期）。 */
    List<Map<String, Object>> listSupplementsByApplyId(Long applyId);
}
