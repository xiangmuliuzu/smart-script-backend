package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.SysSettlement;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 结算管理Mapper接口
 */
public interface SysSettlementMapper {

    /**
     * 查询结算列表
     */
    List<Map<String, Object>> selectSettlementList(@Param("status") String status,
                                                    @Param("startDate") String startDate,
                                                    @Param("endDate") String endDate,
                                                    @Param("keyword") String keyword,
                                                    @Param("offset") int offset,
                                                    @Param("limit") int limit);

    /**
     * 统计结算数量
     */
    long countSettlement(@Param("status") String status,
                        @Param("startDate") String startDate,
                        @Param("endDate") String endDate,
                        @Param("keyword") String keyword);

    /**
     * 根据ID查询结算详情
     */
    Map<String, Object> selectSettlementById(@Param("settlementId") Long settlementId);

    /**
     * 查询结算关联的订单明细
     */
    List<Map<String, Object>> selectSettlementDetails(@Param("settlementId") Long settlementId);

    /**
     * 插入结算单
     */
    int insertSettlement(SysSettlement settlement);

    /**
     * 更新结算状态
     */
    int updateSettlementStatus(@Param("settlementId") Long settlementId,
                              @Param("status") String status,
                              @Param("settlementTime") String settlementTime,
                              @Param("updateBy") String updateBy);

    /**
     * 标记结算单为异常
     */
    int updateSettlementToAbnormal(@Param("settlementId") Long settlementId,
                                  @Param("abnormalReason") String abnormalReason,
                                  @Param("updateBy") String updateBy);

    /**
     * 更新异常处理
     */
    int updateAbnormalHandle(@Param("settlementId") Long settlementId,
                            @Param("handleRemark") String handleRemark,
                            @Param("updateBy") String updateBy);

    /**
     * 计算作者结算数据
     */
    List<Map<String, Object>> calculateAuthorSettlements(@Param("periodStart") String periodStart,
                                                         @Param("periodEnd") String periodEnd);
}
