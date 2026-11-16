package com.smartscript.platform.content.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.StringUtils;
import com.smartscript.platform.content.domain.SysCopyrightApplication;
import com.smartscript.platform.content.domain.SysCopyrightCenterConfig;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.dto.copyright.AppCopyrightApplyRequest;
import com.smartscript.platform.content.dto.copyright.AppCopyrightFeeQuoteDto;
import com.smartscript.platform.content.dto.copyright.AppCopyrightPayOrderDto;
import com.smartscript.platform.content.dto.copyright.AppCopyrightResubmitRequest;
import com.smartscript.platform.content.service.CopyrightFeeCalculator;
import com.smartscript.platform.content.service.ICopyrightPayOrderService;
import com.smartscript.platform.content.service.ISysCopyrightApplicationService;
import com.smartscript.platform.content.service.ISysCopyrightCenterConfigService;
import com.smartscript.platform.content.service.ISysWorkService;
import com.smartscript.platform.content.util.CopyrightStatusUtil;
import com.smartscript.platform.review.service.RiskCheckService;

/**
 * 版权确权缴费实现：支付前风控与询价，付完再创建申请并提交第三方。
 */
@Service
public class CopyrightPayOrderServiceImpl implements ICopyrightPayOrderService
{
    private static final Logger log = LoggerFactory.getLogger(CopyrightPayOrderServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int EXPIRE_HOURS = 24;

    public static final String STATUS_UNPAID = "unpaid";
    public static final String STATUS_PAYING = "paying";
    public static final String STATUS_PAID = "paid";
    public static final String STATUS_CLOSED = "closed";

    private final JdbcTemplate jdbc;
    private final ISysWorkService workService;
    private final ISysCopyrightCenterConfigService centerConfigService;
    private final ISysCopyrightApplicationService copyrightApplicationService;
    private final CopyrightFeeCalculator feeCalculator;
    private final RiskCheckService riskCheckService;

    public CopyrightPayOrderServiceImpl(
            JdbcTemplate jdbc,
            ISysWorkService workService,
            ISysCopyrightCenterConfigService centerConfigService,
            ISysCopyrightApplicationService copyrightApplicationService,
            CopyrightFeeCalculator feeCalculator,
            RiskCheckService riskCheckService)
    {
        this.jdbc = jdbc;
        this.workService = workService;
        this.centerConfigService = centerConfigService;
        this.copyrightApplicationService = copyrightApplicationService;
        this.feeCalculator = feeCalculator;
        this.riskCheckService = riskCheckService;
    }

    @Override
    public AppCopyrightFeeQuoteDto quoteFee(Long userId, Long workId, String copyrightCenter)
    {
        closeExpiredOrders();
        SysWork work = requireOwnedWork(userId, workId);
        SysCopyrightCenterConfig center = requireEnabledCenter(copyrightCenter);
        int wordCount = resolveWordCount(work);
        CopyrightFeeCalculator.FeeQuote quote = feeCalculator.quote(center, wordCount);

        AppCopyrightFeeQuoteDto dto = new AppCopyrightFeeQuoteDto();
        dto.setWorkId(workId);
        dto.setConfigId(center.getConfigId());
        dto.setCopyrightCenter(center.getCenterCode());
        dto.setCenterName(center.getCenterName());
        dto.setWordCount(quote.getWordCount());
        dto.setFeeAmount(quote.getFeeAmount());
        dto.setFeeType(quote.getFeeType());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppCopyrightPayOrderDto createPayOrder(Long userId, AppCopyrightApplyRequest request)
    {
        closeExpiredOrders();
        if (request == null || request.getWorkId() == null)
        {
            throw new IllegalArgumentException("作品ID不能为空");
        }
        if (StringUtils.isBlank(request.getCopyrightCenter()))
        {
            throw new IllegalArgumentException("版权中心不能为空");
        }
        requireStrictMaterials(request.getFileUrl(), request.getOriginalityProofUrls(),
                request.getAuthorIdentityDocUrls());
        requireRightsHolder(request.getRightsHolderName(), request.getIdDocType(), request.getIdDocNo());

        SysWork work = requireOwnedWork(userId, request.getWorkId());
        assertNoActiveApplication(userId, request.getWorkId());
        assertNoOpenPayOrder(userId, request.getWorkId());

        // 支付前违规拦截：作品标题/简介敏感词
        riskCheckService.checkContentOrThrow(userId,
                nullToEmpty(work.getTitle()) + nullToEmpty(work.getSummary()));

        SysCopyrightCenterConfig center = requireEnabledCenter(request.getCopyrightCenter());
        int wordCount = resolveWordCount(work);
        CopyrightFeeCalculator.FeeQuote quote = feeCalculator.quote(center, wordCount);

        Map<String, Object> payload = new HashMap<>();
        payload.put("workId", request.getWorkId());
        payload.put("workType", request.getWorkType());
        payload.put("completeTime", request.getCompleteTime());
        payload.put("copyrightCenter", center.getCenterCode());
        payload.put("configId", center.getConfigId());
        payload.put("fileUrl", request.getFileUrl());
        payload.put("originalityProofUrls", request.getOriginalityProofUrls());
        payload.put("authorIdentityDocUrls", request.getAuthorIdentityDocUrls());
        payload.put("creationProcessUrls", request.getCreationProcessUrls());
        payload.put("otherProofUrls", request.getOtherProofUrls());
        payload.put("rightsHolderName", request.getRightsHolderName());
        payload.put("idDocType", request.getIdDocType());
        payload.put("idDocNo", request.getIdDocNo());
        payload.put("remark", request.getRemark());
        payload.put("resubmitFromApplyId", null);

        return insertPayOrder(userId, work, center, quote, payload);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppCopyrightPayOrderDto createResubmitPayOrder(Long userId, AppCopyrightResubmitRequest request)
    {
        closeExpiredOrders();
        if (request == null || request.getOriginalApplyId() == null)
        {
            throw new IllegalArgumentException("原申请ID不能为空");
        }
        SysCopyrightApplication original = copyrightApplicationService
                .selectCopyrightApplicationByApplyId(request.getOriginalApplyId());
        if (original == null || !userId.equals(original.getUserId()))
        {
            throw new IllegalArgumentException("原申请记录不存在");
        }
        if (!CopyrightStatusUtil.STATUS_REJECTED.equals(original.getStatus()))
        {
            throw new IllegalArgumentException("只有已驳回的申请才能重新提交");
        }
        requireStrictMaterials(request.getFileUrl(), request.getOriginalityProofUrls(),
                request.getAuthorIdentityDocUrls());

        SysWork work = requireOwnedWork(userId, original.getWorkId());
        assertNoActiveApplication(userId, original.getWorkId());
        assertNoOpenPayOrder(userId, original.getWorkId());
        riskCheckService.checkContentOrThrow(userId,
                nullToEmpty(work.getTitle()) + nullToEmpty(work.getSummary()));

        SysCopyrightCenterConfig center = null;
        if (StringUtils.isNotBlank(original.getCopyrightCenter()))
        {
            center = findCenterByCode(original.getCopyrightCenter());
        }
        if (center == null && original.getConfigId() != null)
        {
            center = centerConfigService.selectCopyrightCenterConfigByConfigId(original.getConfigId());
        }
        if (center == null || !"enabled".equals(center.getStatus()))
        {
            throw new IllegalArgumentException("版权中心不可用");
        }

        int wordCount = resolveWordCount(work);
        CopyrightFeeCalculator.FeeQuote quote = feeCalculator.quote(center, wordCount);

        Map<String, Object> payload = new HashMap<>();
        payload.put("workId", original.getWorkId());
        payload.put("workType", request.getWorkType());
        payload.put("completeTime", request.getCompleteTime());
        payload.put("copyrightCenter", center.getCenterCode());
        payload.put("configId", center.getConfigId());
        payload.put("fileUrl", request.getFileUrl());
        payload.put("originalityProofUrls", request.getOriginalityProofUrls());
        payload.put("authorIdentityDocUrls", request.getAuthorIdentityDocUrls());
        payload.put("creationProcessUrls", request.getCreationProcessUrls());
        payload.put("otherProofUrls", request.getOtherProofUrls());
        payload.put("remark", request.getRemark());
        payload.put("resubmitFromApplyId", original.getApplyId());

        return insertPayOrder(userId, work, center, quote, payload);
    }

    @Override
    public AppCopyrightPayOrderDto getPayOrder(Long payOrderId, Long userId)
    {
        closeExpiredOrders();
        Map<String, Object> row = loadPayOrder(payOrderId);
        if (row == null || ((Number) row.get("user_id")).longValue() != userId)
        {
            return null;
        }
        return toDto(row);
    }

    @Override
    public List<AppCopyrightPayOrderDto> listOpenPayOrders(Long userId, String workName)
    {
        closeExpiredOrders();
        StringBuilder sql = new StringBuilder(
                "SELECT o.pay_order_id,o.pay_no,o.user_id,o.work_id,o.config_id,o.copyright_center,"
                        + "o.word_count,o.fee_amount,o.status,o.expire_time,o.apply_id,o.payload_json,"
                        + "o.create_time,w.title AS work_name,c.center_name "
                        + "FROM sys_copyright_pay_order o "
                        + "LEFT JOIN sys_work w ON w.work_id=o.work_id "
                        + "LEFT JOIN sys_copyright_center_config c ON c.config_id=o.config_id "
                        + "WHERE o.user_id=? AND o.status IN ('unpaid','paying') ");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (StringUtils.isNotBlank(workName))
        {
            sql.append("AND w.title LIKE ? ");
            args.add("%" + workName.trim() + "%");
        }
        sql.append("ORDER BY o.create_time DESC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        List<AppCopyrightPayOrderDto> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows)
        {
            AppCopyrightPayOrderDto dto = toDto(row);
            Object workTitle = row.get("work_name");
            if (workTitle != null)
            {
                dto.setWorkName(String.valueOf(workTitle));
            }
            Object centerName = row.get("center_name");
            if (centerName != null)
            {
                dto.setCenterName(String.valueOf(centerName));
            }
            list.add(dto);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> directPay(Long payOrderId, Long userId)
    {
        // 临时跳过支付宝沙箱：直接履约建申请并提交第三方
        return fulfillPaidOrder(payOrderId, userId, "DIRECT-" + System.currentTimeMillis());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> fulfillPaidOrder(Long payOrderId, Long userId, String tradeNo)
    {
        closeExpiredOrders();
        Map<String, Object> row = loadPayOrderForUpdate(payOrderId);
        if (row == null || ((Number) row.get("user_id")).longValue() != userId)
        {
            throw new IllegalArgumentException("支付单不存在");
        }
        String status = String.valueOf(row.get("status"));
        Object applyIdObj = row.get("apply_id");
        if (STATUS_PAID.equals(status) && applyIdObj != null)
        {
            return Map.of("paid", true, "applyId", ((Number) applyIdObj).longValue(), "alreadyFulfilled", true);
        }
        if (STATUS_CLOSED.equals(status))
        {
            throw new IllegalStateException("支付单已关闭");
        }
        if (!STATUS_PAID.equals(status) && !STATUS_PAYING.equals(status) && !STATUS_UNPAID.equals(status))
        {
            throw new IllegalStateException("支付单状态不可履约");
        }

        // 调用方（沙箱 confirm）应已置 paid；此处兼容直接履约
        if (!STATUS_PAID.equals(status))
        {
            jdbc.update("UPDATE sys_copyright_pay_order SET status=?, paid_time=NOW(), update_time=NOW() "
                            + "WHERE pay_order_id=? AND status IN ('unpaid','paying')",
                    STATUS_PAID, payOrderId);
            row = loadPayOrderForUpdate(payOrderId);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = parsePayload((String) row.get("payload_json"));
        Long workId = ((Number) row.get("work_id")).longValue();
        Long configId = ((Number) row.get("config_id")).longValue();
        String centerCode = (String) row.get("copyright_center");
        int wordCount = ((Number) row.get("word_count")).intValue();
        BigDecimal feeAmount = (BigDecimal) row.get("fee_amount");

        SysCopyrightApplication application = new SysCopyrightApplication();
        application.setUserId(userId);
        application.setWorkId(workId);
        application.setConfigId(configId);
        application.setCopyrightCenter(centerCode);
        application.setStatus(CopyrightStatusUtil.STATUS_SUBMITTED);
        application.setSyncStatus("pending");
        application.setWordCount(wordCount);
        application.setFeeAmount(feeAmount);
        application.setPayOrderId(payOrderId);
        String applyNo = "CA" + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
        application.setApplyNo(applyNo);
        application.setSubmitTime(new Date());
        application.setCreateTime(new Date());
        application.setRemark(asText(payload.get("remark")));
        application.setAttachmentUrl(asText(payload.get("fileUrl")));
        application.setOriginalityProof(asText(payload.get("originalityProofUrls")));
        application.setAuthorIdentityDoc(asText(payload.get("authorIdentityDocUrls")));
        application.setCreationProcess(asText(payload.get("creationProcessUrls")));
        application.setOtherProof(asText(payload.get("otherProofUrls")));
        application.setRightsHolderName(asText(payload.get("rightsHolderName")));
        application.setIdDocType(asText(payload.get("idDocType")));
        application.setIdDocNo(asText(payload.get("idDocNo")));

        copyrightApplicationService.insertCopyrightApplication(application);
        Long applyId = application.getApplyId();
        if (applyId == null)
        {
            throw new IllegalStateException("创建版权申请失败");
        }

        try
        {
            copyrightApplicationService.submitApplicationToCenter(applyId);
        }
        catch (Exception e)
        {
            log.error("支付成功后提交第三方失败 payOrderId={}, applyId={}", payOrderId, applyId, e);
            // 申请已创建，保持 submitted，可由运维重推；不回滚支付
        }

        jdbc.update("UPDATE sys_copyright_pay_order SET apply_id=?, status=?, paid_time=IFNULL(paid_time,NOW()), "
                        + "update_time=NOW(), remark=? WHERE pay_order_id=?",
                applyId, STATUS_PAID,
                tradeNo == null ? null : ("tradeNo=" + tradeNo),
                payOrderId);

        Map<String, Object> result = new HashMap<>();
        result.put("paid", true);
        result.put("applyId", applyId);
        result.put("alreadyFulfilled", false);
        return result;
    }

    @Override
    public void closeExpiredOrders()
    {
        jdbc.update("UPDATE sys_copyright_pay_order SET status=?, update_time=NOW() "
                        + "WHERE status IN ('unpaid','paying') AND expire_time < NOW()",
                STATUS_CLOSED);
    }

    private AppCopyrightPayOrderDto insertPayOrder(
            Long userId, SysWork work, SysCopyrightCenterConfig center,
            CopyrightFeeCalculator.FeeQuote quote, Map<String, Object> payload)
    {
        String payNo = "CP" + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, EXPIRE_HOURS);
        Date expireTime = cal.getTime();
        String payloadJson;
        try
        {
            payloadJson = MAPPER.writeValueAsString(payload);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("保存申请快照失败");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO sys_copyright_pay_order("
                            + "pay_no,user_id,work_id,config_id,copyright_center,word_count,fee_amount,"
                            + "status,expire_time,payload_json,create_time,update_time) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,NOW(),NOW())",
                    new String[] {"pay_order_id"});
            ps.setString(1, payNo);
            ps.setLong(2, userId);
            ps.setLong(3, work.getWorkId());
            ps.setLong(4, center.getConfigId());
            ps.setString(5, center.getCenterCode());
            ps.setInt(6, quote.getWordCount());
            ps.setBigDecimal(7, quote.getFeeAmount());
            ps.setString(8, STATUS_UNPAID);
            ps.setTimestamp(9, new java.sql.Timestamp(expireTime.getTime()));
            ps.setString(10, payloadJson);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null)
        {
            throw new IllegalStateException("创建支付单失败");
        }
        Map<String, Object> row = loadPayOrder(key.longValue());
        AppCopyrightPayOrderDto dto = toDto(row);
        dto.setWorkName(work.getTitle());
        dto.setCenterName(center.getCenterName());
        return dto;
    }

    private void assertNoActiveApplication(Long userId, Long workId)
    {
        SysCopyrightApplication query = new SysCopyrightApplication();
        query.setUserId(userId);
        query.setWorkId(workId);
        List<SysCopyrightApplication> existList = copyrightApplicationService.selectCopyrightApplicationList(query);
        for (SysCopyrightApplication exist : existList)
        {
            if (CopyrightStatusUtil.isActiveApplicationStatus(exist.getStatus()))
            {
                throw new IllegalArgumentException("该作品已有进行中的版权申请");
            }
        }
    }

    private void assertNoOpenPayOrder(Long userId, Long workId)
    {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM sys_copyright_pay_order WHERE user_id=? AND work_id=? "
                        + "AND status IN ('unpaid','paying') AND expire_time >= NOW()",
                Integer.class, userId, workId);
        if (count != null && count > 0)
        {
            throw new IllegalArgumentException("该作品已有待支付的版权申请单，请先完成支付或等待关单");
        }
    }

    private void requireMaterials(String fileUrl, String originality, String identity,
            String creation, String other)
    {
        boolean has = StringUtils.isNotBlank(fileUrl)
                || StringUtils.isNotBlank(originality)
                || StringUtils.isNotBlank(identity)
                || StringUtils.isNotBlank(creation)
                || StringUtils.isNotBlank(other);
        if (!has)
        {
            throw new IllegalArgumentException("请上传申请材料");
        }
    }

    /** 首版必填加严：作品样本 + 身份证明 */
    private void requireStrictMaterials(String fileUrl, String originality, String identity)
    {
        boolean sample = StringUtils.isNotBlank(fileUrl) || StringUtils.isNotBlank(originality);
        if (!sample)
        {
            throw new IllegalArgumentException("请上传作品样本/原创性证明材料");
        }
        if (StringUtils.isBlank(identity))
        {
            throw new IllegalArgumentException("请上传作者身份证明材料");
        }
    }

    private void requireRightsHolder(String name, String idType, String idNo)
    {
        if (StringUtils.isBlank(name))
        {
            throw new IllegalArgumentException("请填写权利人姓名");
        }
        if (StringUtils.isBlank(idType))
        {
            throw new IllegalArgumentException("请选择证件类型");
        }
        if (StringUtils.isBlank(idNo))
        {
            throw new IllegalArgumentException("请填写证件号码");
        }
    }

    private SysWork requireOwnedWork(Long userId, Long workId)
    {
        SysWork work = workService.selectWorkById(workId);
        if (work == null)
        {
            throw new IllegalArgumentException("作品不存在");
        }
        if (userId == null || !userId.equals(work.getAuthorId()))
        {
            throw new IllegalArgumentException("无权限申请该作品版权");
        }
        return work;
    }

    private SysCopyrightCenterConfig requireEnabledCenter(String centerCode)
    {
        SysCopyrightCenterConfig center = findCenterByCode(centerCode);
        if (center == null || !"enabled".equals(center.getStatus()))
        {
            throw new IllegalArgumentException("版权中心不可用");
        }
        return center;
    }

    private SysCopyrightCenterConfig findCenterByCode(String centerCode)
    {
        if (StringUtils.isBlank(centerCode))
        {
            return null;
        }
        SysCopyrightCenterConfig query = new SysCopyrightCenterConfig();
        query.setCenterCode(centerCode);
        List<SysCopyrightCenterConfig> list = centerConfigService.selectCopyrightCenterConfigList(query);
        return list.isEmpty() ? null : list.get(0);
    }

    private int resolveWordCount(SysWork work)
    {
        Integer wc = work.getWordCount();
        if (wc == null || wc <= 0)
        {
            throw new IllegalArgumentException("作品正文字数为0，无法计价，请先完善章节正文");
        }
        return wc;
    }

    private Map<String, Object> loadPayOrder(Long payOrderId)
    {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT pay_order_id,pay_no,user_id,work_id,config_id,copyright_center,word_count,"
                        + "fee_amount,status,expire_time,apply_id,payload_json,paid_time,create_time "
                        + "FROM sys_copyright_pay_order WHERE pay_order_id=?",
                payOrderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> loadPayOrderForUpdate(Long payOrderId)
    {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT pay_order_id,pay_no,user_id,work_id,config_id,copyright_center,word_count,"
                        + "fee_amount,status,expire_time,apply_id,payload_json,paid_time,create_time "
                        + "FROM sys_copyright_pay_order WHERE pay_order_id=? FOR UPDATE",
                payOrderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private AppCopyrightPayOrderDto toDto(Map<String, Object> row)
    {
        AppCopyrightPayOrderDto dto = new AppCopyrightPayOrderDto();
        dto.setPayOrderId(((Number) row.get("pay_order_id")).longValue());
        dto.setPayNo((String) row.get("pay_no"));
        dto.setWorkId(((Number) row.get("work_id")).longValue());
        dto.setConfigId(((Number) row.get("config_id")).longValue());
        dto.setCopyrightCenter((String) row.get("copyright_center"));
        dto.setWordCount(((Number) row.get("word_count")).intValue());
        dto.setFeeAmount((BigDecimal) row.get("fee_amount"));
        dto.setStatus((String) row.get("status"));
        dto.setExpireTime(asDate(row.get("expire_time")));
        dto.setCreateTime(asDate(row.get("create_time")));
        Object applyId = row.get("apply_id");
        if (applyId instanceof Number)
        {
            dto.setApplyId(((Number) applyId).longValue());
        }
        SysCopyrightCenterConfig center = findCenterByCode(dto.getCopyrightCenter());
        if (center != null)
        {
            dto.setCenterName(center.getCenterName());
        }
        SysWork work = workService.selectWorkById(dto.getWorkId());
        if (work != null)
        {
            dto.setWorkName(work.getTitle());
        }
        return dto;
    }

    private Date asDate(Object value)
    {
        if (value instanceof java.sql.Timestamp)
        {
            return new Date(((java.sql.Timestamp) value).getTime());
        }
        if (value instanceof Date)
        {
            return (Date) value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String json)
    {
        try
        {
            return MAPPER.readValue(json, Map.class);
        }
        catch (Exception e)
        {
            throw new IllegalStateException("支付单快照损坏");
        }
    }

    private static String asText(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private static String nullToEmpty(String value)
    {
        return value == null ? "" : value;
    }
}
