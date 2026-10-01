package com.ruoyi.web.controller.a4;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.smartscript.platform.user.constant.AppAdminAuditParams;
import com.smartscript.platform.user.constant.AppAdminConstants;

/**
 * A4 写接口请求体的**白名单审计投影**（H9-LOG-02 系统性修复，第 12 批）。
 *
 * 缺陷：A4 写接口以 {@code Map<String,Object>} 接收请求体，若依 {@code LogAspect} 会把整张 Map
 * 序列化进 {@code sys_oper_log.oper_param}；框架 {@code EXCLUDE_PROPERTIES} 只排除 4 个密码字段名，
 * 第 10 批补的 {@code excludeParamNames} 是「已知敏感名」清单，无法穷举任意未知键名
 * （如 {@code xToken}）与嵌套结构（如 {@code {"nested":{"token":…}}}、{@code roleIds:[1,{"token":…}]}）。
 *
 * 本类改为**默认拒绝**：控制器在业务读取之前调用 {@link #retain}，把请求体就地收敛为
 * 《A4-管理接口契约》§5.1–§5.5 声明的字段。未声明键一律丢弃；白名单键的值只有标量
 * （{@code CharSequence}/{@code Number}/{@code Boolean}/{@code null}）才保留，数组只保留标量元素，
 * 嵌套对象/数组一律丢弃。因此未知敏感键与嵌套字段在结构上不可能进入 {@code oper_param}，
 * 也不参与后续业务读取。
 *
 * 值级残余（H12-REV-01 / H13-REV-01 / H13-REV-03，第 13 批）：{@link #retain} 只保证「键」在契约内，不判定「值」的敏感性。
 * 客户端可控字段（消息创建的 {@code requestId}/{@code type}/{@code businessType}/{@code businessId}/{@code userIds}、
 * 角色授权的 {@code roleIds}）的值可承载令牌，仍会原样进入 {@code oper_param}。控制器在**业务读取之后**调用
 * {@link #sanitizeNotificationLog} / {@link #sanitizeRoleGrantLog} 按值级默认拒绝重写日志投影：
 * 自由字符串一律指纹、受控枚举仅留契约值、整数数组元素一律指纹（数字元素同样不可信）。
 *
 * 值级残余收敛（第 14 批，H-07）：其余写接口的非枚举字段——用户状态 {@code status}、实名审核
 * {@code decision}/{@code expectedStatus}、反馈处理 {@code action}/{@code expectedStatus}、
 * 作者能力 {@code enabled}——同样不判定值敏感性（实测合成令牌原样进入 {@code oper_param}，
 * 见第 14 批记录 §2）。控制器在业务读取之后调用 {@link #sanitizeStatusChangeLog} /
 * {@link #sanitizeRealNameDecisionLog} / {@link #sanitizeAuthorCapabilityLog} /
 * {@link #sanitizeFeedbackHandleLog}：受控枚举/布尔仅保留契约值与业务可解析值，
 * 其余取值一律不可逆指纹。
 *
 * 约束与边界：
 *   - 契约字段名全部取自 {@link AppAdminAuditParams}（即契约 §5 原文名），未新增、未改名。
 *   - 只收敛请求体，不改变 {@code @Log} 的 title/businessType/operatorType/isSaveResponseData，
 *     也不改变任何服务层调用与响应装配，故既有响应逐字不变。
 *   - 自由文本（reason/rejectReason/reply/title/content/token）仍由各接口
 *     {@code excludeParamNames} 排除（H9-LOG-01、H10-REV-02 口径），本类不改其判定；
 *     该清单保留为第二道防线。
 */
public final class A4AuditParamProjection
{
    /** 1. `POST /api/v1/admin/material-refs/redeem`（契约 §7 短时授权；A4-G4：令牌走请求体）。 */
    public static final String[] MATERIAL_REDEEM = { AppAdminAuditParams.TOKEN };

    /** 2. `PUT /api/v1/admin/app-users/{userId}/status`（契约 §5.1）。 */
    public static final String[] APP_USER_STATUS = { AppAdminAuditParams.STATUS, AppAdminAuditParams.REASON };

    /** 3. `PUT /api/v1/admin/app-users/{userId}/roles`（契约 §5.1）。 */
    public static final String[] APP_USER_ROLES = { AppAdminAuditParams.ROLE_IDS, AppAdminAuditParams.REASON };

    /** 4. `PUT /api/v1/admin/real-name-applications/{applicationId}/decision`（契约 §5.2）。 */
    public static final String[] REAL_NAME_DECISION = { AppAdminAuditParams.DECISION,
            AppAdminAuditParams.REJECT_REASON, AppAdminAuditParams.EXPECTED_STATUS };

    /** 5. `PUT /api/v1/admin/author-capabilities/{userId}`（契约 §5.3）。 */
    public static final String[] AUTHOR_CAPABILITY = { AppAdminAuditParams.ENABLED, AppAdminAuditParams.REASON };

    /** 6. `POST /api/v1/admin/notifications`（契约 §5.4）。 */
    public static final String[] NOTIFICATION_CREATE = { AppAdminAuditParams.REQUEST_ID, AppAdminAuditParams.TYPE,
            AppAdminAuditParams.TITLE, AppAdminAuditParams.CONTENT, AppAdminAuditParams.BUSINESS_TYPE,
            AppAdminAuditParams.BUSINESS_ID, AppAdminAuditParams.USER_IDS };

    /** 7. `PUT /api/v1/admin/feedback/{feedbackId}/handle`（契约 §5.5）。 */
    public static final String[] FEEDBACK_HANDLE = { AppAdminAuditParams.ACTION, AppAdminAuditParams.REPLY,
            AppAdminAuditParams.EXPECTED_STATUS };

    /**
     * 消息创建接口中**客户端可控的自由字符串**字段（契约 §5.4）：日志中只记录不可逆指纹。
     *
     * `type` 是受控枚举、`userIds` 是整数数组，分别走枚举与数组策略，故不在此列。
     */
    public static final String[] NOTIFICATION_LOG_FINGERPRINT = { AppAdminAuditParams.REQUEST_ID,
            AppAdminAuditParams.BUSINESS_TYPE, AppAdminAuditParams.BUSINESS_ID };

    /**
     * 消息创建接口中**客户端可控的受控枚举字段**（契约 §5.4 `type`）。
     *
     * 服务层在业务阶段才校验枚举，非法值仍会经失败日志序列化，故日志投影只保留契约枚举值，
     * 其余取值一律指纹化（H13-REV-01）。
     */
    public static final String[] NOTIFICATION_LOG_ENUM = { AppAdminAuditParams.TYPE };

    /**
     * 消息创建接口中**契约类型为整数数组的字段**（契约 §5.4 `userIds`）。
     *
     * 数组元素一律指纹化（H13-REV-03）：**数字元素同样不可信**——JSON 数字可承载纯数字令牌、
     * 手机号或证件号，服务层的 {@code longList} 也不会在日志序列化前拒绝它们。
     */
    public static final String[] NOTIFICATION_LOG_ID_ARRAYS = { AppAdminAuditParams.USER_IDS };

    /**
     * 角色授权接口中**契约类型为整数数组的字段**（契约 §5.1 `roleIds`）。
     *
     * 与 {@link #NOTIFICATION_LOG_ID_ARRAYS} 同一构造、同一缺陷（H13-REV-03），一并按默认拒绝处理。
     */
    public static final String[] ROLE_LOG_ID_ARRAYS = { AppAdminAuditParams.ROLE_IDS };

    /**
     * 用户状态接口中**客户端可控的受控枚举字段**（契约 §5.1 `status`，取值 `"0"|"1"`）。
     *
     * 第 14 批：非法取值（任意自由字符串/数字）可承载令牌或 PII，一律指纹化。
     */
    public static final String[] STATUS_LOG_ENUM = { AppAdminAuditParams.STATUS };

    /**
     * 实名审核接口中**客户端可控的受控枚举字段**（契约 §5.2 `decision`，取值 `APPROVE|REJECT`）。
     */
    public static final String[] DECISION_LOG_ENUM = { AppAdminAuditParams.DECISION };

    /**
     * 实名审核接口中**乐观并发期望状态**（契约 §5.2 `expectedStatus`，固定 `PENDING`）。
     */
    public static final String[] REAL_NAME_EXPECTED_LOG_ENUM = { AppAdminAuditParams.EXPECTED_STATUS };

    /**
     * 反馈处理接口中**客户端可控的受控枚举字段**（契约 §5.5 `action`，取值 `ACCEPT|REPLY|CLOSE`）。
     */
    public static final String[] FEEDBACK_ACTION_LOG_ENUM = { AppAdminAuditParams.ACTION };

    /**
     * 反馈处理接口中**乐观并发期望状态**（契约 §5.5 `expectedStatus`，
     * 取值为反馈状态机 `SUBMITTED/PROCESSING/REPLIED/CLOSED`）。
     */
    public static final String[] FEEDBACK_EXPECTED_LOG_ENUM = { AppAdminAuditParams.EXPECTED_STATUS };

    /**
     * 作者能力接口中**契约类型为布尔的字段**（契约 §5.3 `enabled`）。
     *
     * 第 14 批：仅布尔值保持可读；其余取值（任意字符串/数字/数组）可承载令牌或 PII，一律指纹化。
     * H14-REV-02 复核收紧：字符串 {@code "true"/"false"} 已随契约类型闸门一并拒绝（业务 400），
     * 日志投影同步收窄为仅布尔可读——可读集与契约可接受集保持一致。
     */
    public static final String[] AUTHOR_CAPABILITY_LOG_BOOL = { AppAdminAuditParams.ENABLED };

    /** 契约 §5.4 允许的消息类型枚举值（与 {@code UserNotificationAdminService.isKnownType} 同源常量）。 */
    private static final Set<String> NOTIFICATION_TYPES = Set.of(
            AppAdminConstants.NOTIFICATION_SYSTEM, AppAdminConstants.NOTIFICATION_REVIEW,
            AppAdminConstants.NOTIFICATION_TRANSACTION, AppAdminConstants.NOTIFICATION_BENEFIT);

    /** 契约 §5.1 允许的账号状态枚举值（与 {@code AppUserAdminService.changeStatus} 同源常量）。 */
    private static final Set<String> APP_USER_STATUSES = Set.of(
            AppAdminConstants.STATUS_NORMAL, AppAdminConstants.STATUS_DISABLED);

    /** 契约 §5.2 允许的审核动作枚举值。 */
    private static final Set<String> REAL_NAME_DECISIONS = Set.of(
            AppAdminConstants.DECISION_APPROVE, AppAdminConstants.DECISION_REJECT);

    /** 契约 §5.2 固定的期望状态。 */
    private static final Set<String> REAL_NAME_EXPECTED = Set.of(AppAdminConstants.REAL_NAME_PENDING);

    /** 契约 §5.5 允许的处理动作枚举值。 */
    private static final Set<String> FEEDBACK_ACTIONS = Set.of(
            AppAdminConstants.FEEDBACK_ACTION_ACCEPT, AppAdminConstants.FEEDBACK_ACTION_REPLY,
            AppAdminConstants.FEEDBACK_ACTION_CLOSE);

    /** 契约 §5.5 允许的反馈期望状态（状态机四态，作为乐观并发期望值）。 */
    private static final Set<String> FEEDBACK_STATUSES = Set.of(
            AppAdminConstants.FEEDBACK_SUBMITTED, AppAdminConstants.FEEDBACK_PROCESSING,
            AppAdminConstants.FEEDBACK_REPLIED, AppAdminConstants.FEEDBACK_CLOSED);

    /** 非白名单值（对象/嵌套结构）的丢弃标记。 */
    private static final Object DROP = new Object();

    /** 日志指纹前缀：便于与真实 id 区分，表明该值已被不可逆脱敏。 */
    private static final String FINGERPRINT_PREFIX = "fp:";

    /** 指纹取 SHA-256 十六进制前缀的位数（与既有 MaterialAccessTokenService.fingerprint 同口径）。 */
    private static final int FINGERPRINT_HEX_LENGTH = 12;

    private A4AuditParamProjection()
    {
    }

    /**
     * 就地把请求体收敛为白名单字段 + 安全值。
     *
     * <p>白名单为空时收敛为空 Map（默认拒绝）；{@code body} 为空或 {@code null} 时不做任何事。
     * 保持原插入顺序；对无值级脱敏的接口，合法请求的 {@code oper_param} 与收敛前逐字一致
     * （消息创建接口随后另做值级指纹，见 {@link #fingerprintForLog}）。</p>
     *
     * @param body    由 {@code @RequestBody} 绑定的可变 Map（Jackson 产出 LinkedHashMap）
     * @param allowed 该接口的契约字段名（见本类各常量）
     */
    public static void retain(Map<String, Object> body, String... allowed)
    {
        if (body == null || body.isEmpty())
        {
            return;
        }
        Set<String> allow = Set.of(allowed);
        Map<String, Object> kept = new LinkedHashMap<>(body.size());
        for (Map.Entry<String, Object> entry : body.entrySet())
        {
            String key = entry.getKey();
            if (key == null || !allow.contains(key))
            {
                continue;
            }
            Object safe = auditable(entry.getValue());
            if (safe != DROP)
            {
                kept.put(key, safe);
            }
        }
        body.clear();
        body.putAll(kept);
    }

    /**
     * 把客户端可控自由字符串字段的值**就地替换为不可逆指纹**，使日志只见到指纹（H12-REV-01）。
     *
     * <p>调用时机必须是**业务读取之后**：控制器先把原值读入局部变量并交给服务层（幂等键、
     * 业务引用均依赖原值），再调用本方法；{@code @Log} 在控制器方法返回后才序列化参数，
     * 因此被指纹化的只有 {@code oper_param}，业务结果与响应逐字不变。</p>
     *
     * <p>一律指纹化、不看形态（base64url / 全大写 / 纯数字都不可区分，见 {@code OperLogSanitizer}
     * 的教训），因此任一取值都不可能以原值落入日志。字段仍保留（「只脱敏、不删除」），
     * 由候选值重算指纹即可核对，审计关联能力不丢失。</p>
     *
     * @param body 由 {@code @RequestBody} 绑定的可变 Map
     * @param keys 需指纹化的字段名（见 {@link #NOTIFICATION_LOG_FINGERPRINT}）
     */
    public static void fingerprintForLog(Map<String, Object> body, String... keys)
    {
        if (body == null || body.isEmpty())
        {
            return;
        }
        for (String key : keys)
        {
            Object value = body.get(key);
            if (value == null)
            {
                continue;
            }
            body.put(key, fingerprintValue(value));
        }
    }

    /**
     * 标量整体指纹；数组**逐元素**指纹（数字与字符串同等处理，见 {@link #NOTIFICATION_LOG_ID_ARRAYS}）；
     * null 元素保留 null。仅用于日志投影，业务读取仍取原值。
     */
    private static Object fingerprintValue(Object value)
    {
        if (value instanceof List<?> list)
        {
            List<Object> safe = new ArrayList<>(list.size());
            for (Object item : list)
            {
                safe.add(item == null ? null : FINGERPRINT_PREFIX + fingerprint(String.valueOf(item)));
            }
            return safe;
        }
        return FINGERPRINT_PREFIX + fingerprint(String.valueOf(value));
    }

    /**
     * 消息创建接口日志投影的**值级默认拒绝策略**（H12-REV-01、H13-REV-01、H13-REV-03）。
     *
     * <p>契约 §5.4 的每个允许字段都按「只有可证明安全的取值才原样保留」处理：
     * 自由字符串（{@code requestId}/{@code businessType}/{@code businessId}）一律指纹；
     * 受控枚举（{@code type}）仅保留契约枚举值；整数数组（{@code userIds}）**元素一律指纹**——
     * JSON 数字元素同样可承载纯数字令牌、手机号或证件号（H13-REV-03）；
     * 其余取值（含非契约类型）也一律指纹。</p>
     *
     * <p>与 {@link #fingerprintForLog} 相同，必须在**业务读取之后**调用：服务层仍取原值，
     * 幂等键与业务引用不受影响。</p>
     */
    public static void sanitizeNotificationLog(Map<String, Object> body)
    {
        if (body == null || body.isEmpty())
        {
            return;
        }
        fingerprintForLog(body, NOTIFICATION_LOG_FINGERPRINT);
        fingerprintForLog(body, NOTIFICATION_LOG_ID_ARRAYS);
        for (String key : NOTIFICATION_LOG_ENUM)
        {
            fingerprintNonContractEnum(body, key, NOTIFICATION_TYPES);
        }
    }

    /**
     * 角色授权接口日志投影：{@code roleIds} 元素一律指纹（与 {@code userIds} 同构造，H13-REV-03）。
     *
     * <p>同样必须在业务读取之后调用：服务层仍取原值进行角色校验。</p>
     */
    public static void sanitizeRoleGrantLog(Map<String, Object> body)
    {
        if (body == null || body.isEmpty())
        {
            return;
        }
        fingerprintForLog(body, ROLE_LOG_ID_ARRAYS);
    }

    /**
     * 用户状态接口日志投影的**值级默认拒绝**（第 14 批，H-07 值级残余）。
     *
     * <p>契约 §5.1 的 {@code status} 仅保留契约枚举值 {@code "0"/"1"}；其余取值
     * （任意自由字符串、数字、数组）可承载令牌或 PII，一律指纹化。
     * 必须在业务读取之后调用：服务层仍取原值做枚举校验与状态机更新。</p>
     */
    public static void sanitizeStatusChangeLog(Map<String, Object> body)
    {
        if (body == null || body.isEmpty())
        {
            return;
        }
        for (String key : STATUS_LOG_ENUM)
        {
            fingerprintNonContractEnum(body, key, APP_USER_STATUSES);
        }
    }

    /**
     * 实名审核接口日志投影的**值级默认拒绝**（第 14 批）。
     *
     * <p>契约 §5.2 的 {@code decision} 仅保留 {@code APPROVE/REJECT}、{@code expectedStatus}
     * 仅保留固定的 {@code PENDING}；其余取值一律指纹化。{@code rejectReason} 仍由接口
     * {@code excludeParamNames} 排除（H9-LOG-01 口径不变）。必须在业务读取之后调用。</p>
     */
    public static void sanitizeRealNameDecisionLog(Map<String, Object> body)
    {
        if (body == null || body.isEmpty())
        {
            return;
        }
        for (String key : DECISION_LOG_ENUM)
        {
            fingerprintNonContractEnum(body, key, REAL_NAME_DECISIONS);
        }
        for (String key : REAL_NAME_EXPECTED_LOG_ENUM)
        {
            fingerprintNonContractEnum(body, key, REAL_NAME_EXPECTED);
        }
    }

    /**
     * 作者能力接口日志投影的**值级默认拒绝**（第 14 批）。
     *
     * <p>契约 §5.3 的 {@code enabled} 为布尔：仅 {@code Boolean} 取值保持可读；
     * 其余取值（任意字符串/数字）可承载令牌或 PII，一律指纹化（H14-REV-02 复核收紧后
     * 字符串 {@code "true"/"false"} 也会被业务侧 400 拒绝，日志中同样以指纹记录）。
     * {@code reason} 仍由接口 {@code excludeParamNames} 排除。必须在业务读取之后调用。</p>
     */
    public static void sanitizeAuthorCapabilityLog(Map<String, Object> body)
    {
        if (body == null || body.isEmpty())
        {
            return;
        }
        for (String key : AUTHOR_CAPABILITY_LOG_BOOL)
        {
            fingerprintNonContractBoolean(body, key);
        }
    }

    /**
     * 反馈处理接口日志投影的**值级默认拒绝**（第 14 批）。
     *
     * <p>契约 §5.5 的 {@code action} 仅保留 {@code ACCEPT/REPLY/CLOSE}、{@code expectedStatus}
     * 仅保留反馈状态机四态；其余取值一律指纹化。{@code reply} 仍由接口
     * {@code excludeParamNames} 排除（自由文本口径不变）。必须在业务读取之后调用。</p>
     */
    public static void sanitizeFeedbackHandleLog(Map<String, Object> body)
    {
        if (body == null || body.isEmpty())
        {
            return;
        }
        for (String key : FEEDBACK_ACTION_LOG_ENUM)
        {
            fingerprintNonContractEnum(body, key, FEEDBACK_ACTIONS);
        }
        for (String key : FEEDBACK_EXPECTED_LOG_ENUM)
        {
            fingerprintNonContractEnum(body, key, FEEDBACK_STATUSES);
        }
    }

    /** 仅保留允许集中的枚举常量；非法枚举、非字符串或数组取值一律指纹。 */
    private static void fingerprintNonContractEnum(Map<String, Object> body, String key, Set<String> allowed)
    {
        Object value = body.get(key);
        if (value != null && !(value instanceof CharSequence cs && allowed.contains(cs.toString())))
        {
            body.put(key, FINGERPRINT_PREFIX + fingerprint(String.valueOf(value)));
        }
    }

    /** 仅保留布尔值（契约 §5.3 可接受集）；其余取值（含 {@code "true"/"false"} 字符串）一律指纹。 */
    private static void fingerprintNonContractBoolean(Map<String, Object> body, String key)
    {
        Object value = body.get(key);
        if (value == null || value instanceof Boolean)
        {
            return;
        }
        body.put(key, FINGERPRINT_PREFIX + fingerprint(String.valueOf(value)));
    }

    /** SHA-256 前 {@value #FINGERPRINT_HEX_LENGTH} 位十六进制；不可逆，仅用于日志关联。 */
    private static String fingerprint(String value)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(FINGERPRINT_HEX_LENGTH);
            for (int i = 0; i < FINGERPRINT_HEX_LENGTH / 2; i++)
            {
                hex.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
                hex.append(Character.forDigit(digest[i] & 0xF, 16));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            // 日志宁可少记也不外泄：摘要不可用时用固定占位符，绝不回退为原值
            return "unavailable";
        }
    }

    /**
     * 仅接受标量与「标量数组」；对象、嵌套数组等一律丢弃。
     * 数组逐元素过滤，因此嵌套在契约数组字段里的未知结构也不会进入日志。
     */
    private static Object auditable(Object value)
    {
        if (isScalar(value))
        {
            return value;
        }
        if (value instanceof List<?> list)
        {
            List<Object> scalars = new ArrayList<>(list.size());
            for (Object item : list)
            {
                if (isScalar(item))
                {
                    scalars.add(item);
                }
            }
            return scalars;
        }
        return DROP;
    }

    private static boolean isScalar(Object value)
    {
        return value == null || value instanceof CharSequence || value instanceof Number || value instanceof Boolean;
    }
}
