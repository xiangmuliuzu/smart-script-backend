package com.smartscript.platform.review.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.smartscript.platform.review.service.AiQuotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI次数业务Controller（App端调用：消耗/补偿/余额）
 * POST /api/v1/ai/quota/consume {"userId":106,"capability":"writing","quotaCost":1,"workId":32,"idempotencyKey":"req_xxx"}
 * POST /api/v1/ai/quota/refund  {"userId":106,"amount":1,"workId":32,"idempotencyKey":"req_xxx"}
 * GET  /api/v1/ai/quota/balance?userId=106
 *
 * @author smartscript
 */
@Anonymous
@RestController
@RequestMapping("/api/v1/ai/quota")
public class QuotaBusinessController {

    @Autowired
    private AiQuotaService aiQuotaService;

    /**
     * AI次数消耗（预扣，幂等防重复扣减）
     */
    @PostMapping("/consume")
    public Map<String, Object> consume(@RequestBody Map<String, Object> params) {
        try {
            Long userId = params.get("userId") != null ? Long.valueOf(params.get("userId").toString()) : null;
            String capability = params.get("capability") != null ? params.get("capability").toString() : null;
            Integer quotaCost = params.get("quotaCost") != null ? Integer.valueOf(params.get("quotaCost").toString()) : null;
            Long workId = params.get("workId") != null ? Long.valueOf(params.get("workId").toString()) : null;
            String idempotencyKey = params.get("idempotencyKey") != null ? params.get("idempotencyKey").toString() : null;
            return aiQuotaService.consume(userId, capability, quotaCost, workId, idempotencyKey);
        } catch (IllegalStateException e) {
            Map<String, Object> r = new HashMap<>();
            r.put("code", 400);
            r.put("msg", e.getMessage());
            return r;
        } catch (Exception e) {
            Map<String, Object> r = new HashMap<>();
            r.put("code", 500);
            r.put("msg", "AI次数扣减失败: " + e.getMessage());
            return r;
        }
    }

    /**
     * AI次数失败补偿（幂等防重复补偿）
     */
    @PostMapping("/refund")
    public Map<String, Object> refund(@RequestBody Map<String, Object> params) {
        try {
            Long userId = params.get("userId") != null ? Long.valueOf(params.get("userId").toString()) : null;
            Integer amount = params.get("amount") != null ? Integer.valueOf(params.get("amount").toString()) : null;
            Long workId = params.get("workId") != null ? Long.valueOf(params.get("workId").toString()) : null;
            String idempotencyKey = params.get("idempotencyKey") != null ? params.get("idempotencyKey").toString() : null;
            return aiQuotaService.refund(userId, amount, workId, idempotencyKey);
        } catch (IllegalStateException e) {
            Map<String, Object> r = new HashMap<>();
            r.put("code", 400);
            r.put("msg", e.getMessage());
            return r;
        } catch (Exception e) {
            Map<String, Object> r = new HashMap<>();
            r.put("code", 500);
            r.put("msg", "AI次数补偿失败: " + e.getMessage());
            return r;
        }
    }

    /**
     * 查询AI次数余额与最近流水
     */
    @GetMapping("/balance")
    public Map<String, Object> balance(@RequestParam("userId") Long userId) {
        return aiQuotaService.balance(userId);
    }

    /**
     * 积分兑换AI次数（只能积分换次数，不能反向）
     * POST /api/v1/ai/quota/exchange {"userId":106,"points":20,"idempotencyKey":"ex_xxx"}
     */
    @PostMapping("/exchange")
    public Map<String, Object> exchange(@RequestBody Map<String, Object> params) {
        try {
            Long userId = params.get("userId") != null ? Long.valueOf(params.get("userId").toString()) : null;
            Integer points = params.get("points") != null ? Integer.valueOf(params.get("points").toString()) : null;
            String idempotencyKey = params.get("idempotencyKey") != null ? params.get("idempotencyKey").toString() : null;
            return aiQuotaService.exchange(userId, points, idempotencyKey);
        } catch (IllegalStateException e) {
            Map<String, Object> r = new HashMap<>();
            r.put("code", 400);
            r.put("msg", e.getMessage());
            return r;
        } catch (Exception e) {
            Map<String, Object> r = new HashMap<>();
            r.put("code", 500);
            r.put("msg", "积分兑换失败: " + e.getMessage());
            return r;
        }
    }
}
