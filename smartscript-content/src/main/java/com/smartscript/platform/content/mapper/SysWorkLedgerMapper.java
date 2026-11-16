package com.smartscript.platform.content.mapper;

import com.smartscript.platform.content.domain.SysWorkLedger;

/**
 * 作品创作台账 数据层
 *
 * 依据：新建表 sys_work_ledger（B_20261110_001 迁移）。
 * 一作品一条，读写均以 work_id 为主键；写入使用 upsert，避免并发重复插入。
 *
 * @author smartscript
 */
public interface SysWorkLedgerMapper
{
    /**
     * 按作品ID查询台账
     *
     * @param workId 作品ID
     * @return 台账对象，不存在返回 null
     */
    public SysWorkLedger selectByWorkId(Long workId);

    /**
     * 新增或整条覆盖台账（work_id 唯一）
     *
     * @param ledger 台账对象（字段为 null 时落空串）
     * @return 影响行数
     */
    public int upsertLedger(SysWorkLedger ledger);
}
