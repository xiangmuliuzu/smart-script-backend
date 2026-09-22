package com.smartscript.platform.user.constant;

/**
 * A4 PC 管理域常量：账号域、状态机与权限标识。
 *
 * 取值来源：A4-管理接口契约.md / A4-PC管理实施方案.md §8。
 * 与数据库 CHECK 约束 ck_user_feedback_status 保持一致，二者不得单边修改。
 */
public final class AppAdminConstants
{
    private AppAdminConstants()
    {
    }

    /** 账号域：PC 管理员。A4 管理接口不得查询或修改该域。 */
    public static final String USER_TYPE_PC_ADMIN = "00";

    /** 账号域：App 普通用户。 */
    public static final String USER_TYPE_APP_USER = "01";

    /** 账号域：App 创作者。 */
    public static final String USER_TYPE_APP_CREATOR = "02";

    /** 账号域：App 甲方。 */
    public static final String USER_TYPE_APP_CLIENT = "03";

    /** A4 可管理账号域，用于 SQL 层强制限定。 */
    public static final String[] MANAGED_USER_TYPES = { USER_TYPE_APP_USER, USER_TYPE_APP_CREATOR, USER_TYPE_APP_CLIENT };

    /** 账号状态：正常。 */
    public static final String STATUS_NORMAL = "0";

    /** 账号状态：停用。 */
    public static final String STATUS_DISABLED = "1";

    /** 逻辑删除标记：未删除。 */
    public static final String DEL_FLAG_NORMAL = "0";

    // ---- 实名申请状态机（契约 §5.2）----
    public static final String REAL_NAME_PENDING = "PENDING";
    public static final String REAL_NAME_APPROVED = "APPROVED";
    public static final String REAL_NAME_REJECTED = "REJECTED";

    /** 审核动作。 */
    public static final String DECISION_APPROVE = "APPROVE";
    public static final String DECISION_REJECT = "REJECT";

    // ---- 反馈状态机（契约 §5.5，与 DB CHECK 约束同名取值）----
    public static final String FEEDBACK_SUBMITTED = "SUBMITTED";
    public static final String FEEDBACK_PROCESSING = "PROCESSING";
    public static final String FEEDBACK_REPLIED = "REPLIED";
    public static final String FEEDBACK_CLOSED = "CLOSED";

    /** 反馈处理动作。 */
    public static final String FEEDBACK_ACTION_ACCEPT = "ACCEPT";
    public static final String FEEDBACK_ACTION_REPLY = "REPLY";
    public static final String FEEDBACK_ACTION_CLOSE = "CLOSE";

    // ---- 消息类型（契约 §3.4）----
    public static final String NOTIFICATION_SYSTEM = "SYSTEM";
    public static final String NOTIFICATION_REVIEW = "REVIEW";
    public static final String NOTIFICATION_TRANSACTION = "TRANSACTION";
    public static final String NOTIFICATION_BENEFIT = "BENEFIT";

    // ---- 权限标识（契约 §4 / 实施方案 §8）----
    public static final String PERM_APP_LIST = "user:app:list";
    public static final String PERM_APP_QUERY = "user:app:query";
    public static final String PERM_APP_STATUS = "user:app:status";
    public static final String PERM_APP_GRANT = "user:app:grant";
    public static final String PERM_REALNAME_LIST = "user:realname:list";
    public static final String PERM_REALNAME_QUERY = "user:realname:query";
    public static final String PERM_REALNAME_AUDIT = "user:realname:audit";
    public static final String PERM_CREATOR_LIST = "user:creator:list";
    public static final String PERM_CREATOR_UPDATE = "user:creator:update";
    public static final String PERM_MESSAGE_LIST = "user:message:list";
    public static final String PERM_MESSAGE_ADD = "user:message:add";
    public static final String PERM_MESSAGE_QUERY = "user:message:query";
    public static final String PERM_FEEDBACK_LIST = "user:feedback:list";
    public static final String PERM_FEEDBACK_QUERY = "user:feedback:query";
    public static final String PERM_FEEDBACK_HANDLE = "user:feedback:handle";

    /** 默认权限父菜单：A4 顶级目录（迁移 002 固定 menu_id）。 */
    public static final long A4_MENU_ROOT_ID = 3000L;

    /**
     * 超级管理员角色判定：角色主键与角色编码。
     *
     * 该角色**永久禁止**授予 App 用户，无论 sys_role.app_grantable 标记为何值。
     * 该约束由 SQL 层（AppUserAdminMapper）强制，不依赖标记值兜底。
     */
    public static final long SUPER_ADMIN_ROLE_ID = 1L;
    public static final String SUPER_ADMIN_ROLE_KEY = "admin";

    /** 分页上限（契约 §1：1<=pageSize<=100）。 */
    public static final int PAGE_SIZE_MAX = 100;

    /**
     * 判断账号类型是否属于 A4 可管理域。SQL 层已强制限定，此处用于服务层防御性校验。
     */
    public static boolean isManagedUserType(String userType)
    {
        if (userType == null)
        {
            return false;
        }
        for (String type : MANAGED_USER_TYPES)
        {
            if (type.equals(userType))
            {
                return true;
            }
        }
        return false;
    }
}
