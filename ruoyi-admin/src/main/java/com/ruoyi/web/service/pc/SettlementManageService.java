package com.ruoyi.web.service.pc;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.SysSettlement;
import com.ruoyi.system.mapper.SysSettlementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SettlementManageService {

    @Autowired
    private SysSettlementMapper settlementMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<Map<String, Object>> list(String status, String startDate, String endDate, String keyword, int page, int size) {
        int offset = (page - 1) * size;
        return settlementMapper.selectSettlementList(status, startDate, endDate, keyword, offset, size);
    }

    public long count(String status, String startDate, String endDate, String keyword) {
        return settlementMapper.countSettlement(status, startDate, endDate, keyword);
    }

    public Map<String, Object> get(Long settlementId) {
        if (settlementId == null || settlementId <= 0) {
            throw new IllegalArgumentException("结算ID无效");
        }
        
        Map<String, Object> detail = settlementMapper.selectSettlementById(settlementId);
        if (detail == null) {
            throw new IllegalArgumentException("结算单不存在");
        }
        
        List<Map<String, Object>> orders = settlementMapper.selectSettlementDetails(settlementId);
        detail.put("orders", orders);
        
        return detail;
    }

    @Transactional(rollbackFor = Exception.class)
    public void calculate(Map<String, Object> params) {
        String periodStart = (String) params.get("periodStart");
        String periodEnd = (String) params.get("periodEnd");
        
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("结算周期不能为空");
        }
        
        // 查询周期内已完成但未结算的订单，按作者分组统计
        List<Map<String, Object>> authorSettlements = settlementMapper.calculateAuthorSettlements(periodStart, periodEnd);
        
        if (authorSettlements == null || authorSettlements.isEmpty()) {
            throw new IllegalArgumentException("该周期内没有需要结算的订单");
        }
        
        String username = SecurityUtils.getUsername();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        int count = 0;
        
        try {
            Date startDate = sdf.parse(periodStart);
            Date endDate = sdf.parse(periodEnd);
            
            for (Map<String, Object> item : authorSettlements) {
                Long authorId = ((Number) item.get("authorId")).longValue();
                Integer orderCount = ((Number) item.get("orderCount")).intValue();
                BigDecimal totalAmount = (BigDecimal) item.get("totalAmount");
                BigDecimal authorAmount = (BigDecimal) item.get("authorAmount");
                BigDecimal platformAmount = totalAmount.subtract(authorAmount);
                
                SysSettlement settlement = new SysSettlement();
                settlement.setSettlementNo(generateSettlementNo());
                settlement.setAuthorId(authorId);
                settlement.setPeriodStart(startDate);
                settlement.setPeriodEnd(endDate);
                settlement.setOrderCount(orderCount);
                settlement.setTotalAmount(totalAmount);
                settlement.setPlatformAmount(platformAmount);
                settlement.setAuthorAmount(authorAmount);
                settlement.setPlatformRatio(20);
                settlement.setStatus("pending");
                settlement.setCreateBy(username);
                
                settlementMapper.insertSettlement(settlement);
                count++;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("日期格式错误或生成结算单失败: " + e.getMessage());
        }
        
        if (count == 0) {
            throw new IllegalArgumentException("生成结算单失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleAbnormal(Long settlementId, String remark, Boolean markAsAbnormal) {
        if (settlementId == null || settlementId <= 0) {
            throw new IllegalArgumentException("结算ID无效");
        }
        if (remark == null || remark.trim().isEmpty()) {
            throw new IllegalArgumentException("处理说明不能为空");
        }
        
        String username = SecurityUtils.getUsername();
        
        if (markAsAbnormal != null && markAsAbnormal) {
            // 标记为异常：pending -> abnormal
            int rows = settlementMapper.updateSettlementToAbnormal(settlementId, remark, username);
            if (rows == 0) {
                throw new IllegalArgumentException("结算单不存在或状态不是待结算");
            }
        } else {
            // 处理异常：abnormal -> pending (恢复到待结算)
            int rows = settlementMapper.updateAbnormalHandle(settlementId, remark, username);
            if (rows == 0) {
                throw new IllegalArgumentException("结算单不存在或状态不是异常状态");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirmSettle(Long settlementId) {
        if (settlementId == null || settlementId <= 0) {
            throw new IllegalArgumentException("结算ID无效");
        }
        
        String username = SecurityUtils.getUsername();
        String settlementTime = LocalDateTime.now().format(FORMATTER);
        int rows = settlementMapper.updateSettlementStatus(settlementId, "settled", settlementTime, username);
        if (rows == 0) {
            throw new IllegalArgumentException("结算单不存在或状态异常");
        }
    }

    private String generateSettlementNo() {
        return "SET-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
}
