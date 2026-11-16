package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.RechargeOrder;
import com.smartscript.platform.review.mapper.RechargeOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 积分充值 Service（模拟支付壳子）。
 *
 * 换算口径：1 元 = recharge.points-per-yuan 积分（默认 100，改配置即可调整）。
 * 固定档位 6/30/68/128/328/648 元，另支持自定义金额（必须为 1 元的整数倍）。
 *
 * 真实支付接入方式（交给支付组）：
 *   1) 下单接口已生成订单（status=PENDING）并可返回 orderNo 作为商户订单号；
 *   2) 微信/支付宝异步回调验签后，把订单置 PAID 并调用本类 {@link #creditOrder} 入账即可；
 *   3) 幂等由 markPaid 的条件更新（仅 PENDING → PAID 生效）保证，重复回调不会重复加积分。
 *
 * @author smartscript
 */
@Service
public class RechargeService {

    /** 固定档位（元） */
    private static final int[] PLAN_YUAN = {6, 30, 68, 128, 328, 648};

    private static final String CHANNEL_WECHAT = "WECHAT";
    private static final String CHANNEL_ALIPAY = "ALIPAY";

    @Autowired
    private RechargeOrderMapper rechargeOrderMapper;

    @Autowired
    private PointsService pointsService;

    @Value("${recharge.points-per-yuan:100}")
    private int pointsPerYuan;

    @Value("${recharge.min-yuan:1}")
    private int minYuan;

    @Value("${recharge.max-yuan:100000}")
    private int maxYuan;

    public int getPointsPerYuan() {
        return pointsPerYuan <= 0 ? 100 : pointsPerYuan;
    }

    public int getMinYuan() {
        return minYuan <= 0 ? 1 : minYuan;
    }

    public int getMaxYuan() {
        return maxYuan <= 0 ? 100000 : maxYuan;
    }

    /** 充值页配置：换算比例、金额范围、固定档位、支付渠道。 */
    public Map<String, Object> config() {
        int rate = getPointsPerYuan();
        List<Map<String, Object>> plans = new ArrayList<>();
        for (int yuan : PLAN_YUAN) {
            plans.add(planOf(yuan, rate));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pointsPerYuan", rate);
        data.put("minYuan", getMinYuan());
        data.put("maxYuan", getMaxYuan());
        data.put("plans", plans);
        data.put("channels", channels());
        return data;
    }

    private Map<String, Object> planOf(int yuan, int rate) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("yuan", yuan);
        m.put("amountFen", yuan * 100);
        m.put("points", yuan * rate);
        return m;
    }

    private List<Map<String, Object>> channels() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(channelOf(CHANNEL_WECHAT, "微信支付"));
        list.add(channelOf(CHANNEL_ALIPAY, "支付宝"));
        return list;
    }

    private Map<String, Object> channelOf(String code, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        // 真实微信/支付宝对接由支付组完成；壳子阶段两个渠道都展示为可用，走模拟支付。
        m.put("enabled", true);
        return m;
    }

    /** 渠道中文名（写流水备注用）。 */
    private String channelName(String channel) {
        if (CHANNEL_ALIPAY.equals(channel)) return "支付宝";
        return "微信支付";
    }

    private String normalizeChannel(String channel) {
        String value = channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
        if (CHANNEL_WECHAT.equals(value) || CHANNEL_ALIPAY.equals(value)) {
            return value;
        }
        throw new IllegalArgumentException("不支持的支付方式");
    }

    /**
     * 创建充值订单（待支付）。
     */
    public RechargeOrder createOrder(Long userId, int yuan, String channel) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 必填");
        }
        int min = getMinYuan();
        int max = getMaxYuan();
        if (yuan < min || yuan > max) {
            throw new IllegalArgumentException("充值金额需在 " + min + "–" + max + " 元之间");
        }
        String normalized = normalizeChannel(channel);
        RechargeOrder order = new RechargeOrder();
        order.setOrderNo(nextOrderNo());
        order.setUserId(userId);
        order.setAmountFen(yuan * 100);
        order.setPoints(yuan * getPointsPerYuan());
        order.setChannel(normalized);
        order.setStatus("PENDING");
        order.setRemark("充值 " + yuan + " 元（" + channelName(normalized) + "）");
        rechargeOrderMapper.insertOrder(order);
        return order;
    }

    private String nextOrderNo() {
        String time = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        int random = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return "R" + time + random;
    }

    /**
     * 模拟支付成功：订单置 PAID 并给用户加积分。
     *
     * 幂等：订单已是 PAID 时不再重复入账，直接返回当前余额。
     *
     * @return {orderNo, status, points, balance, alreadyPaid}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> mockPay(Long userId, String orderNo) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 必填");
        }
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new IllegalArgumentException("orderNo 必填");
        }
        RechargeOrder order = rechargeOrderMapper.selectByOrderNo(orderNo.trim());
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("订单不属于当前用户");
        }
        if ("PAID".equals(order.getStatus())) {
            return payResult(order, currentBalance(userId), true);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new IllegalStateException("订单状态不可支付：" + order.getStatus());
        }
        return creditOrder(order);
    }

    /**
     * 订单入账（支付回调复用入口）：条件更新 PENDING → PAID 成功后加积分。
     * 已支付订单不重复入账，直接返回当前余额。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> creditOrder(RechargeOrder order) {
        int updated = rechargeOrderMapper.markPaid(order.getOrderId(), new Date());
        if (updated == 0) {
            // 并发下已被其它请求置为 PAID：幂等返回，不再加积分
            return payResult(order, currentBalance(order.getUserId()), true);
        }
        int balance = pointsService.grantPoints(
                order.getUserId(),
                order.getPoints(),
                "RECHARGE",
                order.getOrderId(),
                order.getRemark());
        return payResult(order, balance, false);
    }

    private Map<String, Object> payResult(RechargeOrder order, int balance, boolean alreadyPaid) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", order.getOrderNo());
        data.put("status", "PAID");
        data.put("points", order.getPoints());
        data.put("amountFen", order.getAmountFen());
        data.put("balance", balance);
        data.put("alreadyPaid", alreadyPaid);
        return data;
    }

    private int currentBalance(Long userId) {
        return pointsService.currentBalance(userId);
    }

    /** 用户最近充值订单（充值记录）。 */
    public List<RechargeOrder> orders(Long userId, int limit) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 必填");
        }
        int size = limit <= 0 ? 20 : Math.min(limit, 100);
        return rechargeOrderMapper.selectByUserId(userId, size);
    }
}
