package com.smartscript.platform.content.service;

import com.smartscript.platform.content.domain.SysWorkLedger;

/**
 * 作品创作台账 服务接口
 *
 * 台账跨端共享（PC 用户端 / App），读写接口只做存取与归属校验，
 * 不在此处解析七类文本的具体格式。
 *
 * @author smartscript
 */
public interface IAppWorkLedgerService
{
    /**
     * 查询作品台账（不存在时返回仅含 workId 的空对象，便于前端直接绑定）
     *
     * @param workId 作品ID
     * @return 台账对象
     */
    public SysWorkLedger getLedger(Long workId);

    /**
     * 整条覆盖保存作品台账
     *
     * @param workId 作品ID
     * @param ledger 台账内容（null 字段按空串落库）
     * @return 是否保存成功
     */
    public boolean saveLedger(Long workId, SysWorkLedger ledger);
}
