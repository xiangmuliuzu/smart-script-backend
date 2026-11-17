package com.smartscript.platform.user.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.framework.realtime.RealtimePushService;
import com.smartscript.platform.user.constant.AppAdminConstants;
import com.smartscript.platform.user.domain.admin.AdminNotification;
import com.smartscript.platform.user.mapper.AdminNotificationMapper;
import com.smartscript.platform.user.mapper.UserFollowMapper;

/**
 * 关注动态：关注的创作者发布新作品时，向粉丝投递站内通知。
 *
 * <p>与 A4 后台群发不同，收件人数量由粉丝关系决定，可能超过后台单次发送阈值，
 * 因此不复用 {@link UserNotificationAdminService#create} 的人数限制，改为：
 * 消息主体一次写入，收件记录按 {@link #RECEIVER_CHUNK} 一批分批写入。</p>
 *
 * 强制边界：
 *   <ul>
 *   <li>幂等：requestId = {@code WORK-PUB-{workId}}，同一作品只通知一次；
 *       作者下架后重新上架不会重复投递。</li>
 *   <li>投递过滤：粉丝查询已排除停用/注销账号与双向拉黑关系；
 *       站内投递再按「INBOX × FOLLOW」偏好剔除关闭者。</li>
 *   <li>受众：audience = NULL（通用通知，全端可见）。关注动态不区分买卖角色，
 *       与 PC 门户的消息列表、未读数保持同一口径。</li>
 *   <li>业务引用：businessType = WORK、businessId = workId，供 App 消息详情直达作品页。</li>
 *   </ul>
 */
@Service
public class WorkPublishedNotifyService
{
    private static final Logger log = LoggerFactory.getLogger(WorkPublishedNotifyService.class);

    /** 与 user_notification.type 同源取值。 */
    static final String NOTIFICATION_TYPE = AppAdminConstants.NOTIFICATION_FOLLOW;

    /** 业务引用类型：作品。 */
    static final String BUSINESS_TYPE_WORK = "WORK";

    /** 关注动态不区分买卖角色，全端可见；PC 门户只按 BUYER/SELLER 过滤交易通知。 */
    private static final String AUDIENCE_ALL = null;

    /** 幂等键前缀。 */
    private static final String REQUEST_ID_PREFIX = "WORK-PUB-";

    /** 收件记录单批条数，避免一条 SQL 绑定参数过多。 */
    static final int RECEIVER_CHUNK = 500;

    private static final String DEFAULT_TITLE = "关注的创作者发布了新作品";

    private final AdminNotificationMapper notificationMapper;
    private final UserFollowMapper followMapper;

    /**
     * 实时提醒出口。可选注入：没有装配长连接模块（如纯单元测试上下文）时
     * 通知照常落库，只是不即时打扰在线客户端。
     */
    @Autowired(required = false)
    private RealtimePushService realtimePush;

    public WorkPublishedNotifyService(AdminNotificationMapper notificationMapper, UserFollowMapper followMapper)
    {
        this.notificationMapper = notificationMapper;
        this.followMapper = followMapper;
    }

    /**
     * 作品发布后通知作者粉丝。
     *
     * @param workId     作品主键
     * @param authorId   作者用户主键
     * @param workTitle  作品标题（可空）
     * @param authorName 作者昵称（可空）
     */
    @Transactional(rollbackFor = Exception.class)
    public void notifyFollowers(Long workId, Long authorId, String workTitle, String authorName)
    {
        if (workId == null || authorId == null)
        {
            return;
        }
        String requestId = REQUEST_ID_PREFIX + workId;
        if (notificationMapper.selectByRequestId(requestId) != null)
        {
            // 同一作品已通知过（重复事件 / 重新上架）
            return;
        }
        List<Long> followers = followMapper.selectWorkPublishFollowerIds(authorId);
        if (followers == null || followers.isEmpty())
        {
            return;
        }

        List<Long> delivered = excludeInboxDisabled(followers);
        String title = titleOf(authorName);
        String content = contentOf(authorName, workTitle);

        AdminNotification entity = new AdminNotification();
        entity.setRequestId(requestId);
        entity.setType(NOTIFICATION_TYPE);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setBusinessType(BUSINESS_TYPE_WORK);
        entity.setBusinessId(String.valueOf(workId));
        entity.setAudience(AUDIENCE_ALL);
        entity.setCreatedBy("system");
        try
        {
            notificationMapper.insertNotification(entity);
        }
        catch (DuplicateKeyException e)
        {
            // 并发下同一作品的事件已先落库：以先到的收件人集合为准
            return;
        }

        if (delivered.isEmpty())
        {
            // 所有粉丝都关闭了关注动态：消息主体按幂等键保留，不产生收件记录
            return;
        }
        for (List<Long> chunk : chunks(delivered))
        {
            notificationMapper.insertEventReceivers(entity.getNotificationId(), chunk, "system");
        }
        pushRealtime(entity, delivered);
        log.info("关注动态通知已投递 workId={}, receivers={}", workId, delivered.size());
    }

    /** 剔除已通过「站内信 × FOLLOW」偏好关闭投递的用户；查询按批下发。 */
    private List<Long> excludeInboxDisabled(List<Long> followers)
    {
        Set<Long> disabled = new LinkedHashSet<>();
        for (List<Long> chunk : chunks(followers))
        {
            List<Long> rows = notificationMapper.selectInboxDisabledUserIds(chunk, NOTIFICATION_TYPE);
            if (rows != null)
            {
                disabled.addAll(rows);
            }
        }
        if (disabled.isEmpty())
        {
            return followers;
        }
        List<Long> delivered = new ArrayList<>(followers.size());
        for (Long userId : followers)
        {
            if (!disabled.contains(userId))
            {
                delivered.add(userId);
            }
        }
        return delivered;
    }

    /** 事务提交后推送事件帧；按「实时提醒 × FOLLOW」偏好剔除关闭者。 */
    private void pushRealtime(AdminNotification entity, List<Long> delivered)
    {
        if (realtimePush == null)
        {
            return;
        }
        Set<Long> disabled = new LinkedHashSet<>();
        for (List<Long> chunk : chunks(delivered))
        {
            List<Long> rows = notificationMapper.selectPushDisabledUserIds(chunk, NOTIFICATION_TYPE);
            if (rows != null)
            {
                disabled.addAll(rows);
            }
        }
        List<Long> targets = new ArrayList<>(delivered.size());
        for (Long userId : delivered)
        {
            if (!disabled.contains(userId))
            {
                targets.add(userId);
            }
        }
        if (!targets.isEmpty())
        {
            realtimePush.pushNotificationAfterCommit(entity.getNotificationId(), entity.getBusinessType(),
                    entity.getTitle(), targets);
        }
    }

    static String titleOf(String authorName)
    {
        return hasText(authorName) ? "「" + authorName.trim() + "」发布了新作品" : DEFAULT_TITLE;
    }

    static String contentOf(String authorName, String workTitle)
    {
        StringBuilder content = new StringBuilder("您关注的创作者");
        if (hasText(authorName))
        {
            content.append("「").append(authorName.trim()).append("」");
        }
        content.append("发布了新作品。\n");
        if (hasText(workTitle))
        {
            content.append("· 作品：《").append(workTitle.trim()).append("》\n");
        }
        content.append("可前往作品详情查看。");
        return content.toString();
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    /** 按固定大小切分，保留原顺序。 */
    static <T> List<List<T>> chunks(List<T> values)
    {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i += RECEIVER_CHUNK)
        {
            result.add(values.subList(i, Math.min(values.size(), i + RECEIVER_CHUNK)));
        }
        return result;
    }
}
