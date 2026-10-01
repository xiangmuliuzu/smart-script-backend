package com.smartscript.platform.user.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.user.domain.admin.AppUserCapabilityRow;
import com.smartscript.platform.user.domain.admin.AppUserDetail;
import com.smartscript.platform.user.domain.admin.AppUserRealNameRow;
import com.smartscript.platform.user.domain.admin.AppUserRoleRow;
import com.smartscript.platform.user.domain.admin.AppUserSummary;
import com.smartscript.platform.user.domain.admin.GrantableRole;

/**
 * A4 PC 管理域：App 用户查询（契约 §4 / §5.1）。
 *
 * 强制边界：
 *   - 所有查询在 SQL 层限定 user_type IN ('01','02','03')，不返回 PC 管理员。
 *     该限定写在 XML 中，调用方无法通过参数放宽。
 *   - 手机号由 SQL 表达式产出掩码，本 Mapper 不查询完整号码。
 *   - 过滤使用 MyBatis 参数绑定；排序字段由服务层白名单校验后传入
 *     （orderByColumn + isAsc），禁止直接拼接客户端原始字符串。
 *   - 角色、实名、作者状态均用批量查询装配，禁止逐行查询造成 N+1。
 */
public interface AppUserAdminMapper
{
    /**
     * App 用户分页查询。分页由 PageHelper 在服务层驱动。
     *
     * 支持的参数键：keyword、status、userType、roleCode、realNameStatus、
     * authorCapability、beginTime、endTime、orderByColumn、isAsc。
     */
    List<AppUserSummary> selectAppUserPage(Map<String, Object> params);

    /** App 用户详情；不属于 01/02/03 域时返回 null（调用方转 404）。 */
    AppUserDetail selectAppUserDetail(@Param("userId") Long userId);

    /** 批量取角色编码，供列表装配使用。 */
    List<AppUserRoleRow> selectRoleCodesByUserIds(@Param("userIds") List<Long> userIds);

    /** 批量取实名状态，供列表装配使用。 */
    List<AppUserRealNameRow> selectRealNameStatusByUserIds(@Param("userIds") List<Long> userIds);

    /** 批量取作者能力开关，供列表装配使用。 */
    List<AppUserCapabilityRow> selectAuthorCapabilityByUserIds(@Param("userIds") List<Long> userIds);

    /** 账号域判定：仅当属于 01/02/03 且未删除时返回该用户主键。 */
    Long selectManagedUserId(@Param("userId") Long userId);

    /** 读取当前状态用于幂等判定与条件更新。 */
    String selectStatus(@Param("userId") Long userId);

    /** 条件更新状态：仅当当前状态等于 expectedStatus 时才生效，返回影响行数。 */
    int updateStatusIfMatch(@Param("userId") Long userId,
                            @Param("expectedStatus") String expectedStatus,
                            @Param("status") String status,
                            @Param("updateBy") String updateBy);

    /** 该用户当前角色主键集合，用于计算等价请求是否幂等。 */
    List<Long> selectRoleIds(@Param("userId") Long userId);

    /** 事务替换角色授权前删除既有关系，返回删除行数。 */
    int deleteUserRoles(@Param("userId") Long userId);

    /** 写入角色授权关系，roleIds 已由服务层去重且校验为 App 可用角色。 */
    int insertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    /**
     * 可授予 App 用户的角色清单（契约 §5.1）。
     * 仅返回 app_grantable=1、status='0'、del_flag='0' 且非超级管理员的角色。
     * 权威来源为 sys_role.app_grantable（003 迁移引入），不使用任何配置或命名约定。
     */
    List<GrantableRole> selectGrantableRoles();

    /**
     * 校验可授权边界：返回 roleIds 中**不属于** App 可授权集合的角色个数。
     *
     * 判定完全在 SQL 层完成，依据 sys_role.app_grantable，并叠加启用、未删除、
     * 非超级管理员三条硬约束。超级管理员角色（role_id=1 或 role_key='admin'）
     * 即使被误标记为可授权也一律计入不可授权。
     */
    int countNonGrantableRoles(@Param("roleIds") List<Long> roleIds);
}
