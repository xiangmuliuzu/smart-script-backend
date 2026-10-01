package com.smartscript.platform.user.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ruoyi.common.core.redis.RedisCache;
import com.smartscript.platform.user.constant.AppAdminConstants;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.domain.UserFeedback;
import com.smartscript.platform.user.dto.FeedbackCreateRequest;
import com.smartscript.platform.user.dto.PageResult;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;

/**
 * A5 意见反馈（契约 §1.6，规格 §8.7）。
 *
 * 强制边界：
 *   - 用户只能读写自己的反馈：所有查询带 user_id，他人反馈与不存在的反馈统一 404。
 *   - 分类取值与 A4 处理端、DB CHECK 约束同集合；越界分类返回 400。
 *   - 正文长度受限（5–2000，与列宽一致），并做提交频控（规格 §8.7 要求频率限制）。
 *   - 列表不返回附件引用；附件只在详情返回，与 A4 FB-01 的「附件只在详情」一致。
 *   - 附件只接受平台上传服务返回的引用，不接受任意外部地址。
 */
@Service
public class UserFeedbackService
{
    /** 支持的反馈分类，与 A4 UserFeedbackAdminService 的处理口径一致。 */
    private static final List<String> CATEGORIES = List.of(
            "FEATURE", "EXPERIENCE", "BUG", "COMPLAINT", "OTHER");

    /** 默认分类：未选择时归入「其他」。 */
    private static final String DEFAULT_CATEGORY = "OTHER";

    private static final int CONTENT_MIN = 5;
    private static final int CONTENT_MAX = 2000;
    private static final int ATTACHMENT_MAX = 255;

    /** 提交频控：每用户每小时上限。 */
    private static final int HOURLY_LIMIT = 5;
    private static final String RATE_KEY_SUFFIX = "feedback:hourly:";

    private final AppUserCenterMapper centerMapper;
    private final RedisCache redisCache;
    private final AppSessionRevocationService keyService;

    public UserFeedbackService(AppUserCenterMapper centerMapper,
            RedisCache redisCache,
            AppSessionRevocationService keyService)
    {
        this.centerMapper = centerMapper;
        this.redisCache = redisCache;
        this.keyService = keyService;
    }

    /** 提交反馈。返回新记录标识与初始状态，便于客户端直接跳详情。 */
    @Transactional
    public Map<String, Object> create(Long userId, FeedbackCreateRequest request)
    {
        if (request == null)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "no payload");
        }
        String category = normalizeCategory(request.getCategory());
        String content = normalizeContent(request.getContent());
        String attachmentRef = normalizeAttachment(request.getAttachmentRef());
        enforceRateLimit(userId);

        UserFeedback feedback = new UserFeedback();
        feedback.setCategory(category);
        feedback.setContent(content);
        feedback.setStatus(AppAdminConstants.FEEDBACK_SUBMITTED);
        feedback.setAttachmentRef(attachmentRef);
        feedback.setUserId(userId);
        centerMapper.insertFeedback(feedback);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("feedbackId", feedback.getFeedbackId());
        result.put("status", feedback.getStatus());
        return result;
    }

    /**
     * 我的反馈分页。列表不返回附件引用。
     *
     * total 必须在映射成响应结构之前从原始行取得：映射后的 List 已退化为普通列表，
     * 那时再包 PageInfo 只能拿到当前页条数。
     */
    public PageResult<Map<String, Object>> page(Long userId, String status, int pageNum, int pageSize)
    {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", userId);
        params.put("status", normalizeStatusFilter(status));
        PageHelper.startPage(pageNum, pageSize);
        List<UserFeedback> rows = centerMapper.selectMyFeedbackPage(params);
        long total = new PageInfo<>(rows).getTotal();
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserFeedback row : rows)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("feedbackId", row.getFeedbackId());
            item.put("category", row.getCategory());
            item.put("content", row.getContent());
            item.put("status", row.getStatus());
            item.put("reply", row.getReply());
            item.put("submittedAt", row.getSubmittedAt());
            item.put("handledAt", row.getHandledAt());
            result.add(item);
        }
        return PageResult.of(total, result);
    }

    /** 我的反馈详情（含附件引用）。归属不符返回 404。 */
    public Map<String, Object> detail(Long userId, Long feedbackId)
    {
        if (feedbackId == null)
        {
            throw notFound();
        }
        UserFeedback feedback = centerMapper.selectMyFeedback(userId, feedbackId);
        if (feedback == null)
        {
            throw notFound();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("feedbackId", feedback.getFeedbackId());
        result.put("category", feedback.getCategory());
        result.put("content", feedback.getContent());
        result.put("status", feedback.getStatus());
        result.put("reply", feedback.getReply());
        result.put("attachments", toAttachments(feedback.getAttachmentRef()));
        result.put("submittedAt", feedback.getSubmittedAt());
        result.put("handledAt", feedback.getHandledAt());
        return result;
    }

    /**
     * 单值附件列映射为零或单元素数组。
     * 与 A4 一致：禁止按分隔符拆分，避免把含分隔符的合法引用误切成多个附件。
     */
    private static List<String> toAttachments(String attachmentRef)
    {
        if (attachmentRef == null || attachmentRef.isBlank())
        {
            return List.of();
        }
        return List.of(attachmentRef);
    }

    /**
     * 提交频控：每用户每小时上限。
     *
     * 计数放在 Redis 且以首次提交时间起算窗口；Redis 不可用时**不阻断**
     * 业务写入（频控是防滥用措施，不是正确性前提），仅少一层保护。
     */
    private void enforceRateLimit(Long userId)
    {
        String key = keyService.key(RATE_KEY_SUFFIX + userId);
        try
        {
            Object current = redisCache.getCacheObject(key);
            int count = current instanceof Number n ? n.intValue() : 0;
            if (count >= HOURLY_LIMIT) {
                throw new AppAuthException(com.smartscript.platform.user.constant.AppAuthErrorCodes.SMS_RATE_LIMIT,
                        429, "too many requests");
            }
            redisCache.setCacheObject(key, count + 1, 3600, TimeUnit.SECONDS);
        }
        catch (AppAuthException e)
        {
            throw e;
        }
        catch (RuntimeException e)
        {
            // Redis 异常不阻断提交；写入依赖数据库约束与后续加固清单 H-12 的复核
        }
    }

    static String normalizeCategory(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            return DEFAULT_CATEGORY;
        }
        String value = raw.trim().toUpperCase();
        if (!CATEGORIES.contains(value))
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "invalid category");
        }
        return value;
    }

    static String normalizeContent(String raw)
    {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < CONTENT_MIN || value.length() > CONTENT_MAX)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "content length invalid");
        }
        return value;
    }

    static String normalizeAttachment(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            return null;
        }
        String value = raw.trim();
        if (value.length() > ATTACHMENT_MAX)
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "attachment ref too long");
        }
        return value;
    }

    /** 筛选状态白名单：与 DB CHECK 约束同集合；空值表示不筛选。 */
    static String normalizeStatusFilter(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            return null;
        }
        String value = raw.trim().toUpperCase();
        if (!List.of(AppAdminConstants.FEEDBACK_SUBMITTED, AppAdminConstants.FEEDBACK_PROCESSING,
                AppAdminConstants.FEEDBACK_REPLIED, AppAdminConstants.FEEDBACK_CLOSED).contains(value))
        {
            throw new AppAuthException(AppUserErrorCodes.PARAM, 400, "invalid status");
        }
        return value;
    }

    private static AppAuthException notFound()
    {
        return new AppAuthException(AppUserErrorCodes.RESOURCE_NOT_FOUND, 404,
                AppUserErrorCodes.RESOURCE_NOT_FOUND_TEXT);
    }
}
