package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.PointsAccount;
import com.smartscript.platform.review.domain.PointsRecord;
import com.smartscript.platform.review.service.PointsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分Controller（账户与流水查询）
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/admin/welfare/points")
public class PointsController {

    @Autowired
    private PointsService pointsService;

    /**
     * 查询积分账户列表
     */
    @GetMapping("/account/list")
    public Map<String, Object> accountList(PointsAccount pointsAccount) {
        Map<String, Object> result = new HashMap<>();
        List<PointsAccount> list = pointsService.selectPointsAccountList(pointsAccount);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 查询积分流水列表
     */
    @GetMapping("/record/list")
    public Map<String, Object> recordList(PointsRecord pointsRecord) {
        Map<String, Object> result = new HashMap<>();
        List<PointsRecord> list = pointsService.selectPointsRecordList(pointsRecord);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }
}
