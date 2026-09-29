package com.ruoyi.web.controller.system;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.ISysUserBatchGrantService;

/**
 * A 模块：PC 管理员账号批量授权。
 *
 * 给多个 PC 管理员账号（user_type=00）批量增量授予已有角色。
 * 复用若依 sys_user / sys_role / sys_user_role 与原生权限标识
 * system:user:edit（与原生角色分配同权限），不新建平行权限体系；
 * 目标账号域、受保护账号与受保护角色的校验在服务层强制。
 * 写操作经 @Log 记入若依操作日志（记录动作与结果，不含密码/令牌）。
 */
@RestController
@RequestMapping("/system/user")
public class SysUserBatchGrantController extends BaseController
{
    @Autowired
    private ISysUserBatchGrantService batchGrantService;

    /**
     * 批量授权角色
     *
     * @param body userIds 目标管理员账号 ID 列表；roleIds 待授予角色 ID 列表
     * @return AjaxResult.data：userCount/roleCount/grantedCount/skippedCount
     */
    @PreAuthorize("@ss.hasPermi('system:user:edit')")
    @Log(title = "用户管理", businessType = BusinessType.GRANT)
    @PutMapping("/batchGrantRoles")
    public AjaxResult batchGrantRoles(@RequestBody BatchGrantRolesBody body)
    {
        if (body == null || body.getUserIds() == null || body.getRoleIds() == null)
        {
            return error("目标账号与角色不能为空");
        }
        Map<String, Object> result = batchGrantService.grantRolesToUsers(body.getUserIds(), body.getRoleIds());
        long granted = result.get("grantedCount") instanceof Number n ? n.longValue() : 0;
        long skipped = result.get("skippedCount") instanceof Number n ? n.longValue() : 0;
        AjaxResult ajax = AjaxResult.success(result);
        ajax.put("msg", StringUtils.format("授权完成：新增 {} 条授权，{} 条已持有跳过", granted, skipped));
        return ajax;
    }

    /**
     * 批量授权请求体。只含账号与角色 ID 列表；不含数据范围、备注等非白名单字段。
     */
    public static class BatchGrantRolesBody
    {
        private List<Long> userIds;
        private List<Long> roleIds;

        public List<Long> getUserIds()
        {
            return userIds;
        }

        public void setUserIds(List<Long> userIds)
        {
            this.userIds = userIds;
        }

        public List<Long> getRoleIds()
        {
            return roleIds;
        }

        public void setRoleIds(List<Long> roleIds)
        {
            this.roleIds = roleIds;
        }
    }
}
