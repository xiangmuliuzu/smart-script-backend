package com.smartscript.platform.user.constant;

/**
 * A4 PC 管理写接口的参数名登记表：① {@code @Log(excludeParamNames=...)} 的排除名（H9-LOG-02）；
 * ② 请求体白名单投影的契约字段名（第 12 批 H9-LOG-02 系统性修复，见 {@code A4AuditParamProjection}）。
 *
 * 背景：A4 写接口以 {@code Map<String,Object>} 接收请求体，若依 {@code LogAspect} 会把整张 Map
 * 序列化进 {@code sys_oper_log.oper_param}，而框架 {@code EXCLUDE_PROPERTIES} 只覆盖 4 个密码字段名。
 * 第 9 批实测：未声明的敏感键名（{@code code}、{@code refreshToken}）会被原样记入日志。
 *
 * 排除名（本类上半部分）都是验收标准 §9.3 点名的敏感类别（验证码、Token、手机号、身份证号），
 * 且都不是 A4 接口的契约字段，声明排除不会丢失任何业务审计信息。第 10 批声明后，
 * 残余为「已知名清单无法穷举未知键名」；第 12 批改由白名单投影（默认拒绝）系统性收敛，
 * 本清单保留为第二道防线。
 *
 * 注解数组元素必须是编译期常量，故逐个声明、由各接口 {@code @Log(excludeParamNames=...)} 引用。
 */
public final class AppAdminAuditParams
{
    private AppAdminAuditParams()
    {
    }

    // ---- Token 类（§9.3）----
    public static final String TOKEN = "token";
    public static final String ACCESS_TOKEN = "accessToken";
    public static final String REFRESH_TOKEN = "refreshToken";

    // ---- 验证码类（§9.3）----
    public static final String CODE = "code";
    public static final String SMS_CODE = "smsCode";
    public static final String CAPTCHA = "captcha";

    // ---- 个人敏感信息（§9.3）----
    public static final String PHONE = "phone";
    public static final String ID_NUMBER = "idNumber";

    // ---- 各接口自由文本（按接口声明；数值用于请求参数名 "body" 占位）----
    public static final String REASON = "reason";
    public static final String REJECT_REASON = "rejectReason";
    public static final String REPLY = "reply";
    public static final String BODY = "body";

    /**
     * 消息正文（H10-REV-02）。
     *
     * 消息创建接口的 {@code title}/{@code content} 是自由文本，可含手机号、身份证号或 Token；
     * 第 10 批复核实测其进入 {@code oper_param}。正文本身已持久化在业务表，操作日志保留
     * {@code requestId}/{@code type}/{@code userIds} 等定位字段即可完整还原「谁在何时向谁发布了哪条消息」，
     * 因此与 {@code reason} 等同口径排除，避免自由文本 PII 进入日志。
     */
    public static final String TITLE = "title";
    public static final String CONTENT = "content";

    // ---- 契约字段名（白名单投影用；《A4-管理接口契约》§5.1–§5.5 原文名，未改名）----
    /** §5.1 `PUT /app-users/{id}/status`。 */
    public static final String STATUS = "status";
    /** §5.1 `PUT /app-users/{id}/roles`。 */
    public static final String ROLE_IDS = "roleIds";
    /** §5.2 `PUT /real-name-applications/{id}/decision`。 */
    public static final String DECISION = "decision";
    /** §5.2 / §5.5 乐观并发期望状态。 */
    public static final String EXPECTED_STATUS = "expectedStatus";
    /** §5.3 `PUT /author-capabilities/{id}`。 */
    public static final String ENABLED = "enabled";
    /** §5.4 `POST /notifications` 幂等键。 */
    public static final String REQUEST_ID = "requestId";
    /** §5.4 消息类型。 */
    public static final String TYPE = "type";
    /** §5.4 可空业务引用类型。 */
    public static final String BUSINESS_TYPE = "businessType";
    /** §5.4 可空业务引用主键。 */
    public static final String BUSINESS_ID = "businessId";
    /** §5.4 收件人主键数组。 */
    public static final String USER_IDS = "userIds";
    /** §5.5 `PUT /feedback/{id}/handle` 动作。 */
    public static final String ACTION = "action";
}
