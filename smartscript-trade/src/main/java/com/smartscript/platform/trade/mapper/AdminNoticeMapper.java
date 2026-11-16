package com.smartscript.platform.trade.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

/**
 * 管理端公告（sys_notice, audience='ADMIN'）写入出口。
 *
 * 任务征集的发布权收归平台后，「需求方」侧的事件不再是某个 App 用户，
 * 而是运营自己；运营只看管理端，因此这类事件写进管理端「沟通 - 公告」收件箱。
 * 不走 ISysNoticeService：那条链路面向人工发布，缺省 status='1'（关闭）且没有幂等键。
 */
public interface AdminNoticeMapper
{
    /**
     * 按幂等键写入管理端公告：同一 dedupKey 只落一条，重复调用返回 0。
     * 键存进 remark，人工发布的公告不会带同名键，互不影响。
     */
    int insertIfAbsent(@Param("dedupKey") String dedupKey, @Param("title") String title,
            @Param("content") String content);

    /**
     * 管理端公告的可见账号（后台用户 user_type='00'，未禁用未删除）。
     * 与 NoticeRecipientMapper 的 ADMIN 受众口径一致，用于实时帧的收件人。
     */
    List<Long> selectAdminRecipients();
}
