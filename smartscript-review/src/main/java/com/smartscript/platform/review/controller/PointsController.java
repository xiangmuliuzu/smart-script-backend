package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.PointsAccount;
import com.smartscript.platform.review.domain.PointsRecord;
import com.smartscript.platform.review.service.PointsService;
import com.smartscript.platform.review.util.AdminListPager;
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
     *
     * <p>page / pageSize 由前端 el-pagination 传入，必须这里显式接收：
     * 挂到实体上是绑不上的，只会被静默丢弃，导致每页返回全表。</p>
     */
    @GetMapping("/account/list")
    public Map<String, Object> accountList(PointsAccount pointsAccount,
                                           @RequestParam(value = "page", required = false) Integer page,
                                           @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        Map<String, Object> result = new HashMap<>();
        List<PointsAccount> list = AdminListPager.query(page, pageSize,
                () -> pointsService.selectPointsAccountList(pointsAccount));
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", AdminListPager.total(list));
        return result;
    }

    /**
     * 修改指定用户的积分余额
     */
    @PutMapping("/account/balance")
    public Map<String, Object> updateBalance(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        Object uid = body.get("userId");
        Object bal = body.get("balance");
        if (uid == null || bal == null) {
            result.put("code", 400);
            result.put("msg", "userId与balance不能为空");
            return result;
        }
        try {
            Long userId = Long.valueOf(String.valueOf(uid));
            int balance = Integer.parseInt(String.valueOf(bal));
            String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
            int after = pointsService.adjustBalance(userId, balance, remark);
            result.put("code", 200);
            result.put("msg", "操作成功");
            result.put("data", after);
        } catch (NumberFormatException e) {
            result.put("code", 400);
            result.put("msg", "userId或balance格式不正确");
        } catch (IllegalArgumentException | IllegalStateException e) {
            result.put("code", 400);
            result.put("msg", e.getMessage());
        }
        return result;
    }

    /**
     * 查询积分流水列表
     *
     * <p>分页口径同 {@link #accountList}：前端传 page / pageSize，这里必须显式接住。</p>
     */
    @GetMapping("/record/list")
    public Map<String, Object> recordList(PointsRecord pointsRecord,
                                          @RequestParam(value = "page", required = false) Integer page,
                                          @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        Map<String, Object> result = new HashMap<>();
        List<PointsRecord> list = AdminListPager.query(page, pageSize,
                () -> pointsService.selectPointsRecordList(pointsRecord));
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", AdminListPager.total(list));
        return result;
    }
}
