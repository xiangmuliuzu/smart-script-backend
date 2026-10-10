package com.smartscript.platform.review.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.smartscript.platform.review.domain.OperationLog;
import com.smartscript.platform.review.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作日志Controller（审计查询）
 * GET /api/v1/admin/operation/logs?module=ai_quota&operation=consume&operatorId=9093
 *
 * @author smartscript
 */
@Anonymous
@RestController
@RequestMapping("/api/v1/admin/operation")
public class OperationLogController {

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping("/logs")
    public Map<String, Object> logs(OperationLog query) {
        Map<String, Object> result = new HashMap<>();
        List<OperationLog> list = operationLogService.selectOperationLogList(query);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("total", list.size());
        result.put("rows", list);
        return result;
    }
}
