package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.AiQuotaAccount;
import com.smartscript.platform.review.domain.AiQuotaRecord;
import com.smartscript.platform.review.mapper.AiQuotaAccountMapper;
import com.smartscript.platform.review.mapper.AiQuotaRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * AI配额Service业务层处理
 *
 * @author smartscript
 */
@Service
public class AiQuotaService {

    @Autowired
    private AiQuotaAccountMapper aiQuotaAccountMapper;

    @Autowired
    private AiQuotaRecordMapper aiQuotaRecordMapper;

    /**
     * 查询AI配额账户列表
     */
    public List<AiQuotaAccount> selectAiQuotaAccountList(AiQuotaAccount aiQuotaAccount) {
        return aiQuotaAccountMapper.selectAiQuotaAccountList(aiQuotaAccount);
    }

    /**
     * 查询AI配额账户详情
     */
    public AiQuotaAccount selectAiQuotaAccountById(Long accountId) {
        return aiQuotaAccountMapper.selectAiQuotaAccountById(accountId);
    }

    /**
     * 新增AI配额账户
     */
    public int insertAiQuotaAccount(AiQuotaAccount aiQuotaAccount) {
        return aiQuotaAccountMapper.insertAiQuotaAccount(aiQuotaAccount);
    }

    /**
     * 修改AI配额账户
     */
    public int updateAiQuotaAccount(AiQuotaAccount aiQuotaAccount) {
        return aiQuotaAccountMapper.updateAiQuotaAccount(aiQuotaAccount);
    }

    /**
     * 删除AI配额账户
     */
    public int deleteAiQuotaAccountById(Long accountId) {
        return aiQuotaAccountMapper.deleteAiQuotaAccountById(accountId);
    }

    /**
     * 查询AI配额记录列表
     */
    public List<AiQuotaRecord> selectAiQuotaRecordList(AiQuotaRecord aiQuotaRecord) {
        return aiQuotaRecordMapper.selectAiQuotaRecordList(aiQuotaRecord);
    }

    /**
     * 新增AI配额记录
     */
    public int insertAiQuotaRecord(AiQuotaRecord aiQuotaRecord) {
        return aiQuotaRecordMapper.insertAiQuotaRecord(aiQuotaRecord);
    }
}
