package com.smartscript.platform.trade.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.smartscript.platform.trade.domain.DemandSandboxPayment;
import com.smartscript.platform.trade.domain.SysDemand;
import com.smartscript.platform.trade.mapper.DemandSandboxPaymentMapper;

/**
 * 需求任务的支付宝官方沙箱入账服务（托管付款 + 押金付款）。
 *
 * <p>与队友的订单沙箱 {@link AlipaySandboxPaymentService} 并存但相互独立：本服务只处理
 * 需求任务的两种收款——发布者托管(escrow)、创作者押金(deposit)，落到 demand_sandbox_payment。
 * 仅入账走真实沙箱；放款/退还/没收等出账为记账，在 {@link TradeDemandService} 内完成。</p>
 *
 * <p>安全护栏：仅当 APP_ALIPAY_SANDBOX_ENABLED=true + 三把密钥齐全，且当前库名以 _sandbox 结尾
 * 或被 APP_ALIPAY_SANDBOX_ALLOWED_DATABASES 显式授权时才允许执行。</p>
 */
@Service
public class DemandSandboxPaymentService
{
    static final String GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    /** 扫码收款单号超过该时长视为可能失效，同一付款人再次发起时自动换新码。 */
    private static final long QR_STALE_MILLIS = 10 * 60 * 1000L;

    private final JdbcTemplate jdbc;
    private final DemandSandboxPaymentMapper paymentMapper;
    private final TradeDemandService demandService;

    private final boolean enabled;
    private final String appId;
    private final String privateKey;
    private final String publicKey;

    @Value("${APP_ALIPAY_SANDBOX_ALLOWED_DATABASES:}")
    private String allowedDatabases;

    @Value("${APP_ALIPAY_SANDBOX_NOTIFY_URL:}")
    private String notifyUrl;

    public DemandSandboxPaymentService(JdbcTemplate jdbc,
            DemandSandboxPaymentMapper paymentMapper,
            TradeDemandService demandService,
            @Value("${APP_ALIPAY_SANDBOX_ENABLED:false}") boolean enabled,
            @Value("${APP_ALIPAY_SANDBOX_APP_ID:}") String appId,
            @Value("${APP_ALIPAY_SANDBOX_PRIVATE_KEY:}") String privateKey,
            @Value("${APP_ALIPAY_SANDBOX_PUBLIC_KEY:}") String publicKey)
    {
        this.jdbc = jdbc;
        this.paymentMapper = paymentMapper;
        this.demandService = demandService;
        this.enabled = enabled;
        this.appId = appId;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    private void requireSandbox()
    {
        if (!enabled || appId.isBlank() || privateKey.isBlank() || publicKey.isBlank())
            throw new IllegalStateException("支付宝沙箱尚未配置");
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        String normalized = database == null ? "" : database.trim().toLowerCase(Locale.ROOT);
        boolean isolated = normalized.endsWith("_sandbox");
        boolean explicitlyAllowed = allowedDatabases != null && Arrays.stream(allowedDatabases.split(","))
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
        if (normalized.isEmpty() || (!isolated && !explicitlyAllowed))
            throw new IllegalStateException("支付宝沙箱仅允许使用隔离库或明确授权的开发库");
    }

    AlipayClient client()
    {
        return new DefaultAlipayClient(GATEWAY, appId, privateKey, "json", "UTF-8", publicKey, "RSA2");
    }

    /* ==================== 托管付款（发布者） ==================== */

    @Transactional
    public Map<String, Object> createEscrow(Long demandId, Long publisherId, boolean refresh)
    {
        requireSandbox();
        SysDemand demand = loadDemand(demandId);
        if (publisherId == null || !publisherId.equals(demand.getClientId()))
            throw new IllegalArgumentException("只有发布者可支付托管款");
        if (!TradeDemandService.DEMAND_STATUS_OPEN.equals(demand.getStatus()))
            throw new IllegalStateException("当前任务状态不可支付托管款");
        if (TradeDemandService.ESCROW_ESCROWED.equals(demand.getEscrowStatus()))
            throw new IllegalStateException("托管款已支付");
        BigDecimal amount = demand.getBudget();
        assertAmount(amount);
        return precreate(demandId, DemandSandboxPayment.PURPOSE_ESCROW, publisherId, amount,
                "剧云策任务托管 " + demandId, refresh, payer -> demandService.markEscrowed(demandId));
    }

    @Transactional
    public Map<String, Object> confirmEscrow(Long demandId, Long publisherId)
    {
        requireSandbox();
        SysDemand demand = loadDemand(demandId);
        if (publisherId == null || !publisherId.equals(demand.getClientId()))
            throw new IllegalArgumentException("只有发布者可确认托管款");
        return confirm(demandId, DemandSandboxPayment.PURPOSE_ESCROW, amountOf(demand, true),
                payer -> demandService.markEscrowed(demandId));
    }

    /* ==================== 押金付款（创作者） ==================== */

    @Transactional
    public Map<String, Object> createDeposit(Long demandId, Long creatorId, boolean refresh)
    {
        requireSandbox();
        SysDemand demand = loadDemand(demandId);
        if (creatorId == null) throw new IllegalArgumentException("未登录");
        if (creatorId.equals(demand.getClientId()))
            throw new IllegalArgumentException("不能接自己发布的任务");
        if (!TradeDemandService.DEMAND_STATUS_OPEN.equals(demand.getStatus()))
            throw new IllegalStateException("该任务已被接单或不可接单");
        if (!TradeDemandService.ESCROW_ESCROWED.equals(demand.getEscrowStatus()))
            throw new IllegalStateException("该任务尚未完成托管付款，暂不可接单");
        BigDecimal amount = TradeDemandService.computeDepositAmount(demand.getBudget());
        assertAmount(amount);
        return precreate(demandId, DemandSandboxPayment.PURPOSE_DEPOSIT, creatorId, amount,
                "剧云策任务押金 " + demandId, refresh, payer -> demandService.claimDemand(demandId, payer));
    }

    @Transactional
    public Map<String, Object> confirmDeposit(Long demandId, Long creatorId)
    {
        requireSandbox();
        SysDemand demand = loadDemand(demandId);
        BigDecimal amount = TradeDemandService.computeDepositAmount(demand.getBudget());
        // 先到先得仲裁：押金支付记录按(任务+用途)全局唯一、多人共用同一二维码/订单号，
        // 支付宝侧同一 out_trade_no 只会被成功支付一次，落败者不会被扣款；
        // 接单占位由 claimDemand 的条件 UPDATE（status='open'）保证只有一人成功。
        // 若单已被他人接走（先支付先确认者已 markPaid+claim），后到的确认者必须拿到
        // paid=false/claimed=false+reason，而不是被共享记录的 PAID 状态误导成“接单成功”。
        if (demand.getAcceptedCreatorId() != null && !demand.getAcceptedCreatorId().equals(creatorId))
        {
            Map<String, Object> taken = new HashMap<>();
            taken.put("paid", false);
            taken.put("claimed", false);
            taken.put("sandbox", true);
            taken.put("reason", "该任务已被其他创作者接单，您的押金未扣款，请另选任务");
            return taken;
        }
        // 结算归属取支付记录里的付款人：confirm 内查单若命中他人已扫付的旧码，
        // 应结算给真实付款人，而不是误把任务判给后到的确认者。
        DemandSandboxPayment depositRow = paymentMapper.selectForUpdate(demandId, DemandSandboxPayment.PURPOSE_DEPOSIT);
        Long recordPayerId = depositRow == null ? creatorId : depositRow.getPayerId();
        Map<String, Object> result = new HashMap<>(confirm(demandId, DemandSandboxPayment.PURPOSE_DEPOSIT, amount,
                payer -> demandService.claimDemand(demandId, payer)));
        boolean paid = Boolean.TRUE.equals(result.get("paid"));
        result.put("claimed", paid && creatorId.equals(recordPayerId));
        if (paid && !creatorId.equals(recordPayerId))
            result.put("reason", "该押金已由其他创作者支付并结算，任务归付款人所有");
        return result;
    }

    /* ==================== 管理端只读 ==================== */

    /**
     * 某任务的沙箱收款记录（托管/押金各一行）。纯读，不做沙箱护栏校验，
     * 否则管理端在未开启沙箱的环境里连历史资金都查不到。
     */
    public List<DemandSandboxPayment> listPayments(Long demandId)
    {
        return paymentMapper.selectListByDemandId(demandId);
    }

    /* ==================== 异步通知（可选，验签后主动查单为准） ==================== */

    public boolean processNotification(Map<String, String> params)
    {
        requireSandbox();
        try
        {
            if (!appId.equals(params.get("app_id")) || !"RSA2".equals(params.get("sign_type"))
                    || !AlipaySignature.rsaCheckV1(new HashMap<>(params), publicKey, "UTF-8", "RSA2"))
                return false;
            String tradeStatus = params.get("trade_status");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus))
                return true;
            String outTradeNo = params.get("out_trade_no");
            if (outTradeNo == null || outTradeNo.isBlank()) return false;
            DemandSandboxPayment rec = paymentMapper.selectByOutTradeNo(outTradeNo);
            if (rec == null) return false;
            if (DemandSandboxPayment.PURPOSE_ESCROW.equals(rec.getPurpose()))
            {
                SysDemand demand = loadDemand(rec.getDemandId());
                confirm(rec.getDemandId(), DemandSandboxPayment.PURPOSE_ESCROW, demand.getBudget(),
                        payer -> demandService.markEscrowed(rec.getDemandId()));
            }
            else
            {
                SysDemand demand = loadDemand(rec.getDemandId());
                BigDecimal deposit = TradeDemandService.computeDepositAmount(demand.getBudget());
                confirm(rec.getDemandId(), DemandSandboxPayment.PURPOSE_DEPOSIT, deposit,
                        payer -> demandService.claimDemand(rec.getDemandId(), payer));
            }
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /* ==================== 内部通用流程 ==================== */

    /**
     * 生成二维码：同一付款人重复发起时，待支付单号超过 {@link #QR_STALE_MILLIS} 或前端点「刷新」
     * 就换新商户单号重新预下单，避免拿到支付宝侧已失效的旧码。
     *
     * <p>押金码按(任务+用途)全局共用。同一付款人再次发起按上述规则换号；
     * 换人发起时先核验旧码：旧码已被原付款人支付则按其归属就地结算，
     * 未支付则关旧码并把单号换给新付款人，避免新付款人扫旧码付款后资金记在他人名下。</p>
     *
     * <p>预算被编辑过也必须换号：待支付记录里的金额是旧预算，若仍按新预算校验就会先抛错、
     * 换号分支永远走不到，这笔款项从此再也付不出去。旧码确实已付时照常结算（钱是真的，
     * 不能搁置），并在返回体里标出金额已变更，由管理端资金流水核对差额。</p>
     */
    private Map<String, Object> precreate(Long demandId, String purpose, Long payerId, BigDecimal amount,
            String subject, boolean refresh, Consumer<Long> settle)
    {
        String prefix = DemandSandboxPayment.PURPOSE_ESCROW.equals(purpose) ? "SDESC" : "SDDEP";
        String candidate = prefix + java.util.UUID.randomUUID().toString().replace("-", "");
        paymentMapper.upsertPending(demandId, purpose, payerId, amount, candidate);
        DemandSandboxPayment row = paymentMapper.selectForUpdate(demandId, purpose);
        if (row == null) throw new IllegalStateException("支付记录创建失败");
        if (DemandSandboxPayment.STATUS_PAID.equals(row.getStatus()))
            throw new IllegalStateException("该笔款项已支付");
        String outTradeNo = row.getOutTradeNo();
        boolean samePayer = payerId.equals(row.getPayerId());
        boolean amountChanged = amount.compareTo(row.getAmount()) != 0;
        boolean stale = row.getUpdatedAt() == null
                || row.getUpdatedAt().getTime() < System.currentTimeMillis() - QR_STALE_MILLIS;
        boolean closed = DemandSandboxPayment.STATUS_CLOSED.equals(row.getStatus());
        if (!samePayer || refresh || stale || closed || amountChanged)
        {
            if (!closed)
            {
                // 换号前先查一次旧单：旧码若已被扫付，就地结算并把结算结果返回前端，
                // 这里不能抛异常，否则同事务内的结算会被回滚，钱到账却漏单。
                // 传 null 表示「以记录里的金额为准结算」：预算变更时旧码可能真被按旧金额扫付，
                // 按新预算校验会抛错并卡死整条付款链路。
                Map<String, Object> settled = confirm(demandId, purpose, null, settle);
                if (Boolean.TRUE.equals(settled.get("paid")))
                {
                    if (!DemandSandboxPayment.PURPOSE_ESCROW.equals(purpose) && !samePayer)
                    {
                        // 旧码由他人扫付：单已归付款人，这里必须像 confirmDeposit 一样将
                        // paid 与 claimed 分开，否则落败者会收到「接单成功」的错误提示。
                        Map<String, Object> taken = new HashMap<>(settled);
                        taken.put("claimed", false);
                        taken.put("reason", "该押金已由其他创作者支付并结算，任务归付款人所有");
                        return taken;
                    }
                    return amountChanged ? withAmountChanged(settled) : settled;
                }
                // 支付宝侧还活着的旧单要主动关掉，旧码才真正失效
                if (!Boolean.TRUE.equals(settled.get("closed"))) closeQuietly(outTradeNo);
            }
            paymentMapper.rotatePending(demandId, purpose, candidate, payerId, amount);
            outTradeNo = candidate;
        }
        try
        {
            AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
            model.setOutTradeNo(outTradeNo);
            model.setTotalAmount(amount.toPlainString());
            model.setSubject(subject);
            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            request.setBizModel(model);
            if (notifyUrl != null && !notifyUrl.isBlank())
            {
                if (!notifyUrl.startsWith("https://")) throw new IllegalStateException("沙箱通知地址必须使用 HTTPS");
                request.setNotifyUrl(notifyUrl);
            }
            AlipayTradePrecreateResponse response = client().execute(request);
            if (!response.isSuccess() || !outTradeNo.equals(response.getOutTradeNo())
                    || response.getQrCode() == null || response.getQrCode().isBlank())
                throw new IllegalStateException("支付宝沙箱创建支付失败，请检查沙箱配置");
            return Map.of("qrCode", response.getQrCode(), "outTradeNo", outTradeNo,
                    "purpose", purpose, "amount", amount, "sandbox", true);
        }
        catch (AlipayApiException e)
        {
            throw new IllegalStateException("支付宝沙箱服务暂不可用");
        }
    }

    /** 结算金额与任务当前应缴金额不一致时打标，供前端与管理端核对差额。 */
    private Map<String, Object> withAmountChanged(Map<String, Object> settled)
    {
        Map<String, Object> result = new HashMap<>(settled);
        result.put("amountChanged", true);
        return result;
    }

    /** 尽力关闭旧单让旧码失效；沙箱里未成过交易的单会返回 TRADE_NOT_EXIST，属正常，不影响换新码。 */
    private void closeQuietly(String outTradeNo)
    {
        try
        {
            AlipayTradeCloseModel model = new AlipayTradeCloseModel();
            model.setOutTradeNo(outTradeNo);
            AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
            request.setBizModel(model);
            client().execute(request);
        }
        catch (AlipayApiException e)
        {
            // 关闭失败只是旧码延后自然过期，新码照常可用
        }
    }

    /** 查单确认：以支付宝主动查询结果为准，成功则结算并回调业务动作（结算对象为记录中的付款人）。 */
    private Map<String, Object> confirm(Long demandId, String purpose, BigDecimal expectedAmount, Consumer<Long> settle)
    {
        DemandSandboxPayment row = paymentMapper.selectForUpdate(demandId, purpose);
        if (row == null) throw new IllegalStateException("请先发起沙箱支付");
        if (DemandSandboxPayment.STATUS_PAID.equals(row.getStatus()))
            return Map.of("paid", true, "sandbox", true);
        if (expectedAmount != null && row.getAmount().compareTo(expectedAmount) != 0)
            throw new IllegalStateException("任务金额已变更，请点「刷新收款码」按新金额重新付款");
        String outTradeNo = row.getOutTradeNo();
        try
        {
            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo(outTradeNo);
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            request.setBizModel(model);
            AlipayTradeQueryResponse response = client().execute(request);
            if (!response.isSuccess())
            {
                if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) return Map.of("paid", false, "sandbox", true);
                throw new IllegalStateException("支付宝沙箱查询失败，请检查配置后重试");
            }
            String status = response.getTradeStatus();
            if ("TRADE_CLOSED".equals(status))
            {
                if (!outTradeNo.equals(response.getOutTradeNo())) throw new IllegalStateException("支付宝返回的订单不匹配");
                paymentMapper.markClosed(outTradeNo);
                return Map.of("paid", false, "closed", true, "sandbox", true);
            }
            if (!"TRADE_SUCCESS".equals(status) && !"TRADE_FINISHED".equals(status))
                return Map.of("paid", false, "sandbox", true);
            if (!outTradeNo.equals(response.getOutTradeNo()) || response.getTradeNo() == null || response.getTradeNo().isBlank()
                    || response.getTotalAmount() == null || row.getAmount().compareTo(new BigDecimal(response.getTotalAmount())) != 0)
                throw new IllegalStateException("支付宝返回的订单或金额不匹配");
            paymentMapper.markPaid(outTradeNo, response.getTradeNo());
            settle.accept(row.getPayerId());
            return Map.of("paid", true, "sandbox", true);
        }
        catch (AlipayApiException e)
        {
            throw new IllegalStateException("支付宝沙箱服务暂不可用");
        }
    }

    private SysDemand loadDemand(Long demandId)
    {
        SysDemand demand = demandService.selectDemandById(demandId);
        if (demand == null) throw new IllegalArgumentException("任务不存在");
        return demand;
    }

    private BigDecimal amountOf(SysDemand demand, boolean budget)
    {
        return budget ? demand.getBudget() : TradeDemandService.computeDepositAmount(demand.getBudget());
    }

    private void assertAmount(BigDecimal amount)
    {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2)
            throw new IllegalStateException("支付金额无效");
    }
}
