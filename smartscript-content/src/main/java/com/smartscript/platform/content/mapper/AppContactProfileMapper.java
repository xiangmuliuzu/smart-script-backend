package com.smartscript.platform.content.mapper;

import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.dto.AppContactDto;

/**
 * 联系方式档案只读 数据层（App 书城「版权合作联系方式」查询）。
 *
 * 依据：云端 script_platform_dev 库 sys_contact_profile 表（附件5.1 表3-62）。
 *
 * 边界：只读，不新增/修改联系方式档案（写入属用户中心 2.5.7 接口的业务域）。
 * 只判定「是否登记」并下发展示范围，不读取明文密文内容（详见 {@link AppContactDto}）。
 *
 * 命名说明：刻意不叫 SysContactProfileMapper —— MapperScanner 按类名注册 bean，
 * 后续模块落地同表 Mapper 会与本类冲突（参见 SysContentWorkMapper 的同类问题）。
 *
 * @author xiangsipeng
 */
public interface AppContactProfileMapper
{
    /**
     * 按作者（档案归属人）查询联系方式档案
     *
     * sys_contact_profile.owner_id 为唯一键，一个用户至多一条记录。
     *
     * @param ownerId 档案归属人用户ID（App 侧取 sys_work.author_id）
     * @return 联系方式摘要（无档案时返回 null）
     */
    public AppContactDto selectContactByOwnerId(@Param("ownerId") Long ownerId);
}