package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.AiQuotaAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * AI配额账户Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface AiQuotaAccountMapper {

    /**
     * 查询AI配额账户列表
     */
    List<AiQuotaAccount> selectAiQuotaAccountList(AiQuotaAccount aiQuotaAccount);

    /**
     * 查询AI配额账户详情
     */
    AiQuotaAccount selectAiQuotaAccountById(@Param("accountId") Long accountId);

    /**
     * 根据用户ID查询AI配额账户
     */
    AiQuotaAccount selectAiQuotaAccountByUserId(@Param("userId") Long userId);

    /**
     * 新增AI配额账户
     */
    int insertAiQuotaAccount(AiQuotaAccount aiQuotaAccount);

    /**
     * 修改AI配额账户
     */
    int updateAiQuotaAccount(AiQuotaAccount aiQuotaAccount);

    /**
     * 删除AI配额账户
     */
    int deleteAiQuotaAccountById(@Param("accountId") Long accountId);
}
