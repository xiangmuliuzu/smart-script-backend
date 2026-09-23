package com.smartscript.platform.user.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.UserFeedback;
import com.smartscript.platform.user.domain.UserMessage;
import com.smartscript.platform.user.domain.UserNotificationPreference;
import com.smartscript.platform.user.domain.UserPhoneChangeLog;
import com.smartscript.platform.user.domain.UserRealNameAuth;

/**
 * A5 App 用户中心数据访问（契约 A5-USER-CENTER-CONTRACT-v1）。
 *
 * 覆盖个人资料、实名、换绑手机号、消息收件箱、通知偏好与我的反馈。
 * 表结构由 A2 迁移建立，本接口只读写，不做结构变更。
 *
 * 强制边界：
 *   1. **数据归属在 SQL 层强制**：所有按用户读取的语句都带 user_id 条件，
 *      越权访问在数据库层就查不到行，由服务层统一转 404。
 *   2. **敏感字段不出库**：手机号、姓名、证件号只取掩码；
 *      换绑审计只写掩码，不写验证码。
 *   3. **实名材料引用只在状态详情查询选择**，与 A4 列表不回传材料一致。
 *   4. 状态变更使用条件更新（expectedStatus），并发下只有一个成功。
 */
public interface AppUserCenterMapper
{
    // ------------------------------------------------------------------
    // 个人资料
    // ------------------------------------------------------------------

    /** 昵称变更；返回影响行数。 */
    int updateNickName(@Param("userId") Long userId, @Param("nickName") String nickName);

    /** 头像变更；返回影响行数。 */
    int updateAvatar(@Param("userId") Long userId, @Param("avatar") String avatar);

    /** 手机号占用判定（排除逻辑删除与指定的自身用户）。 */
    int countPhoneTaken(@Param("phone") String phone, @Param("excludeUserId") Long excludeUserId);

    /**
     * 换绑手机号。唯一索引 uk_sys_user_phonenumber 兜底并发，
     * 冲突时抛出 DuplicateKeyException，由服务层转 409。
     */
    int updatePhone(@Param("userId") Long userId, @Param("phone") String phone);

    /** 写换绑审计（只含掩码）。 */
    int insertPhoneChangeLog(UserPhoneChangeLog record);

    // ------------------------------------------------------------------
    // 实名认证
    // ------------------------------------------------------------------

    /** 最新一条实名申请（含材料引用）；无记录返回 null。 */
    UserRealNameAuth selectLatestRealName(@Param("userId") Long userId);

    /** 最新实名状态；无记录返回 null。 */
    String selectLatestRealNameStatus(@Param("userId") Long userId);

    /** 写入新的实名申请（驳回后重提也走新增，保留完整审计链）。 */
    int insertRealName(UserRealNameAuth record);

    // ------------------------------------------------------------------
    // 消息收件箱
    // ------------------------------------------------------------------

    /** 我的消息分页；分页由 PageHelper 驱动。参数键：type。 */
    List<UserMessage> selectMyMessages(Map<String, Object> params);

    /** 我的消息详情（含正文），归属不符返回 null。 */
    UserMessage selectMyMessage(@Param("userId") Long userId, @Param("messageId") Long messageId);

    /** 消息总未读数。 */
    int countUnread(@Param("userId") Long userId);

    /** 按类型未读数，用于首页角标与分类筛选。 */
    List<Map<String, Object>> countUnreadGroupByType(@Param("userId") Long userId);

    /** 单条已读；已读时返回 0 行，服务层按存在性判定幂等成功。 */
    int markRead(@Param("userId") Long userId, @Param("messageId") Long messageId, @Param("now") Date now);

    /** 全部已读；返回本次实际更新行数。 */
    int markAllRead(@Param("userId") Long userId, @Param("now") Date now);

    /** 我的通知偏好（稀疏存储，只返回显式改动过的组合）。 */
    List<UserNotificationPreference> selectPreferences(@Param("userId") Long userId);

    /** 写入或更新偏好。 */
    int upsertPreference(@Param("userId") Long userId,
                         @Param("channel") String channel,
                         @Param("type") String type,
                         @Param("enabled") boolean enabled);

    /** 删除偏好行（保存为默认值时使用，保持稀疏存储）。 */
    int deletePreference(@Param("userId") Long userId,
                         @Param("channel") String channel,
                         @Param("type") String type);

    // ------------------------------------------------------------------
    // 我的反馈
    // ------------------------------------------------------------------

    int insertFeedback(UserFeedback feedback);

    /** 我的反馈分页；分页由 PageHelper 驱动。参数键：userId、status。 */
    List<UserFeedback> selectMyFeedbackPage(Map<String, Object> params);

    /** 我的反馈详情（含附件引用），归属不符返回 null。 */
    UserFeedback selectMyFeedback(@Param("userId") Long userId, @Param("feedbackId") Long feedbackId);
}
