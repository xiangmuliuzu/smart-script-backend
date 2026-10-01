package com.smartscript.platform.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * A6 统一身份：角色、权限与作者能力解析（契约 A6-IDENTITY-CONTRACT-v1 §1.3）。
 *
 * 强制边界：
 *   - 角色与权限一律排除超管角色 `admin` 与停用/删除角色；
 *   - 权限只取启用的菜单，`perms` 为空的行不参与（避免下游拿到空串做误判）；
 *   - 作者能力只读取 enabled 标记，不暴露操作人与原因（那是 A4 管理端的事）。
 */
public interface AppIdentityMapper
{
    /**
     * 角色编码集合（用于授权判定）。
     *
     * 排除超管角色：App 与 PC 共用 sys_user_role，越权暴露管理端角色必须在 SQL 层杜绝。
     */
    List<String> selectRoleCodes(@Param("userId") Long userId);

    /**
     * 权限标识集合（去重、去空）。
     *
     * 来源 sys_role_menu + sys_menu.perms，只取未停用菜单与未停用/未删除角色。
     */
    List<String> selectPermissionCodes(@Param("userId") Long userId);

    /** 作者能力是否开启；无记录返回 null（由服务层归一为 false）。 */
    Boolean selectAuthorCapabilityEnabled(@Param("userId") Long userId);
}
