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

    /**
     * 统计配额账户数
     */
    int countAccounts();

    /**
     * 乐观锁扣减次数（可用不足或版本不符返回0）
     */
    int updateConsume(@Param("userId") Long userId, @Param("amount") java.math.BigDecimal amount, @Param("version") Long version);

    /**
     * 乐观锁回补次数（失败补偿）
     */
    int updateRefund(@Param("userId") Long userId, @Param("amount") java.math.BigDecimal amount, @Param("version") Long version);

    /**
     * 乐观锁增加次数（积分兑换等获得）
     */
    int updateEarn(@Param("userId") Long userId, @Param("amount") java.math.BigDecimal amount, @Param("version") Long version);
}
