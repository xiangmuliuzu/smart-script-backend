package com.ruoyi.system.service;

import java.util.List;
import java.util.Map;

/**
 * PC 管理员账号批量授权服务接口（A 模块）。
 *
 * 给多个 PC 管理员账号（sys_user.user_type='00'）批量增量授予已有角色，
 * 复用若依 sys_user / sys_role / sys_user_role，不新建平行权限体系。
 */
public interface ISysUserBatchGrantService
{
    /**
     * 批量增量授予角色：对每个目标账号，只补插其尚未持有的角色关联。
     *
     * 全量校验前置：任一目标账号或角色不合规时整体拒绝（事务内不落任何行），
     * 不会出现部分成功却提示全部成功。重复提交幂等（已持有的关联跳过）。
     *
     * @param userIds 目标账号 ID 集合（1–100 个）
     * @param roleIds 角色 ID 集合（1–20 个）
     * @return grantedCount 本次实际新增关联数、skippedCount 已持有跳过数、
     *         userCount/accountCount 目标账号数、roleCount 角色数
     */
    Map<String, Object> grantRolesToUsers(List<Long> userIds, List<Long> roleIds);
}
