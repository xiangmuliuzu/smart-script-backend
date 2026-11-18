package com.smartscript.platform.user.service;

import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.smartscript.platform.review.service.ReviewApprovedEvent;

/**
 * 将审核模块发布的“作品审核通过”事件投递为创作者站内通知。
 *
 * 这里复用 A4 已有的 UserNotificationAdminService，因此 App、PC 创作者端
 * 使用同一套消息查询、已读和未读角标接口。通知只说明“可以上架”，不会代替
 * 作者执行上架动作。
 */
@Component
public class ReviewApprovedNotificationListener
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory
            .getLogger(ReviewApprovedNotificationListener.class);

    private final UserNotificationAdminService notificationService;

    public ReviewApprovedNotificationListener(UserNotificationAdminService notificationService)
    {
        this.notificationService = notificationService;
    }

    @EventListener
    public void onReviewApproved(ReviewApprovedEvent event)
    {
        if (event == null || event.getAuthorId() == null || event.getWorkId() == null
                || event.getReviewId() == null)
        {
            return;
        }
        String title = event.getWorkTitle() == null || event.getWorkTitle().isBlank()
                ? "你的作品"
                : "《" + event.getWorkTitle() + "》";
        String content = title + "已通过内容审核，当前仍未自动上架。请进入作品详情，确认信息后点击“上架到书城”。";
        try
        {
            notificationService.create(
                    "REV-APPROVED-" + event.getReviewId(),
                    "REVIEW",
                    "作品审核通过",
                    content,
                    "WORK",
                    String.valueOf(event.getWorkId()),
                    List.of(event.getAuthorId()),
                    "system");
        }
        catch (Exception e)
        {
            // 通知失败不能回滚已经完成的审核，也不能让审核接口返回失败；下次可按 reviewId 重试。
            log.warn("审核通过通知投递失败 reviewId={}, workId={}", event.getReviewId(), event.getWorkId(), e);
        }
    }
}
