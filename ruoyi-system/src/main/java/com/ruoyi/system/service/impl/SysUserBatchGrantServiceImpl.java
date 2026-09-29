package com.ruoyi.system.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.SysUserRole;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.mapper.SysRoleMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.mapper.SysUserRoleMapper;
import com.ruoyi.system.service.ISysUserBatchGrantService;

/**
 * PC 管理员账号批量授权（A 模块）。
 *
 * 复用若依原生用户/角色/关联体系。校验规则：
 *   1. 操作者权限由控制器 @PreAuthorize('system:user:edit') 把关；
 *      服务层再防御一次操作者账号域（非 00 拒绝）。
 *   2. 目标账号必须存在、未删除、未停用，且 user_type='00'；
 *      超级管理员（user_id=1）为受保护账号，不得作为目标。
 *   3. 数据范围与原生授权接口（/system/user/authRole）同闸门：
 *      逐个调用 checkUserDataScope(userId)、checkRoleDataScope(roleId)，
 *      受限数据权限的管理员不能给数据范围外的账号/角色授权。
 *   4. 角色必须存在、未删除、未停用；超级管理员角色（role_id=1 或
 *      role_key='admin'）为受保护角色，禁止经批量授权发放。
 *   4. 增量授予：已持有的关联跳过（重复提交幂等）；全量校验通过后
 *      才在同一事务内补插缺失关联，任一校验失败整体拒绝。
 */
@Service
public class SysUserBatchGrantServiceImpl implements ISysUserBatchGrantService
{
    private static final int USER_IDS_MAX = 100;

    private static final int ROLE_IDS_MAX = 20;

    /** 逻辑删除标记（sys_user.del_flag / sys_role.del_flag）。 */
    private static final String DEL_FLAG_DELETED = "2";

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    /** 数据范围校验复用原生实现（与 /system/user/authRole 同一闸门）。 */
    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysRoleService roleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> grantRolesToUsers(List<Long> userIds, List<Long> roleIds)
    {
        assertOperatorIsPcAdmin();

        List<Long> targetUserIds = dedupe(userIds, USER_IDS_MAX, "目标账号");
        List<Long> targetRoleIds = dedupe(roleIds, ROLE_IDS_MAX, "角色");

        Map<Long, SysUser> users = new LinkedHashMap<>();
        for (Long userId : targetUserIds)
        {
            // 数据范围：受限管理员对范围外账号直接拒绝（超管在原生实现内放行）
            userService.checkUserDataScope(userId);
            SysUser user = userMapper.selectUserById(userId);
            if (user == null || DEL_FLAG_DELETED.equals(user.getDelFlag()))
            {
                throw new ServiceException("用户不存在或已删除：userId=" + userId);
            }
            if (userId != null && userId == 1L)
            {
                // 受保护管理员：超级管理员不得经批量授权被改动
                throw new ServiceException("超级管理员账号不允许通过批量授权修改：userId=" + userId);
            }
            // 账号域边界：只接受明确的 user_type='00'；NULL/空串（历史脏数据）与
            // APP 用户（01/02/03）一律拒绝，APP 账号只能走 A4 的 /api/v1/admin 授权接口
            if (!UserConstants.USER_TYPE_PC_ADMIN.equals(user.getUserType()))
            {
                throw new ServiceException("目标账号不是 PC 管理员，禁止在此批量授权：userId=" + userId);
            }
            if (UserConstants.USER_DISABLE.equals(user.getStatus()))
            {
                throw new ServiceException("目标账号已停用：userId=" + userId);
            }
            users.put(userId, user);
        }

        Map<Long, SysRole> roles = new LinkedHashMap<>();
        for (Long roleId : targetRoleIds)
        {
            // 数据范围：逐个角色校验，与原生授权一致
            roleService.checkRoleDataScope(roleId);
            SysRole role = roleMapper.selectRoleById(roleId);
            if (role == null || DEL_FLAG_DELETED.equals(role.getDelFlag()))
            {
                throw new ServiceException("角色不存在或已删除：roleId=" + roleId);
            }
            if (roleId != null && roleId == 1L
                    || Constants.SUPER_ADMIN.equals(role.getRoleKey()))
            {
                // 受保护角色：超级管理员角色禁止经批量授权发放
                throw new ServiceException("超级管理员角色不允许通过批量授权发放：roleId=" + roleId);
            }
            if (UserConstants.ROLE_DISABLE.equals(role.getStatus()))
            {
                throw new ServiceException("角色已停用：roleId=" + roleId);
            }
            roles.put(roleId, role);
        }

        // 增量补插：查询现有关联，只插入缺失组合（重复提交幂等）
        List<SysUserRole> existing = userRoleMapper.selectUserRolesByUserIds(targetUserIds);
        List<SysUserRole> missing = new ArrayList<>();
        for (Long userId : targetUserIds)
        {
            for (Long roleId : targetRoleIds)
            {
                if (!contains(existing, userId, roleId))
                {
                    SysUserRole pair = new SysUserRole();
                    pair.setUserId(userId);
                    pair.setRoleId(roleId);
                    missing.add(pair);
                }
            }
        }
        if (!missing.isEmpty())
        {
            userRoleMapper.batchUserRole(missing);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userCount", users.size());
        result.put("roleCount", roles.size());
        result.put("grantedCount", missing.size());
        result.put("skippedCount", (long) users.size() * roles.size() - missing.size());
        return result;
    }

    /**
     * 操作者账号域防御：管理链请求的 principal 必须是 00 账号。
     * 无认证上下文（单元测试）时跳过；生产请求始终有 LoginUser principal。
     */
    private void assertOperatorIsPcAdmin()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser)
        {
            SysUser operator = loginUser.getUser();
            String userType = operator == null ? null : operator.getUserType();
            // 操作者同样只接受明确的 00：NULL/空串视为账号域不明，拒绝执行
            if (!UserConstants.USER_TYPE_PC_ADMIN.equals(userType))
            {
                throw new ServiceException("该账号类型不允许执行管理员批量授权");
            }
        }
    }

    private static List<Long> dedupe(List<Long> ids, int max, String label)
    {
        if (ids == null || ids.isEmpty())
        {
            throw new ServiceException(label + "不能为空");
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>(ids);
        if (distinct.contains(null))
        {
            throw new ServiceException(label + "ID 不能为空值");
        }
        if (distinct.size() > max)
        {
            throw new ServiceException(label + "数量不能超过 " + max);
        }
        return new ArrayList<>(distinct);
    }

    private static boolean contains(List<SysUserRole> pairs, Long userId, Long roleId)
    {
        for (SysUserRole pair : pairs)
        {
            if (pair.getUserId().equals(userId) && pair.getRoleId().equals(roleId))
            {
                return true;
            }
        }
        return false;
    }
}
