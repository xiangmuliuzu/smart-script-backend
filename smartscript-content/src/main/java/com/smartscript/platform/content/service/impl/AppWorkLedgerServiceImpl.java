package com.smartscript.platform.content.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.content.domain.SysWorkLedger;
import com.smartscript.platform.content.mapper.SysWorkLedgerMapper;
import com.smartscript.platform.content.service.IAppWorkLedgerService;

/**
 * 作品创作台账 服务实现
 *
 * 依据：sys_work_ledger（B_20261110_001 迁移）。
 * 说明：一作品一条；保存为整条覆盖，未传字段落空串（前端按整表提交）。
 *
 * @author smartscript
 */
@Service
public class AppWorkLedgerServiceImpl implements IAppWorkLedgerService
{
    @Autowired
    private SysWorkLedgerMapper ledgerMapper;

    @Override
    public SysWorkLedger getLedger(Long workId)
    {
        SysWorkLedger ledger = ledgerMapper.selectByWorkId(workId);
        if (ledger == null)
        {
            ledger = new SysWorkLedger();
            ledger.setWorkId(workId);
        }
        return ledger;
    }

    @Override
    @Transactional
    public boolean saveLedger(Long workId, SysWorkLedger ledger)
    {
        if (workId == null || ledger == null)
        {
            return false;
        }
        ledger.setWorkId(workId);
        return ledgerMapper.upsertLedger(ledger) > 0;
    }
}
