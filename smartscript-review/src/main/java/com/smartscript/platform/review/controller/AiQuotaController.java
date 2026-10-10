package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.AiQuotaAccount;
import com.smartscript.platform.review.domain.AiQuotaRecord;
import com.smartscript.platform.review.service.AiQuotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI配额Controller
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/admin/ai/quota")
public class AiQuotaController {

    @Autowired
    private AiQuotaService aiQuotaService;

    /**
     * AI配额统计（用户数/今日调用/失败/补偿）
     */
    @GetMapping("/statistics")
    public Map<String, Object> statistics() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("totalUsers", aiQuotaService.countAccounts());
        result.put("todayCalls", aiQuotaService.countTodayCalls());
        result.put("failedCalls", aiQuotaService.countFailedCalls());
        result.put("refundedCalls", aiQuotaService.countRefundedCalls());
        return result;
    }

    /**
     * 查询AI配额账户列表
     */
    @GetMapping("/list")
    public Map<String, Object> list(AiQuotaAccount aiQuotaAccount) {
        Map<String, Object> result = new HashMap<>();
        List<AiQuotaAccount> list = aiQuotaService.selectAiQuotaAccountList(aiQuotaAccount);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 查询AI配额账户详情
     */
    @GetMapping("/detail/{accountId}")
    public Map<String, Object> detail(@PathVariable("accountId") Long accountId) {
        Map<String, Object> result = new HashMap<>();
        AiQuotaAccount aiQuotaAccount = aiQuotaService.selectAiQuotaAccountById(accountId);
        if (aiQuotaAccount == null) {
            result.put("code", 404);
            result.put("msg", "未找到该账户");
        } else {
            result.put("code", 200);
            result.put("msg", "操作成功");
            result.put("data", aiQuotaAccount);
        }
        return result;
    }

    /**
     * 新增AI配额账户
     */
    @PostMapping
    public Map<String, Object> add(@RequestBody AiQuotaAccount aiQuotaAccount) {
        Map<String, Object> result = new HashMap<>();
        aiQuotaService.insertAiQuotaAccount(aiQuotaAccount);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 修改AI配额账户
     */
    @PutMapping
    public Map<String, Object> edit(@RequestBody AiQuotaAccount aiQuotaAccount) {
        Map<String, Object> result = new HashMap<>();
        aiQuotaService.updateAiQuotaAccount(aiQuotaAccount);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 删除AI配额账户
     */
    @DeleteMapping("/{accountId}")
    public Map<String, Object> remove(@PathVariable("accountId") Long accountId) {
        Map<String, Object> result = new HashMap<>();
        aiQuotaService.deleteAiQuotaAccountById(accountId);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 查询AI配额记录列表
     */
    @GetMapping("/record/list")
    public Map<String, Object> recordList(AiQuotaRecord aiQuotaRecord) {
        Map<String, Object> result = new HashMap<>();
        List<AiQuotaRecord> list = aiQuotaService.selectAiQuotaRecordList(aiQuotaRecord);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }
}
