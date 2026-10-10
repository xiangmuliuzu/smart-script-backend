package com.smartscript.platform.content.controller.app;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppReportRequest;
import com.smartscript.platform.content.dto.AppReportResultDto;
import com.smartscript.platform.content.service.IAppReportService;

/**
 * App 内容举报（B 模块，接口文档 2.8.17 表2-110）。
 *
 * 鉴权：App 私有接口，需 App Access Token（/content/reports 未登记白名单，走 App 凭证域 authenticated）。
 * 归属一律取当前登录身份，不接收请求体 userId，避免冒名举报。
 *
 * 路径说明：文档写的是 POST /api/reports，B 模块既有接口统一收拢在 App 凭证域
 * /api/v1/content/** 下（见 AppAuthSecurityConfig.APP_PATH_PREFIXES），故落 /content/reports。
 *
 * 字段口径：POST body 兼容文档的 snake_case（target_type / target_id）与模块内既有 camelCase
 * （targetType / targetId）两种写法；出参沿用 B 模块 App 契约的 camelCase。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content")
public class AppReportController
{
    /** 入参非法 */
    private static final int CODE_BAD_REQUEST = 400;

    /** sys_report.target_type varchar(30) 上限 */
    private static final int TARGET_TYPE_MAX_LENGTH = 30;

    /** sys_report.reason varchar(50) 上限 */
    private static final int REASON_MAX_LENGTH = 50;

    /** sys_report.description varchar(500) 上限 */
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    private final IAppReportService reportService;

    public AppReportController(IAppReportService reportService)
    {
        this.reportService = reportService;
    }

    /**
     * 提交举报（2.8.17）
     *
     * @param body 请求体 {target_type|targetType 必填, target_id|targetId 必填正整数,
     *             reason 必填, description 可选}
     * @return data = {reportId, status}
     */
    @PostMapping("/reports")
    public AppApiResponse<AppReportResultDto> report(@RequestBody(required = false) Map<String, Object> body)
    {
        String targetType = trimToNull(asString(body, "target_type", "targetType"));
        if (targetType == null)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "target_type 不能为空");
        }
        if (targetType.length() > TARGET_TYPE_MAX_LENGTH)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "target_type 长度不能超过 " + TARGET_TYPE_MAX_LENGTH);
        }
        Long targetId = asLong(body, "target_id", "targetId");
        if (targetId == null || targetId <= 0)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "target_id 不能为空且必须为正整数");
        }
        String reason = trimToNull(asString(body, "reason"));
        if (reason == null)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "reason 不能为空");
        }
        if (reason.length() > REASON_MAX_LENGTH)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "reason 长度不能超过 " + REASON_MAX_LENGTH);
        }
        String description = trimToNull(asString(body, "description"));
        if (description != null && description.length() > DESCRIPTION_MAX_LENGTH)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "description 长度不能超过 " + DESCRIPTION_MAX_LENGTH);
        }
        AppReportRequest request = new AppReportRequest();
        request.setTargetType(targetType);
        request.setTargetId(targetId);
        request.setReason(reason);
        request.setDescription(description);
        return AppApiResponse.ok(reportService.submitReport(request));
    }

    /**
     * 依次尝试多个键名取字符串值（兼容 snake_case / camelCase）。
     */
    private static String asString(Map<String, Object> body, String... keys)
    {
        if (body == null)
        {
            return null;
        }
        for (String key : keys)
        {
            Object value = body.get(key);
            if (value != null)
            {
                return String.valueOf(value);
            }
        }
        return null;
    }

    /**
     * 依次尝试多个键名取 Long（兼容 JSON 数字与数字字符串）。
     */
    private static Long asLong(Map<String, Object> body, String... keys)
    {
        if (body == null)
        {
            return null;
        }
        for (String key : keys)
        {
            Object value = body.get(key);
            if (value instanceof Number number)
            {
                return number.longValue();
            }
            if (value instanceof String text)
            {
                try
                {
                    return Long.parseLong(text.trim());
                }
                catch (NumberFormatException ignored)
                {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 去除首尾空白；空白串归一为 null。
     */
    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}