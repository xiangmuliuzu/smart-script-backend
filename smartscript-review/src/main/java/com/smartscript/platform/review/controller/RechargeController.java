package com.smartscript.platform.review.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.smartscript.platform.review.domain.RechargeOrder;
import com.smartscript.platform.review.service.PointsService;
import com.smartscript.platform.review.service.RechargeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 积分充值接口（App / PC 用户端共用）。
 *
 * 当前为「模拟支付壳子」：下单后调用 mock-pay 即视为支付成功并立即到账积分；
 * 真实微信/支付宝下单与回调由支付组替换 mock-pay（订单表与入账逻辑已就绪）。
 *
 * 接口清单：
 *   GET  /api/v1/welfare/recharge/config?userId=  → 换算比例/金额范围/固定档位/支付渠道/当前余额
 *   POST /api/v1/welfare/recharge/order          → 创建充值订单（待支付）
 *   POST /api/v1/welfare/recharge/mock-pay       → 模拟支付成功（幂等，积分到账）
 *   GET  /api/v1/welfare/recharge/orders?userId= → 充值记录
 *
 * @author smartscript
 */
@Anonymous
@RestController
@RequestMapping("/api/v1/welfare/recharge")
public class RechargeController {

    @Autowired
    private RechargeService rechargeService;

    @Autowired
    private PointsService pointsService;

    /** 统一信封：{code, msg, data} */
    private Map<String, Object> ok(Map<String, Object> data) {
        Map<String, Object> env = new HashMap<>();
        env.put("code", 200);
        env.put("msg", "success");
        env.put("data", data);
        return env;
    }

    private Map<String, Object> fail(int code, String msg) {
        Map<String, Object> env = new HashMap<>();
        env.put("code", code);
        env.put("msg", msg);
        env.put("data", new HashMap<>());
        return env;
    }

    private Long longOf(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intOf(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String fmt(Date d) {
        if (d == null) return null;
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /**
     * 充值页配置。userId 可选：传入时同时返回当前积分余额。
     */
    @GetMapping("/config")
    public Map<String, Object> config(@RequestParam(value = "userId", required = false) Long userId) {
        Map<String, Object> data = rechargeService.config();
        if (userId != null && userId > 0) {
            data.put("userId", userId);
            data.put("balance", pointsService.currentBalance(userId));
        }
        return ok(data);
    }

    /**
     * 创建充值订单。body: {userId, yuan, channel(WECHAT|ALIPAY)}
     */
    @PostMapping("/order")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> body) {
        Long userId = longOf(body.get("userId"));
        Integer yuan = intOf(body.get("yuan"));
        String channel = body.get("channel") == null ? "" : body.get("channel").toString();
        if (yuan == null) return fail(400, "充值金额必填");
        try {
            RechargeOrder order = rechargeService.createOrder(userId, yuan, channel);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderId", order.getOrderId());
            data.put("orderNo", order.getOrderNo());
            data.put("amountFen", order.getAmountFen());
            data.put("points", order.getPoints());
            data.put("channel", order.getChannel());
            data.put("status", order.getStatus());
            data.put("balance", pointsService.currentBalance(userId));
            return ok(data);
        } catch (IllegalArgumentException e) {
            return fail(400, e.getMessage());
        }
    }

    /**
     * 模拟支付成功。body: {userId, orderNo}
     */
    @PostMapping("/mock-pay")
    public Map<String, Object> mockPay(@RequestBody Map<String, Object> body) {
        Long userId = longOf(body.get("userId"));
        String orderNo = body.get("orderNo") == null ? "" : body.get("orderNo").toString();
        try {
            return ok(rechargeService.mockPay(userId, orderNo));
        } catch (IllegalArgumentException e) {
            return fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return fail(400, e.getMessage());
        }
    }

    /**
     * 充值记录（按时间倒序）。
     */
    @GetMapping("/orders")
    public Map<String, Object> orders(@RequestParam("userId") Long userId,
                                      @RequestParam(value = "limit", defaultValue = "20") int limit) {
        if (userId == null) return fail(400, "userId 必填");
        List<Map<String, Object>> list = new ArrayList<>();
        for (RechargeOrder order : rechargeService.orders(userId, limit)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderId", order.getOrderId());
            m.put("orderNo", order.getOrderNo());
            m.put("amountFen", order.getAmountFen());
            m.put("points", order.getPoints());
            m.put("channel", order.getChannel());
            m.put("status", order.getStatus());
            m.put("payTime", fmt(order.getPayTime()));
            m.put("createTime", fmt(order.getCreatedAt()));
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("orders", list);
        return ok(data);
    }
}
