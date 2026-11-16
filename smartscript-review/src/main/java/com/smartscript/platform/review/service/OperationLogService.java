package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.OperationLog;
import com.smartscript.platform.review.mapper.OperationLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 操作日志Service（sys_operation_log 埋点）
 * 在 AI次数消耗/补偿/兑换、审核流转等关键操作埋点，便于审计与复盘。
 *
 * @author smartscript
 */
@Service
public class OperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    /**
     * 记录操作日志（失败不影响主业务）
     */
    public void record(String module, String operation, String targetType, String targetId,
                       String targetName, Long operatorId, String operatorName, String status, String remark) {
        try {
            OperationLog log = new OperationLog();
            log.setModule(module);
            log.setOperation(operation);
            log.setTargetType(targetType);
            log.setTargetId(targetId);
            log.setTargetName(targetName);
            log.setOperatorId(operatorId == null ? 0L : operatorId);
            log.setOperatorName(operatorName == null ? "system" : operatorName);
            log.setStatus(status == null ? "success" : status);
            log.setRemark(remark);
            operationLogMapper.insertOperationLog(log);
        } catch (Exception e) {
            // 埋点失败不阻断业务
        }
    }

    /**
     * 查询操作日志列表
     */
    public List<OperationLog> selectOperationLogList(OperationLog query) {
        return operationLogMapper.selectOperationLogList(query);
    }
}
