package com.smartscript.platform.content.service.impl;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.StringUtils;
import com.smartscript.platform.content.domain.SysCopyrightApplication;
import com.smartscript.platform.content.dto.copyright.AppCopyrightSupplementRequest;
import com.smartscript.platform.content.dto.copyright.CopyrightThirdPartyMessageRequest;
import com.smartscript.platform.content.service.ICopyrightThirdPartyMessageService;
import com.smartscript.platform.content.service.ISysCopyrightApplicationService;
import com.smartscript.platform.content.util.CopyrightStatusUtil;

/**
 * 第三方代理消息接收：落库 → 驱动补正/发证/驳回；用户补正不重交官费。
 */
@Service
public class CopyrightThirdPartyMessageServiceImpl implements ICopyrightThirdPartyMessageService
{
    private static final Logger log = LoggerFactory.getLogger(CopyrightThirdPartyMessageServiceImpl.class);

    public static final String TYPE_SUPPLEMENT_REQUIRED = "supplement_required";
    public static final String TYPE_NOTICE = "notice";
    public static final String TYPE_CERTIFICATED = "certificated";
    public static final String TYPE_REJECTED = "rejected";

    public static final String SUPP_OPEN = "open";
    public static final String SUPP_SUBMITTED = "submitted";
    public static final String SUPP_OVERDUE = "overdue";
    public static final String SUPP_CLOSED = "closed";

    private final JdbcTemplate jdbc;
    private final ISysCopyrightApplicationService applicationService;

    public CopyrightThirdPartyMessageServiceImpl(
            JdbcTemplate jdbc,
            ISysCopyrightApplicationService applicationService)
    {
        this.jdbc = jdbc;
        this.applicationService = applicationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> receiveMessage(CopyrightThirdPartyMessageRequest request)
    {
        closeOverdueSupplements();
        if (request == null || StringUtils.isBlank(request.getExternalMessageId()))
        {
            throw new IllegalArgumentException("externalMessageId不能为空");
        }
        if (StringUtils.isBlank(request.getMessageType()))
        {
            throw new IllegalArgumentException("messageType不能为空");
        }

        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(1) FROM sys_copyright_third_party_message WHERE external_message_id=?",
                Integer.class, request.getExternalMessageId());
        if (exists != null && exists > 0)
        {
            Map<String, Object> dup = new HashMap<>();
            dup.put("accepted", true);
            dup.put("duplicate", true);
            dup.put("externalMessageId", request.getExternalMessageId());
            return dup;
        }

        SysCopyrightApplication application = resolveApplication(request.getApplyId(), request.getExternalApplyNo());
        Long applyId = application != null ? application.getApplyId() : null;

        Date deadline = parseDeadline(request.getDeadlineTime());
        if (deadline == null && TYPE_SUPPLEMENT_REQUIRED.equals(request.getMessageType()))
        {
            deadline = plusDays(new Date(), CopyrightStatusUtil.SUPPLEMENT_DEADLINE_DAYS);
        }

        Long messagePk = insertMessage(request, applyId, deadline);

        Map<String, Object> result = new HashMap<>();
        result.put("accepted", true);
        result.put("duplicate", false);
        result.put("messageId", messagePk);
        result.put("applyId", applyId);

        if (application == null)
        {
            markProcessed(messagePk, "ignored", "未匹配到申请，仅落库");
            result.put("processStatus", "ignored");
            return result;
        }

        try
        {
            String type = request.getMessageType().trim();
            if (TYPE_SUPPLEMENT_REQUIRED.equals(type))
            {
                handleSupplementRequired(application, messagePk, request, deadline);
            }
            else if (TYPE_CERTIFICATED.equals(type))
            {
                handleCertificated(application, request);
            }
            else if (TYPE_REJECTED.equals(type))
            {
                handleRejected(application, request);
            }
            else if (TYPE_NOTICE.equals(type) || "other".equals(type))
            {
                // 仅落库展示
            }
            else
            {
                markProcessed(messagePk, "ignored", "未知消息类型:" + type);
                result.put("processStatus", "ignored");
                return result;
            }
            markProcessed(messagePk, "processed", null);
            result.put("processStatus", "processed");
            result.put("applicationStatus", applicationService
                    .selectCopyrightApplicationByApplyId(applyId).getStatus());
        }
        catch (Exception e)
        {
            log.error("处理第三方版权消息失败 messageId={}", messagePk, e);
            markProcessed(messagePk, "failed", e.getMessage());
            throw e;
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> mockPushSupplement(Long applyId, String content)
    {
        if (applyId == null)
        {
            throw new IllegalArgumentException("applyId不能为空");
        }
        SysCopyrightApplication app = applicationService.selectCopyrightApplicationByApplyId(applyId);
        if (app == null)
        {
            throw new IllegalArgumentException("申请不存在");
        }
        CopyrightThirdPartyMessageRequest req = new CopyrightThirdPartyMessageRequest();
        req.setExternalMessageId("MOCK-SUPP-" + applyId + "-" + System.currentTimeMillis());
        req.setApplyId(applyId);
        req.setExternalApplyNo(StringUtils.isNotBlank(app.getExternalApplyNo())
                ? app.getExternalApplyNo()
                : app.getExternalNo());
        req.setMessageType(TYPE_SUPPLEMENT_REQUIRED);
        req.setTitle("版权中心补正通知");
        req.setContent(StringUtils.isNotBlank(content)
                ? content
                : "请补充作品原创性证明及作者身份证明材料（Mock）");
        req.setAttachmentUrl("https://mock-copyright.example.com/supplement.pdf");
        return receiveMessage(req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submitSupplement(Long userId, Long applyId, AppCopyrightSupplementRequest request)
    {
        closeOverdueSupplements();
        if (request == null || StringUtils.isBlank(request.getMaterialsJson()))
        {
            throw new IllegalArgumentException("补正材料不能为空");
        }
        SysCopyrightApplication app = applicationService.selectCopyrightApplicationByApplyId(applyId);
        if (app == null || !userId.equals(app.getUserId()))
        {
            throw new IllegalArgumentException("申请不存在");
        }
        if (!CopyrightStatusUtil.STATUS_NEED_SUPPLEMENT.equals(app.getStatus()))
        {
            throw new IllegalStateException("当前状态不可提交补正");
        }

        Map<String, Object> open = getOpenSupplement(applyId);
        // 状态已是待补正但补正单缺失时，按申请上的截止/要求补建，避免联调脏数据直接失败
        if (open == null)
        {
            open = ensureOpenSupplement(app);
        }
        if (open == null)
        {
            throw new IllegalStateException("没有待处理的补正单");
        }
        Date deadline = toDate(mapGet(open, "deadlineTime", "deadline_time"));
        if (deadline != null && deadline.before(new Date()))
        {
            closeOverdueSupplements();
            throw new IllegalStateException("补正已超时，无法提交");
        }

        Long supplementId = toLong(mapGet(open, "supplementId", "supplement_id"));
        if (supplementId == null)
        {
            throw new IllegalStateException("补正单数据异常，请刷新后重试");
        }
        int updated = jdbc.update(
                "UPDATE sys_copyright_supplement SET status=?, materials_json=?, user_remark=?, "
                        + "submit_time=NOW(), update_time=NOW() WHERE supplement_id=? AND status=?",
                SUPP_SUBMITTED, request.getMaterialsJson(),
                request.getRemark(), supplementId, SUPP_OPEN);
        if (updated == 0)
        {
            throw new IllegalStateException("补正单已处理或不存在，请刷新后重试");
        }

        // 合入申请材料：写入 other_proof 追加说明（保持原分类材料不动）
        String merged = mergeMaterials(app.getOtherProof(), request.getMaterialsJson());
        app.setOtherProof(merged);
        app.setStatus(CopyrightStatusUtil.STATUS_REVIEWING);
        app.setRemark("用户已提交补正材料，等待版权中心继续审核");
        app.setSupplementNotice(" ");
        // 截止保留便于追溯；状态已回 reviewing
        app.setUpdateTime(new Date());
        applicationService.updateCopyrightApplication(app);

        // Mock：告知代理已收补正（仅日志；真实环境在此调代理 API）
        log.info("Mock: 已将补正材料转交第三方 applyId={}, supplementId={}", applyId, supplementId);

        Map<String, Object> result = new HashMap<>();
        result.put("applyId", applyId);
        result.put("supplementId", supplementId);
        result.put("status", CopyrightStatusUtil.STATUS_REVIEWING);
        return result;
    }

    @Override
    public int closeOverdueSupplements()
    {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT s.supplement_id, s.apply_id FROM sys_copyright_supplement s "
                        + "WHERE s.status=? AND s.deadline_time < NOW()",
                SUPP_OPEN);
        int n = 0;
        for (Map<String, Object> row : rows)
        {
            Long supplementId = ((Number) row.get("supplement_id")).longValue();
            Long applyId = ((Number) row.get("apply_id")).longValue();
            jdbc.update("UPDATE sys_copyright_supplement SET status=?, update_time=NOW() WHERE supplement_id=? AND status=?",
                    SUPP_OVERDUE, supplementId, SUPP_OPEN);
            SysCopyrightApplication app = applicationService.selectCopyrightApplicationByApplyId(applyId);
            if (app != null && CopyrightStatusUtil.STATUS_NEED_SUPPLEMENT.equals(app.getStatus()))
            {
                app.setStatus(CopyrightStatusUtil.STATUS_SUPPLEMENT_OVERDUE);
                app.setRemark(CopyrightStatusUtil.getDefaultRemark(CopyrightStatusUtil.STATUS_SUPPLEMENT_OVERDUE));
                app.setUpdateTime(new Date());
                applicationService.updateCopyrightApplication(app);
                n++;
            }
        }
        return n;
    }

    @Override
    public List<Map<String, Object>> listMessagesByApplyId(Long applyId)
    {
        return jdbc.queryForList(
                "SELECT id AS messageId, external_message_id AS externalMessageId, message_type AS messageType, "
                        + "title, content, attachment_url AS attachmentUrl, deadline_time AS deadlineTime, "
                        + "process_status AS processStatus, create_time AS createTime "
                        + "FROM sys_copyright_third_party_message WHERE apply_id=? ORDER BY create_time DESC",
                applyId);
    }

    @Override
    public List<Map<String, Object>> listRecentMessages(Integer limit)
    {
        int lim = limit == null ? 50 : Math.min(Math.max(limit, 1), 200);
        return jdbc.queryForList(
                "SELECT m.id AS messageId, m.external_message_id AS externalMessageId, m.apply_id AS applyId, "
                        + "m.external_apply_no AS externalApplyNo, m.message_type AS messageType, "
                        + "m.title, m.content, m.attachment_url AS attachmentUrl, m.deadline_time AS deadlineTime, "
                        + "m.process_status AS processStatus, m.create_time AS createTime, "
                        + "a.apply_no AS applyNo "
                        + "FROM sys_copyright_third_party_message m "
                        + "LEFT JOIN sys_copyright_application a ON a.apply_id = m.apply_id "
                        + "ORDER BY m.create_time DESC LIMIT " + lim);
    }

    @Override
    public Map<String, Object> getOpenSupplement(Long applyId)
    {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT supplement_id AS supplementId, apply_id AS applyId, requirement_text AS requirementText, "
                        + "attachment_url AS attachmentUrl, deadline_time AS deadlineTime, status, "
                        + "materials_json AS materialsJson, user_remark AS userRemark, submit_time AS submitTime, "
                        + "create_time AS createTime "
                        + "FROM sys_copyright_supplement WHERE apply_id=? AND status=? "
                        + "ORDER BY supplement_id DESC LIMIT 1",
                applyId, SUPP_OPEN);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<Map<String, Object>> listSupplementsByApplyId(Long applyId)
    {
        return jdbc.queryForList(
                "SELECT supplement_id AS supplementId, apply_id AS applyId, requirement_text AS requirementText, "
                        + "attachment_url AS attachmentUrl, deadline_time AS deadlineTime, status, "
                        + "materials_json AS materialsJson, user_remark AS userRemark, submit_time AS submitTime, "
                        + "create_time AS createTime "
                        + "FROM sys_copyright_supplement WHERE apply_id=? ORDER BY supplement_id DESC",
                applyId);
    }

    private void handleSupplementRequired(
            SysCopyrightApplication application, Long messagePk,
            CopyrightThirdPartyMessageRequest request, Date deadline)
    {
        String status = application.getStatus();
        if (CopyrightStatusUtil.STATUS_CERTIFICATED.equals(status)
                || CopyrightStatusUtil.STATUS_REJECTED.equals(status))
        {
            throw new IllegalStateException("申请已结案，不能再进入补正");
        }
        // 关闭旧 open 补正单
        jdbc.update("UPDATE sys_copyright_supplement SET status=?, update_time=NOW() WHERE apply_id=? AND status=?",
                SUPP_CLOSED, application.getApplyId(), SUPP_OPEN);

        String requirement = StringUtils.isNotBlank(request.getContent())
                ? request.getContent()
                : "请按版权中心要求补充材料";
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO sys_copyright_supplement("
                            + "apply_id,request_message_id,requirement_text,attachment_url,deadline_time,status,create_time,update_time) "
                            + "VALUES(?,?,?,?,?,?,NOW(),NOW())",
                    new String[] {"supplement_id"});
            ps.setLong(1, application.getApplyId());
            ps.setLong(2, messagePk);
            ps.setString(3, requirement);
            ps.setString(4, request.getAttachmentUrl());
            ps.setTimestamp(5, new java.sql.Timestamp(deadline.getTime()));
            ps.setString(6, SUPP_OPEN);
            return ps;
        });

        application.setStatus(CopyrightStatusUtil.STATUS_NEED_SUPPLEMENT);
        application.setSupplementDeadline(deadline);
        application.setSupplementNotice(requirement.length() > 500 ? requirement.substring(0, 500) : requirement);
        application.setRemark(CopyrightStatusUtil.getDefaultRemark(CopyrightStatusUtil.STATUS_NEED_SUPPLEMENT));
        application.setUpdateTime(new Date());
        applicationService.updateCopyrightApplication(application);
    }

    private void handleCertificated(SysCopyrightApplication application, CopyrightThirdPartyMessageRequest request)
    {
        application.setStatus(CopyrightStatusUtil.STATUS_CERTIFICATED);
        if (StringUtils.isNotBlank(request.getCertNo()))
        {
            application.setCertNo(request.getCertNo());
        }
        if (StringUtils.isNotBlank(request.getCertUrl()))
        {
            application.setCertUrl(request.getCertUrl());
        }
        application.setCertTime(new Date());
        application.setRemark(CopyrightStatusUtil.getDefaultRemark(CopyrightStatusUtil.STATUS_CERTIFICATED));
        application.setSupplementNotice("");
        application.setUpdateTime(new Date());
        applicationService.updateCopyrightApplication(application);
        jdbc.update("UPDATE sys_copyright_supplement SET status=?, update_time=NOW() WHERE apply_id=? AND status IN (?,?)",
                SUPP_CLOSED, application.getApplyId(), SUPP_OPEN, SUPP_OVERDUE);
    }

    private void handleRejected(SysCopyrightApplication application, CopyrightThirdPartyMessageRequest request)
    {
        application.setStatus(CopyrightStatusUtil.STATUS_REJECTED);
        String reason = StringUtils.isNotBlank(request.getRejectReason())
                ? request.getRejectReason()
                : (StringUtils.isNotBlank(request.getContent()) ? request.getContent() : "不予登记");
        application.setRejectReason(reason);
        application.setRemark("审核不通过：" + reason);
        application.setSupplementNotice("");
        application.setUpdateTime(new Date());
        applicationService.updateCopyrightApplication(application);
        jdbc.update("UPDATE sys_copyright_supplement SET status=?, update_time=NOW() WHERE apply_id=? AND status IN (?,?)",
                SUPP_CLOSED, application.getApplyId(), SUPP_OPEN, SUPP_OVERDUE);
    }

    private SysCopyrightApplication resolveApplication(Long applyId, String externalApplyNo)
    {
        if (applyId != null)
        {
            return applicationService.selectCopyrightApplicationByApplyId(applyId);
        }
        if (StringUtils.isNotBlank(externalApplyNo))
        {
            SysCopyrightApplication query = new SysCopyrightApplication();
            query.setExternalNo(externalApplyNo);
            List<SysCopyrightApplication> list = applicationService.selectCopyrightApplicationList(query);
            return list.isEmpty() ? null : list.get(0);
        }
        return null;
    }

    private Long insertMessage(CopyrightThirdPartyMessageRequest request, Long applyId, Date deadline)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO sys_copyright_third_party_message("
                            + "external_message_id,apply_id,external_apply_no,message_type,title,content,"
                            + "attachment_url,requirement_json,deadline_time,payload_json,process_status,create_time,update_time) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?, 'pending',NOW(),NOW())",
                    new String[] {"id"});
            ps.setString(1, request.getExternalMessageId());
            if (applyId == null)
            {
                ps.setObject(2, null);
            }
            else
            {
                ps.setLong(2, applyId);
            }
            ps.setString(3, request.getExternalApplyNo());
            ps.setString(4, request.getMessageType());
            ps.setString(5, request.getTitle());
            ps.setString(6, request.getContent());
            ps.setString(7, request.getAttachmentUrl());
            ps.setString(8, request.getRequirementJson());
            if (deadline == null)
            {
                ps.setObject(9, null);
            }
            else
            {
                ps.setTimestamp(9, new java.sql.Timestamp(deadline.getTime()));
            }
            ps.setString(10, request.getPayloadJson());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null)
        {
            throw new IllegalStateException("保存第三方消息失败");
        }
        return key.longValue();
    }

    private void markProcessed(Long messageId, String status, String remark)
    {
        jdbc.update(
                "UPDATE sys_copyright_third_party_message SET process_status=?, process_remark=?, "
                        + "processed_time=NOW(), update_time=NOW() WHERE id=?",
                status, remark, messageId);
    }

    private Date parseDeadline(String text)
    {
        if (StringUtils.isBlank(text))
        {
            return null;
        }
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};
        for (String p : patterns)
        {
            try
            {
                return new SimpleDateFormat(p).parse(text.trim());
            }
            catch (ParseException ignored)
            {
            }
        }
        throw new IllegalArgumentException("deadlineTime格式无效");
    }

    private Date plusDays(Date from, int days)
    {
        Calendar cal = Calendar.getInstance();
        cal.setTime(from);
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }

    private String mergeMaterials(String existing, String incoming)
    {
        if (StringUtils.isBlank(existing))
        {
            return incoming;
        }
        if (StringUtils.isBlank(incoming))
        {
            return existing;
        }
        return existing + "\n--- supplement ---\n" + incoming;
    }

    /** 待补正状态但缺 open 补正单时补建。 */
    private Map<String, Object> ensureOpenSupplement(SysCopyrightApplication app)
    {
        if (app == null || app.getApplyId() == null)
        {
            return null;
        }
        Date deadline = app.getSupplementDeadline() != null
                ? app.getSupplementDeadline()
                : plusDays(new Date(), CopyrightStatusUtil.SUPPLEMENT_DEADLINE_DAYS);
        String requirement = StringUtils.isNotBlank(app.getSupplementNotice())
                ? app.getSupplementNotice()
                : "请按版权中心要求补充材料";
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO sys_copyright_supplement("
                            + "apply_id,request_message_id,requirement_text,attachment_url,deadline_time,status,create_time,update_time) "
                            + "VALUES(?,?,?,?,?,?,NOW(),NOW())",
                    new String[] {"supplement_id"});
            ps.setLong(1, app.getApplyId());
            ps.setObject(2, null);
            ps.setString(3, requirement);
            ps.setString(4, null);
            ps.setTimestamp(5, new Timestamp(deadline.getTime()));
            ps.setString(6, SUPP_OPEN);
            return ps;
        });
        log.warn("已为待补正申请补建 open 补正单 applyId={}", app.getApplyId());
        return getOpenSupplement(app.getApplyId());
    }

    private static Object mapGet(Map<String, Object> map, String... keys)
    {
        if (map == null || keys == null)
        {
            return null;
        }
        for (String key : keys)
        {
            if (map.containsKey(key) && map.get(key) != null)
            {
                return map.get(key);
            }
        }
        // MySQL 驱动偶发返回全小写/全大写列名
        for (Map.Entry<String, Object> e : map.entrySet())
        {
            if (e.getKey() == null || e.getValue() == null)
            {
                continue;
            }
            for (String key : keys)
            {
                if (e.getKey().equalsIgnoreCase(key))
                {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private static Long toLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        try
        {
            return Long.parseLong(String.valueOf(value).trim());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static Date toDate(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Date)
        {
            return (Date) value;
        }
        if (value instanceof LocalDateTime)
        {
            return Date.from(((LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant());
        }
        if (value instanceof Instant)
        {
            return Date.from((Instant) value);
        }
        if (value instanceof Number)
        {
            return new Date(((Number) value).longValue());
        }
        String text = String.valueOf(value).trim();
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd"};
        for (String p : patterns)
        {
            try
            {
                return new SimpleDateFormat(p).parse(text);
            }
            catch (ParseException ignored)
            {
            }
        }
        return null;
    }
}
