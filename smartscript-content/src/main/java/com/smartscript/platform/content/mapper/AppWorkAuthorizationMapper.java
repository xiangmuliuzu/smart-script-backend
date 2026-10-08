package com.smartscript.platform.content.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 作品授权只读 数据层（App 书城「授权后放开全文」判定）。
 *
 * 依据：云端 script_platform_dev 库 sys_copyright_authorization 表（附件5.1）。
 *
 * 边界：该表属 D 模块（版权财务域）持有，本模块**只读**，不新增/修改授权记录；
 * 也不依赖 smartscript-copyright（D 模块尚未落地），仅按表结构直接取数。
 *
 * 命名说明：刻意不叫 SysCopyrightAuthorizationMapper —— MapperScanner 按类名注册
 * bean，D 模块后续落地同名 Mapper 会与本类冲突（参见 SysContentWorkMapper 的同类问题）。
 *
 * @author xiangsipeng
 */
public interface AppWorkAuthorizationMapper
{
    /**
     * 统计「当前生效」的授权记录条数
     *
     * 生效口径：status='active' 且 start_date<=今天 且 (end_date 为空 或 end_date>=今天)。
     * 不细分 license_type/license_scope：二者为 D 模块语义，本批只按「生效中的有效授权」
     * 放开全文，避免越界解释他人业务域字段。
     *
     * @param workId     作品ID
     * @param licenseeId 被授权方用户ID（App 侧为 sys_user.user_id）
     * @return 生效授权条数（0 表示未授权）
     */
    public int countEffectiveAuthorization(@Param("workId") Long workId, @Param("licenseeId") Long licenseeId);
}