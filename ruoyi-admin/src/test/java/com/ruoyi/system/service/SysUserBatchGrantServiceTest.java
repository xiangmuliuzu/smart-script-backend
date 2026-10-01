package com.ruoyi.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.SysUserRole;
import com.ruoyi.system.mapper.SysRoleMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.mapper.SysUserRoleMapper;
import com.ruoyi.system.service.impl.SysUserBatchGrantServiceImpl;
import com.ruoyi.web.controller.system.SysUserBatchGrantController;

/**
 * A 模块：PC 管理员账号批量授权——校验、事务边界与幂等。
 *
 * 覆盖：操作者权限注解、混入 APP 账号、受保护账号/角色、重复提交幂等、
 * 部分失败整体拒绝（不落任何行）。事务回滚由 @Transactional 保证，
 * 本测试验证「任一目标不合规时不得发起写入」这一前置闸门。
 */
class SysUserBatchGrantServiceTest
{
    private SysUserMapper userMapper;
    private SysRoleMapper roleMapper;
    private SysUserRoleMapper userRoleMapper;
    private com.ruoyi.system.service.ISysUserService userService;
    private com.ruoyi.system.service.ISysRoleService roleService;
    private SysUserBatchGrantServiceImpl service;

    @BeforeEach
    void setUp()
    {
        userMapper = mock(SysUserMapper.class);
        roleMapper = mock(SysRoleMapper.class);
        userRoleMapper = mock(SysUserRoleMapper.class);
        userService = mock(com.ruoyi.system.service.ISysUserService.class);
        roleService = mock(com.ruoyi.system.service.ISysRoleService.class);
        service = new SysUserBatchGrantServiceImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "userMapper", userMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "userRoleMapper", userRoleMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "userService", userService);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "roleService", roleService);
    }

    private static SysUser pcAdmin(long id)
    {
        SysUser u = new SysUser();
        u.setUserId(id);
        u.setUserName("admin" + id);
        u.setUserType("00");
        u.setStatus("0");
        u.setDelFlag("0");
        return u;
    }

    private static SysRole role(long id, String key, String status)
    {
        SysRole r = new SysRole();
        r.setRoleId(id);
        r.setRoleName("角色" + id);
        r.setRoleKey(key);
        r.setStatus(status);
        r.setDelFlag("0");
        return r;
    }

    // ---------------- 操作者权限（控制器注解为第一道闸门） ----------------

    @Test
    void controllerRequiresSystemUserEditPermission() throws Exception
    {
        Method m = SysUserBatchGrantController.class.getMethod("batchGrantRoles",
                SysUserBatchGrantController.BatchGrantRolesBody.class);
        PreAuthorize pre = m.getAnnotation(PreAuthorize.class);
        org.junit.jupiter.api.Assertions.assertNotNull(pre, "批量授权必须声明 @PreAuthorize");
        assertTrue(pre.value().contains("system:user:edit"),
                "操作者必须具备 system:user:edit（与原生角色分配同权限）");
        Log log = m.getAnnotation(Log.class);
        org.junit.jupiter.api.Assertions.assertNotNull(log, "写操作必须记入若依操作日志");
        assertEquals(BusinessType.GRANT, log.businessType());
    }

    @Test
    void serviceRejectsOperatorWithoutPcAdminDomain()
    {
        // 生产请求经管理链必有 LoginUser principal；模拟 01 账号持牌调用被服务层防御拒绝
        SysUser appOperator = pcAdmin(9);
        appOperator.setUserType("01");
        com.ruoyi.common.core.domain.model.LoginUser loginUser =
                Mockito.mock(com.ruoyi.common.core.domain.model.LoginUser.class);
        Mockito.when(loginUser.getUser()).thenReturn(appOperator);
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        loginUser, null, Collections.emptyList());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try
        {
            ServiceException e = assertThrows(ServiceException.class,
                    () -> service.grantRolesToUsers(Arrays.asList(2L), Arrays.asList(3L)));
            assertTrue(e.getMessage().contains("账号类型"));
            verify(userRoleMapper, never()).batchUserRole(anyList());
        }
        finally
        {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    // ---------------- 目标账号校验 ----------------

    @Test
    void mixedAppAccountRejectsWholeBatch()
    {
        when(userMapper.selectUserById(2L)).thenReturn(pcAdmin(2));
        SysUser appUser = pcAdmin(3);
        appUser.setUserType("01");
        when(userMapper.selectUserById(3L)).thenReturn(appUser);
        when(roleMapper.selectRoleById(10L)).thenReturn(role(10L, "operator", "0"));

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.grantRolesToUsers(Arrays.asList(2L, 3L), Arrays.asList(10L)));
        assertTrue(e.getMessage().contains("不是 PC 管理员"), "混入 APP 账号必须整体拒绝：" + e.getMessage());
        verify(userRoleMapper, never()).selectUserRolesByUserIds(anyList());
        verify(userRoleMapper, never()).batchUserRole(anyList());
    }

    @Test
    void nullUserTypeTargetRejected()
    {
        // 只接受明确的 user_type='00'：NULL（历史脏数据）一律拒绝
        SysUser legacy = pcAdmin(4);
        legacy.setUserType(null);
        when(userMapper.selectUserById(4L)).thenReturn(legacy);
        when(roleMapper.selectRoleById(10L)).thenReturn(role(10L, "operator", "0"));

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.grantRolesToUsers(Arrays.asList(4L), Arrays.asList(10L)));
        assertTrue(e.getMessage().contains("不是 PC 管理员"));
        verify(userRoleMapper, never()).batchUserRole(anyList());
    }

    @Test
    void nullUserTypeOperatorRejected()
    {
        SysUser legacyOperator = pcAdmin(8);
        legacyOperator.setUserType(null);
        com.ruoyi.common.core.domain.model.LoginUser loginUser =
                Mockito.mock(com.ruoyi.common.core.domain.model.LoginUser.class);
        Mockito.when(loginUser.getUser()).thenReturn(legacyOperator);
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        loginUser, null, Collections.emptyList());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try
        {
            ServiceException e = assertThrows(ServiceException.class,
                    () -> service.grantRolesToUsers(Arrays.asList(2L), Arrays.asList(10L)));
            assertTrue(e.getMessage().contains("账号类型"));
            verify(userRoleMapper, never()).batchUserRole(anyList());
        }
        finally
        {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void protectedSuperAdminAccountRejected()
    {
        when(userMapper.selectUserById(1L)).thenReturn(pcAdmin(1));
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.grantRolesToUsers(Arrays.asList(1L), Arrays.asList(10L)));
        assertTrue(e.getMessage().contains("超级管理员账号"));
        verify(userRoleMapper, never()).batchUserRole(anyList());
    }

    // ---------------- 角色校验 ----------------

    @Test
    void protectedSuperAdminRoleRejected()
    {
        when(userMapper.selectUserById(2L)).thenReturn(pcAdmin(2));
        when(roleMapper.selectRoleById(1L)).thenReturn(role(1L, "admin", "0"));

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.grantRolesToUsers(Arrays.asList(2L), Arrays.asList(1L)));
        assertTrue(e.getMessage().contains("超级管理员角色"));
        verify(userRoleMapper, never()).batchUserRole(anyList());
    }

    @Test
    void adminRoleKeyRejectedEvenWithOtherId()
    {
        when(userMapper.selectUserById(2L)).thenReturn(pcAdmin(2));
        when(roleMapper.selectRoleById(99L)).thenReturn(role(99L, "admin", "0"));

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.grantRolesToUsers(Arrays.asList(2L), Arrays.asList(99L)));
        assertTrue(e.getMessage().contains("超级管理员角色"), "role_key=admin 无论 roleId 为何都必须拒绝");
        verify(userRoleMapper, never()).batchUserRole(anyList());
    }

    @Test
    void disabledOrDeletedRoleRejected()
    {
        when(userMapper.selectUserById(2L)).thenReturn(pcAdmin(2));
        when(roleMapper.selectRoleById(10L)).thenReturn(role(10L, "ops", "1"));

        assertThrows(ServiceException.class,
                () -> service.grantRolesToUsers(Arrays.asList(2L), Arrays.asList(10L)));

        SysRole deleted = role(11L, "ops", "0");
        deleted.setDelFlag("2");
        when(roleMapper.selectRoleById(11L)).thenReturn(deleted);
        assertThrows(ServiceException.class,
                () -> service.grantRolesToUsers(Arrays.asList(2L), Arrays.asList(11L)));
        verify(userRoleMapper, never()).batchUserRole(anyList());
    }

    // ---------------- 事务边界 / 部分失败 ----------------

    @Test
    void oneInvalidTargetPreventsAllWrites()
    {
        when(userMapper.selectUserById(2L)).thenReturn(pcAdmin(2));
        when(userMapper.selectUserById(3L)).thenReturn(null);
        // 第 2 个目标不存在 → 整批拒绝，禁止对第 1 个目标先写
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.grantRolesToUsers(Arrays.asList(2L, 3L), Arrays.asList(10L)));
        assertTrue(e.getMessage().contains("不存在"));
        verify(userRoleMapper, never()).batchUserRole(anyList());
    }

    // ---------------- 幂等与增量 ----------------

    @Test
    void duplicateSubmitIsIdempotentAndSkipsExisting()
    {
        when(userMapper.selectUserById(2L)).thenReturn(pcAdmin(2));
        when(roleMapper.selectRoleById(10L)).thenReturn(role(10L, "operator", "0"));
        when(roleMapper.selectRoleById(11L)).thenReturn(role(11L, "auditor", "0"));

        // 第二次提交：2-10 已持有，2-11 缺失
        SysUserRole owned = new SysUserRole();
        owned.setUserId(2L);
        owned.setRoleId(10L);
        when(userRoleMapper.selectUserRolesByUserIds(Arrays.asList(2L)))
                .thenReturn(List.of(owned))
                .thenReturn(List.of(owned, pair(2L, 11L)));

        Map<String, Object> first = service.grantRolesToUsers(Arrays.asList(2L), Arrays.asList(10L, 11L));
        assertEquals(1, ((Number) first.get("grantedCount")).longValue());
        assertEquals(1, ((Number) first.get("skippedCount")).longValue());

        // 重复提交同一批：全部已持有，不再写库
        Map<String, Object> second = service.grantRolesToUsers(Arrays.asList(2L), Arrays.asList(10L, 11L));
        assertEquals(0, ((Number) second.get("grantedCount")).longValue());
        assertEquals(2, ((Number) second.get("skippedCount")).longValue());
    }

    @Test
    void outOfScopeUserRejectedBeforeAnyWrite()
    {
        // 受限数据权限的管理员：对范围外账号整批拒绝（与原生 /system/user/authRole 同闸门）
        when(userMapper.selectUserById(2L)).thenReturn(pcAdmin(2));
        when(roleMapper.selectRoleById(10L)).thenReturn(role(10L, "operator", "0"));
        org.mockito.Mockito.doThrow(new ServiceException("没有权限访问用户数据！"))
                .when(userService).checkUserDataScope(2L);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.grantRolesToUsers(Arrays.asList(2L), Arrays.asList(10L)));
        assertTrue(e.getMessage().contains("没有权限访问用户数据"));
        verify(userRoleMapper, never()).batchUserRole(anyList());
    }

    @Test
    void outOfScopeRoleRejectedBeforeAnyWrite()
    {
        when(userMapper.selectUserById(2L)).thenReturn(pcAdmin(2));
        when(roleMapper.selectRoleById(10L)).thenReturn(role(10L, "operator", "0"));
        org.mockito.Mockito.doThrow(new ServiceException("没有权限访问角色数据！"))
                .when(roleService).checkRoleDataScope(10L);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.grantRolesToUsers(Arrays.asList(2L), Arrays.asList(10L)));
        assertTrue(e.getMessage().contains("没有权限访问角色数据"));
        verify(userRoleMapper, never()).batchUserRole(anyList());
    }

    @Test
    void dataScopeCheckedForEveryTargetBeforeWrites()
    {
        when(userMapper.selectUserById(2L)).thenReturn(pcAdmin(2));
        when(userMapper.selectUserById(3L)).thenReturn(pcAdmin(3));
        when(roleMapper.selectRoleById(10L)).thenReturn(role(10L, "operator", "0"));
        when(userRoleMapper.selectUserRolesByUserIds(Arrays.asList(2L, 3L))).thenReturn(Collections.emptyList());

        service.grantRolesToUsers(Arrays.asList(2L, 3L), Arrays.asList(10L));

        // 逐个目标账号与角色都经过数据范围闸门
        verify(userService).checkUserDataScope(2L);
        verify(userService).checkUserDataScope(3L);
        verify(roleService).checkRoleDataScope(10L);
    }

    @Test
    void successComputesMissingPairsOnly()
    {
        when(userMapper.selectUserById(2L)).thenReturn(pcAdmin(2));
        when(userMapper.selectUserById(3L)).thenReturn(pcAdmin(3));
        when(roleMapper.selectRoleById(10L)).thenReturn(role(10L, "operator", "0"));

        SysUserRole owned = pair(2L, 10L);
        when(userRoleMapper.selectUserRolesByUserIds(Arrays.asList(2L, 3L)))
                .thenReturn(List.of(owned));

        Map<String, Object> result = service.grantRolesToUsers(Arrays.asList(2L, 3L), Arrays.asList(10L));
        assertEquals(1, ((Number) result.get("grantedCount")).longValue(), "仅 3-10 缺失");
        verify(userRoleMapper).batchUserRole(Mockito.argThat(list -> {
            SysUserRole only = ((List<?>) list).isEmpty() ? null : (SysUserRole) ((List<?>) list).get(0);
            return only != null && only.getUserId().equals(3L) && only.getRoleId().equals(10L);
        }));
    }

    // ---------------- 入参防御 ----------------

    @Test
    void emptyOrOversizedInputRejected()
    {
        assertThrows(ServiceException.class, () -> service.grantRolesToUsers(Collections.emptyList(), Arrays.asList(1L)));
        assertThrows(ServiceException.class, () -> service.grantRolesToUsers(Arrays.asList(2L), Collections.emptyList()));
        Long[] tooManyUsers = new Long[101];
        Arrays.fill(tooManyUsers, 2L);
        assertThrows(ServiceException.class,
                () -> service.grantRolesToUsers(Arrays.asList(tooManyUsers), Arrays.asList(10L)));
    }

    private static SysUserRole pair(long userId, long roleId)
    {
        SysUserRole p = new SysUserRole();
        p.setUserId(userId);
        p.setRoleId(roleId);
        return p;
    }
}
