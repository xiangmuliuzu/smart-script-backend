package com.smartscript.platform.notify;

import java.util.List;

/**
 * 跨模块站内信投递口（content/trade 等业务模块 → user 收件箱）。
 *
 * <p>业务模块不得编译依赖 smartscript-user；运行时由 user 模块提供实现。
 * 通知失败不得影响主业务：实现侧应独立事务并吞掉异常。</p>
 */
public interface UserInboxNotifier
{
    /**
     * 向指定用户投递一条站内通知。
     *
     * @param requestId    幂等键（同一事件重复触发只落一条）
     * @param type         消息类型（SYSTEM/TRANSACTION/SEAL/COPYRIGHT/…）
     * @param title        标题
     * @param content      正文
     * @param businessType 业务类型（可与 type 相同；与 businessId 成对）
     * @param businessId   业务主键（落地页定位）
     * @param userIds      收件人
     */
    void notify(String requestId, String type, String title, String content,
            String businessType, String businessId, List<Long> userIds);
}
