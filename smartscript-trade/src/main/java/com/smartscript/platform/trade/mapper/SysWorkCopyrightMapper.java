package com.smartscript.platform.trade.mapper;

/**
 * 作品确权状态只读查询（跨域表）。
 * <p>
 * sys_copyright_application 属 content 版权域，trade 不引入模块依赖，
 * 仅在同库上只读判定「作品是否已完成版权登记发证」，用于询盘/订单流程分流。
 */
public interface SysWorkCopyrightMapper
{
    /** 作品是否存在已发证（certificated）的版权登记记录：true=有版权. */
    boolean hasCertificatedCopyright(Long workId);
}
