package com.smartscript.platform.content.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.smartscript.platform.content.domain.SysCopyrightSeal;
import com.smartscript.platform.content.domain.SysCopyrightSealLog;
import com.smartscript.platform.content.mapper.SysCopyrightSealMapper;
import com.smartscript.platform.content.service.ISysCopyrightSealService;

/** 印章审核与状态变更，使用条件更新避免并发越过状态机。 */
@Service
public class SysCopyrightSealServiceImpl implements ISysCopyrightSealService
{
    private static final int REASON_MAX_LENGTH = 500;

    @Autowired
    private SysCopyrightSealMapper sealMapper;

    @Override
    public List<SysCopyrightSeal> selectSealList(String reviewStatus, String sealStatus, String keyword)
    {
        return sealMapper.selectSealList(reviewStatus, sealStatus, keyword);
    }

    @Override
    public SysCopyrightSeal selectSealById(Long sealId)
    {
        return sealMapper.selectSealById(sealId);
    }

    @Override
    public List<SysCopyrightSealLog> selectSealLogs(Long sealId)
    {
        return sealMapper.selectSealLogs(sealId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reviewSeal(Long sealId, String action, String reason, Long operatorId, String operatorName)
    {
        boolean approve = "approve".equals(action);
        if (!approve && !"reject".equals(action))
        {
            return false;
        }
        String trimmedReason = reason == null ? null : reason.trim();
        if ((!approve && !StringUtils.hasText(trimmedReason))
                || (trimmedReason != null && trimmedReason.length() > REASON_MAX_LENGTH))
        {
            return false;
        }

        String targetStatus = approve ? "approved" : "rejected";
        int updated = sealMapper.updateReview(sealId, targetStatus, approve ? null : trimmedReason,
                operatorId, operatorName);
        if (updated == 0)
        {
            return false;
        }
        insertLog(sealId, approve ? "review_approve" : "review_reject", "pending", targetStatus,
                approve ? null : trimmedReason, operatorId, operatorName);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSealStatus(Long sealId, String targetStatus, Long operatorId, String operatorName)
    {
        boolean enable = "enabled".equals(targetStatus);
        if (!enable && !"disabled".equals(targetStatus))
        {
            return false;
        }
        String expectedStatus = enable ? "disabled" : "enabled";
        int updated = sealMapper.updateSealStatus(sealId, expectedStatus, targetStatus, operatorName);
        if (updated == 0)
        {
            return false;
        }
        insertLog(sealId, enable ? "enable" : "disable", expectedStatus, targetStatus, null,
                operatorId, operatorName);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resolveAbnormalSeal(Long sealId, String reason, Long operatorId, String operatorName)
    {
        String trimmedReason = reason == null ? null : reason.trim();
        if (!StringUtils.hasText(trimmedReason) || trimmedReason.length() > REASON_MAX_LENGTH)
        {
            return false;
        }
        int updated = sealMapper.resolveAbnormalSeal(sealId, trimmedReason, operatorName);
        if (updated == 0)
        {
            return false;
        }
        insertLog(sealId, "resolve_abnormal", "abnormal", "disabled", trimmedReason,
                operatorId, operatorName);
        return true;
    }

    private void insertLog(Long sealId, String operationType, String fromStatus, String toStatus,
            String reason, Long operatorId, String operatorName)
    {
        SysCopyrightSealLog log = new SysCopyrightSealLog();
        log.setSealId(sealId);
        log.setOperationType(operationType);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setReason(reason);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setCreateTime(new Date());
        sealMapper.insertSealLog(log);
    }
}
