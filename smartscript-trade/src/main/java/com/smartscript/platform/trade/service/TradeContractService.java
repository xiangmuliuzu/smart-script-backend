package com.smartscript.platform.trade.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.uuid.Seq;
import com.smartscript.platform.trade.support.ContractHtmlSupport;
import com.smartscript.platform.trade.support.ContractPdfRenderer;
import com.smartscript.platform.trade.domain.SysOrderStatusLog;
import com.smartscript.platform.trade.mapper.SysOrderStatusLogMapper;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;
import com.smartscript.platform.user.security.AppIdentityContext;
import com.smartscript.platform.user.service.AppRealNameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 交易域合同服务（APP 端）。
 * <p>
 * 职责：
 * <ul>
 *   <li>订单生成时自动创建合同（status=pending）</li>
 *   <li>APP 端查看合同详情（含 HTML 内容、双方签署状态、签名/印章图片）</li>
 *   <li>APP 端签署合同（手写签名图片 + 印章图片，至少传一个）</li>
 *   <li>双方签署完成后合成最终 PDF</li>
 * </ul>
 * <p>
 * 签署规则：手写签名或盖章满足其一即可，也支持两者都有。
 */
@Service
public class TradeContractService
{
    private static final Logger log = LoggerFactory.getLogger(TradeContractService.class);

    private static final String CONTRACT_SELECT =
            "SELECT c.contract_id AS contractId, c.contract_no AS contractNo, "
            + "c.contract_type AS contractType, c.template_id AS templateId, c.amount AS amount, "
            + "c.order_id AS orderId, o.order_no AS orderNo, c.buyer_id AS buyerId, ub.nick_name AS buyerName, "
            + "ub.real_name AS buyerRealName, "
            + "c.seller_id AS sellerId, us.nick_name AS sellerName, "
            + "us.real_name AS sellerRealName, "
            + "c.buyer_sign_status AS buyerSignStatus, c.seller_sign_status AS sellerSignStatus, "
            + "c.buyer_sign_time AS buyerSignTime, c.seller_sign_time AS sellerSignTime, "
            + "c.buyer_signature_image AS buyerSignatureImage, c.buyer_seal_image AS buyerSealImage, "
            + "c.seller_signature_image AS sellerSignatureImage, c.seller_seal_image AS sellerSealImage, "
            + "c.signed_pdf_url AS signedPdfUrl, "
            + "c.source_type AS sourceType, c.source_pdf_url AS sourcePdfUrl, c.pdf_page_count AS pageCount, "
            + "c.buyer_sign_pos AS buyerSignPos, c.seller_sign_pos AS sellerSignPos, "
            + "c.status AS status, c.contract_content AS content, c.clauses_text AS clausesText, "
            + "w.title AS workTitle, o.license_type AS licenseType, "
            + "DATE_FORMAT(c.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt "
            + "FROM sys_contract c JOIN sys_order o ON o.order_id = c.order_id "
            + "LEFT JOIN sys_user ub ON ub.user_id = c.buyer_id "
            + "LEFT JOIN sys_user us ON us.user_id = c.seller_id "
            + "LEFT JOIN sys_work w ON w.work_id = o.work_id ";

    /**
     * 合同列表查询 FROM 片段（不含 contract_content 大字段，额外带作品封面）。
     * <p>单列供 COUNT 与列表共用，确保 keyword 关联到的
     * {@code o}/{@code w}/{@code ub}/{@code us} 表在两处都存在（否则 count 会因缺 JOIN 报列不存在）。
     */
    private static final String CONTRACT_LIST_FROM =
            " FROM sys_contract c JOIN sys_order o ON o.order_id = c.order_id "
            + " LEFT JOIN sys_user ub ON ub.user_id = c.buyer_id "
            + " LEFT JOIN sys_user us ON us.user_id = c.seller_id "
            + " LEFT JOIN sys_work w ON w.work_id = o.work_id ";

    private static final String CONTRACT_LIST_SELECT =
            "SELECT c.contract_id AS contractId, c.contract_no AS contractNo, c.amount AS amount, "
            + "c.order_id AS orderId, o.order_no AS orderNo, c.buyer_id AS buyerId, ub.nick_name AS buyerName, "
            + "c.seller_id AS sellerId, us.nick_name AS sellerName, "
            + "c.buyer_sign_status AS buyerSignStatus, c.seller_sign_status AS sellerSignStatus, "
            + "c.buyer_sign_time AS buyerSignTime, c.seller_sign_time AS sellerSignTime, "
            + "c.signed_pdf_url AS signedPdfUrl, c.status AS status, "
            + "c.source_type AS sourceType, c.source_pdf_url AS sourcePdfUrl, c.pdf_page_count AS pageCount, "
            + "c.template_id AS templateId, "
            + "w.title AS workTitle, w.cover AS workCover, o.license_type AS licenseType, "
            + "DATE_FORMAT(c.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt "
            + CONTRACT_LIST_FROM;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContractPdfRenderer pdfRenderer;

    /** 实名认证状态查询（A5）：签署前置校验用。 */
    @Autowired
    private AppUserCenterMapper userCenterMapper;

    /** 交易全生命周期站内通知统一出口。 */
    @Autowired
    private TradeNotifyService notifyService;

    @Autowired
    private SysOrderStatusLogMapper orderStatusLogMapper;

    /**
     * 按订单 ID 取合同详情。
     */
    public Map<String, Object> getByOrderId(Long orderId)
    {
        try
        {
            return decorateSource(enrichWithSignatures(jdbc.queryForMap(CONTRACT_SELECT + " WHERE c.order_id = ?", orderId)));
        }
        catch (org.springframework.dao.EmptyResultDataAccessException e)
        {
            return null;
        }
    }

    /**
     * 按合同 ID 取合同详情。
     */
    public Map<String, Object> getById(Long contractId)
    {
        try
        {
            return decorateSource(enrichWithSignatures(jdbc.queryForMap(CONTRACT_SELECT + " WHERE c.contract_id = ?", contractId)));
        }
        catch (org.springframework.dao.EmptyResultDataAccessException e)
        {
            throw new IllegalArgumentException("合同不存在");
        }
    }

    /**
     * 合同列表（PC 门户 / APP 共用）。
     * <p>
     * role=seller 取我作为卖方的合同，role=buyer 取我作为买方的合同，为空取两者并集；
     * status 可选（pending / partial_signed / completed 等）。
     *
     * @param userId   当前用户 ID
     * @param role     买卖角色过滤（seller / buyer / 空）
     * @param status   合同状态过滤（可选）
     * @param keyword  关键词模糊搜索（合同号/订单号/作品名/买卖双方昵称，可选）
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return {total, list}
     */
    public Map<String, Object> listContracts(Long userId, String role, String status, String keyword, int pageNum, int pageSize)
    {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if ("seller".equalsIgnoreCase(role))
        {
            where.append(" AND c.seller_id = ? ");
            args.add(userId);
        }
        else if ("buyer".equalsIgnoreCase(role))
        {
            where.append(" AND c.buyer_id = ? ");
            args.add(userId);
        }
        else
        {
            where.append(" AND (c.buyer_id = ? OR c.seller_id = ?) ");
            args.add(userId);
            args.add(userId);
        }
        if (status != null && !status.isBlank())
        {
            where.append(" AND c.status = ? ");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank())
        {
            // 转义 LIKE 通配符，避免用户输入 % / _ 改变匹配语义
            String escaped = keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            String match = "%" + escaped + "%";
            where.append(" AND (c.contract_no LIKE ? OR o.order_no LIKE ? OR w.title LIKE ? OR ub.nick_name LIKE ? OR us.nick_name LIKE ?) ");
            args.add(match);
            args.add(match);
            args.add(match);
            args.add(match);
            args.add(match);
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + CONTRACT_LIST_FROM + where, Long.class, args.toArray());
        int size = Math.max(1, Math.min(pageSize, 100));
        int offset = Math.max(0, pageNum - 1) * size;
        java.util.List<Object> listArgs = new java.util.ArrayList<>(args);
        listArgs.add(size);
        listArgs.add(offset);
        java.util.List<Map<String, Object>> list = jdbc.queryForList(
                CONTRACT_LIST_SELECT + where + " ORDER BY c.created_at DESC LIMIT ? OFFSET ?", listArgs.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total == null ? 0L : total);
        result.put("list", list);
        return result;
    }

    /**
     * 详情出口实时注入双方签名/印章图：存库 content 是生成时固化的版本，
     * 签署产生的图片在合同列上；返回前把占位符替换为 <img>，
     * 使 APP 端 WebView 与任意详情消费方即时看到已签署的签字盖章。
     */
    private Map<String, Object> enrichWithSignatures(Map<String, Object> detail)
    {
        Object content = detail.get("content");
        if (content instanceof String && !((String) content).isBlank())
        {
            detail.put("content", ContractHtmlSupport.injectSignatures((String) content,
                    (String) detail.get("buyerSignatureImage"), (String) detail.get("buyerSealImage"),
                    (String) detail.get("sellerSignatureImage"), (String) detail.get("sellerSealImage"),
                    str(detail.get("buyerSignTime")), str(detail.get("sellerSignTime"))));
        }
        return detail;
    }

    /**
     * 订单生成时自动创建合同。
     * <p>
     * 根据订单信息生成合同 HTML 内容，插入 sys_contract，回写 sys_order.contract_id。
     *
     * @param orderId     订单 ID
     * @param orderNo     订单编号
     * @param buyerId     买方 ID
     * @param sellerId    卖方 ID
     * @param buyerName   买方昵称
     * @param sellerName  卖方昵称
     * @param workTitle   作品名称
     * @param amount      合同金额（元）
     * @param licenseType 授权类型
     * @return 合同 ID
     */
    @Transactional
    public Long autoGenerateContract(Long orderId, String orderNo, Long buyerId, Long sellerId,
                                     String buyerName, String sellerName, String workTitle,
                                     java.math.BigDecimal amount, String licenseType)
    {
        // 检查是否已有合同
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_contract WHERE order_id = ? AND status <> 'cancelled'",
                Integer.class, orderId);
        if (existing != null && existing > 0)
        {
            log.info("订单 {} 已存在合同，跳过自动生成", orderId);
            Long existingId = jdbc.queryForObject(
                    "SELECT contract_id FROM sys_contract WHERE order_id = ? AND status <> 'cancelled' LIMIT 1",
                    Long.class, orderId);
            return existingId;
        }

        String contractNo = "CT" + Seq.getId();
        String templateId = "TPL_COPYRIGHT_LICENSE";
        String contractType = "license";
        // 默认正文条款纯文本：随合同持久化到 clauses_text，作为可编辑区初值与重渲染回喂源
        String clausesText = ContractHtmlSupport.defaultClausesText(templateId, workTitle, licenseType, amount);
        // 署名优先用实名姓名快照：下单时可能已实名，未实名的仍回退昵称（签署时点另有双签重渲染兜底）
        String html = ContractHtmlSupport.buildContractHtml(contractNo, orderNo, workTitle,
                displayName(buyerId, buyerName), displayName(sellerId, sellerName),
                amount, licenseType, templateId, clausesText);
        
        jdbc.update("INSERT INTO sys_contract (contract_no, contract_type, amount, order_id, buyer_id, seller_id, "
                        + "template_id, contract_content, clauses_text, contract_url, buyer_sign_status, seller_sign_status, status, create_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, '', 0, 0, 'pending', ?)",
                contractNo, contractType, amount, orderId, buyerId, sellerId,
                templateId, html, clausesText, "system");

        Long contractId = jdbc.queryForObject(
                "SELECT contract_id FROM sys_contract WHERE contract_no = ?", Long.class, contractNo);

        jdbc.update("UPDATE sys_order SET contract_id = ?, update_time = NOW() WHERE order_id = ?",
                contractId, orderId);

        log.info("订单 {} 自动生成合同 {} (ID={})", orderId, contractNo, contractId);
        return contractId;
    }

    /**
     * APP 端签署合同。
     * <p>
     * 根据当前用户判定角色（buyer/seller），保存签名/印章图片，更新签署状态。
     *
     * @param contractId      合同 ID
     * @param signatureImage  手写签名图片（可选）
     * @param sealImage       印章图片（可选）
     * @return 签署后的合同状态
     */
    public Map<String, Object> sign(Long contractId, MultipartFile signatureImage, MultipartFile sealImage)
    {
        return sign(contractId, signatureImage, sealImage, null);
    }

    @Transactional
    public Map<String, Object> sign(Long contractId, MultipartFile signatureImage, MultipartFile sealImage,
                                    String placementsJson)
    {
        // 至少传一个
        boolean hasSignature = signatureImage != null && !signatureImage.isEmpty();
        boolean hasSeal = sealImage != null && !sealImage.isEmpty();
        if (!hasSignature && !hasSeal)
        {
            throw new IllegalArgumentException("手写签名和印章至少需要提供一项");
        }

        // 取合同并锁定
        Map<String, Object> contract = jdbc.queryForMap(
                "SELECT contract_id AS contractId, order_id AS orderId, buyer_id AS buyerId, seller_id AS sellerId, "
                + "buyer_sign_status AS buyerSignStatus, seller_sign_status AS sellerSignStatus, "
                + "source_type AS sourceType, pdf_page_count AS pageCount, "
                + "status FROM sys_contract WHERE contract_id = ? FOR UPDATE", contractId);

        String currentStatus = String.valueOf(contract.get("status"));
        if (!"pending".equals(currentStatus) && !"partial_signed".equals(currentStatus))
        {
            throw new IllegalStateException("当前合同状态不可签署");
        }

        // 判定当前用户角色
        Long currentUserId = getCurrentUserId();
        Long buyerId = ((Number) contract.get("buyerId")).longValue();
        Long sellerId = ((Number) contract.get("sellerId")).longValue();

        String party;
        if (currentUserId.equals(buyerId))
        {
            party = "buyer";
        }
        else if (currentUserId.equals(sellerId))
        {
            party = "seller";
        }
        else
        {
            throw new IllegalStateException("您不是该合同的买方或卖方");
        }

        // 检查该方是否已签署
        int currentSignStatus = ((Number) contract.get(party + "SignStatus")).intValue();
        if (currentSignStatus == 1)
        {
            throw new IllegalStateException("您已签署过该合同");
        }

        // 实名前置：买卖双方均需已完成实名认证（APPROVED）方可签字盖章，
        // 保证合同主体可追溯；报错指明缺失方，便于用户引导补办
        String buyerRealNameStatus = userCenterMapper.selectLatestRealNameStatus(buyerId);
        String sellerRealNameStatus = userCenterMapper.selectLatestRealNameStatus(sellerId);
        if (!AppRealNameService.STATUS_APPROVED.equals(buyerRealNameStatus))
        {
            throw new IllegalStateException("买卖双方均需完成实名认证后才能签署合同，当前买方尚未实名");
        }
        if (!AppRealNameService.STATUS_APPROVED.equals(sellerRealNameStatus))
        {
            throw new IllegalStateException("买卖双方均需完成实名认证后才能签署合同，当前卖方尚未实名");
        }

        // 上传件签署：需前端提交的签章定位 JSON，按页面比例落库，完成时据此盖章合成；
        // 模板件忽略 placements（由系统按固定占位符自动落位），sign_pos 保持为空。
        boolean uploaded = "uploaded".equals(str(contract.get("sourceType")));
        String partyPos = uploaded ? normalizePlacements(placementsJson, contract.get("pageCount")) : null;

        // 上传件落章可行性前置校验（刻意置于保存图片之前）：定位里出现的 kind 必须有对应图片且可被 PDFBox 解码，
        // 否则完成时 appendStamps 会静默跳章、导致“已签署却缺章/无 PDF”而无感知；
        // 在写磁盘前拦截，可避免校验失败回滚 DB 后在磁盘上遗留孤儿图片文件。
        if (uploaded)
        {
            boolean needSeal = false;
            boolean needSignature = false;
            try
            {
                JsonNode pnode = PLACEMENT_MAPPER.readTree(partyPos);
                if (pnode != null && pnode.isArray())
                {
                    for (JsonNode n : pnode)
                    {
                        String k = n.path("kind").asText("");
                        if ("seal".equals(k))
                        {
                            needSeal = true;
                        }
                        else if ("signature".equals(k))
                        {
                            needSignature = true;
                        }
                    }
                }
            }
            catch (Exception e)
            {
                throw new IllegalArgumentException("签章定位信息解析失败");
            }
            if (needSeal && !hasSeal)
            {
                throw new IllegalArgumentException("已定位印章但未提供印章图片");
            }
            if (needSignature && !hasSignature)
            {
                throw new IllegalArgumentException("已定位签名但未提供签名图片");
            }
            if (needSeal && !canDecodeImage(sealImage))
            {
                throw new IllegalArgumentException("印章图片无法解析用于盖章，请改用 PNG/JPG 格式");
            }
            if (needSignature && !canDecodeImage(signatureImage))
            {
                throw new IllegalArgumentException("签名图片无法解析用于盖章，请重新绘制签名");
            }
        }

        // 保存图片
        String signatureUrl = null;
        String sealUrl = null;
        try
        {
            if (hasSignature)
            {
                signatureUrl = saveSignImage(contractId, party + "_signature", signatureImage);
            }
            if (hasSeal)
            {
                sealUrl = saveSignImage(contractId, party + "_seal", sealImage);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("保存图片失败: " + e.getMessage());
        }

        // 判断双方是否都已签署
        boolean buyerSigned = "buyer".equals(party) || ((Number) contract.get("buyerSignStatus")).intValue() == 1;
        boolean sellerSigned = "seller".equals(party) || ((Number) contract.get("sellerSignStatus")).intValue() == 1;
        String newStatus = (buyerSigned && sellerSigned) ? "completed" : "partial_signed";

        // 固定参数化 SQL：首次签署时签名/印章列本为空，直接整列覆写，避免动态拼接出语法错误；
        // sign_status = 0 乐观条件与行锁双重保险，防止并发抢占后重复写入。
        int changed;
        if ("buyer".equals(party))
        {
            changed = jdbc.update("UPDATE sys_contract SET buyer_sign_status = 1, buyer_sign_time = NOW(), "
                    + "buyer_signature_image = ?, buyer_seal_image = ?, buyer_sign_pos = ?, update_time = NOW(), status = ? "
                    + "WHERE contract_id = ? AND buyer_sign_status = 0",
                    signatureUrl, sealUrl, partyPos, newStatus, contractId);
        }
        else
        {
            changed = jdbc.update("UPDATE sys_contract SET seller_sign_status = 1, seller_sign_time = NOW(), "
                    + "seller_signature_image = ?, seller_seal_image = ?, seller_sign_pos = ?, update_time = NOW(), status = ? "
                    + "WHERE contract_id = ? AND seller_sign_status = 0",
                    signatureUrl, sealUrl, partyPos, newStatus, contractId);
        }
        if (changed != 1)
        {
            throw new IllegalStateException("合同状态已变化，请刷新后重试");
        }

        // 每次签署后立即将签名/印章持久化到 contract_content，使 DB 中的 HTML 始终反映最新签署状态
        persistSignaturesToContent(contractId);

        // 如果双方都签完，合成可下载 PDF：
        // 上传件 → 在源 PDF 上按双方拖拽坐标盖章；模板件 → 按实名姓名重渲染 HTML 正文并渲染 PDF。
        // 生成失败不影响签署事务，可由管理端补偿重建。
        if ("completed".equals(newStatus))
        {
            try
            {
                if (uploaded)
                {
                    stampedSourcePdf(contractId);
                }
                else
                {
                    rerenderContentWithRealNames(contractId);
                    generateSignedPdf(contractId);
                }
            }
            catch (Exception e)
            {
                log.error("生成签署后 PDF 失败（签署状态不受影响），contractId={}", contractId, e);
            }
        }

        // 签署结果通知：双方签满→通知买方付款；仅单方签完→通知另一方尽快签署
        Long orderId = ((Number) contract.get("orderId")).longValue();
        String orderNo = null;
        try
        {
            orderNo = jdbc.queryForObject("SELECT order_no FROM sys_order WHERE order_id = ?", String.class, orderId);
        }
        catch (Exception ignore)
        {
            // 编号仅用于通知文案展示，取不到不影响通知投递
        }
        if ("completed".equals(newStatus))
        {
            // 合同双签完成 → 驱动订单 confirmed 自动进入 paying（取代原「买方付款进托管」步骤）
            try
            {
                int orderMoved = jdbc.update("UPDATE sys_order SET status = 'paying', update_time = NOW() "
                        + "WHERE order_id = ? AND status = 'confirmed'", orderId);
                if (orderMoved == 1)
                {
                    SysOrderStatusLog olog = new SysOrderStatusLog();
                    olog.setOrderId(orderId);
                    olog.setFromStatus("confirmed");
                    olog.setToStatus("paying");
                    olog.setOperatorId(currentUserId);
                    olog.setOperatorRole("system");
                    olog.setRemark("双方签署完成，订单进入付款");
                    olog.setCreateBy(String.valueOf(currentUserId));
                    olog.setCreateTime(new Date());
                    orderStatusLogMapper.insertLog(olog);
                }
            }
            catch (Exception e)
            {
                log.error("合同 {} 双签完成但推进订单至 paying 失败，orderId={}", contractId, orderId, e);
            }
            notifyService.notifyContractCompleted(orderId, orderNo, buyerId);
        }
        else
        {
            Long counterpartId = "buyer".equals(party) ? sellerId : buyerId;
            notifyService.notifyContractSignedByParty(contractId, orderNo, counterpartId);
        }

        return getById(contractId);
    }

    /**
     * 双签完成时用实名姓名快照重渲染正文后存库：
     * 合同可能在双方实名之前生成（正文回退昵称），签署前置已保证此刻双方均已实名，
     * 重渲染保证存库正文与最终 PDF 上的甲乙方为真实姓名。
     * <p>
     * 重渲染后保留已有的签名/印章图片，避免丢失先签署方的视觉内容。
     */
    private void rerenderContentWithRealNames(Long contractId)
    {
        Map<String, Object> detail = jdbc.queryForMap(CONTRACT_SELECT + " WHERE c.contract_id = ?", contractId);
        Object licenseType = detail.get("licenseType");
        String html = ContractHtmlSupport.buildContractHtml(
                str(detail.get("contractNo")), str(detail.get("orderNo")), str(detail.get("workTitle")),
                displayNameOf(detail.get("buyerRealName"), detail.get("buyerName")),
                displayNameOf(detail.get("sellerRealName"), detail.get("sellerName")),
                (java.math.BigDecimal) detail.get("amount"),
                licenseType == null ? null : String.valueOf(licenseType),
                str(detail.get("templateId")),
                (String) detail.get("clausesText"));
        // 重渲染后注入已有的签名/印章，保证实名重渲染不丢失签署内容
        html = ContractHtmlSupport.injectSignatures(html,
                (String) detail.get("buyerSignatureImage"), (String) detail.get("buyerSealImage"),
                (String) detail.get("sellerSignatureImage"), (String) detail.get("sellerSealImage"),
                str(detail.get("buyerSignTime")), str(detail.get("sellerSignTime")));
        jdbc.update("UPDATE sys_contract SET contract_content = ?, update_time = NOW() WHERE contract_id = ?",
                html, contractId);
        log.info("合同 {} 双签完成，已按实名姓名重渲染正文（含签名/印章持久化）", contractId);
    }

    /**
     * 每次签署后将签名/印章图片注入 contract_content 并回写数据库。
     * <p>
     * 保证 contract_content 始终是"活文档"——即使服务重启、API 不再动态注入，
     * WebView 加载 content 也能直接看到已签署的签名、印章与签署时间。
     */
    private void persistSignaturesToContent(Long contractId)
    {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT contract_content AS content, "
                + "buyer_signature_image AS buyerSignatureImage, buyer_seal_image AS buyerSealImage, "
                + "seller_signature_image AS sellerSignatureImage, seller_seal_image AS sellerSealImage, "
                + "buyer_sign_time AS buyerSignTime, seller_sign_time AS sellerSignTime "
                + "FROM sys_contract WHERE contract_id = ?", contractId);
        String html = String.valueOf(row.get("content"));
        // 上传件 contract_content 为 NULL（走源 PDF 落章，无 HTML 正文）：
        // String.valueOf(null) 会得到字面量 "null"，若继续回写将污染正文并导致重建出错误 PDF，故直接跳过。
        if (row.get("content") == null || html.isBlank())
        {
            return;
        }
        String enriched = ContractHtmlSupport.injectSignatures(html,
                (String) row.get("buyerSignatureImage"), (String) row.get("buyerSealImage"),
                (String) row.get("sellerSignatureImage"), (String) row.get("sellerSealImage"),
                str(row.get("buyerSignTime")), str(row.get("sellerSignTime")));
        jdbc.update("UPDATE sys_contract SET contract_content = ?, update_time = NOW() WHERE contract_id = ?",
                enriched, contractId);
        log.debug("合同 {} 签名/印章已持久化到 contract_content", contractId);
    }

    /** 合同署名：实名通过者用明文姓名快照，否则回退昵称。 */
    private String displayName(Long userId, String fallback)
    {
        try
        {
            String realName = jdbc.queryForObject(
                    "SELECT COALESCE(NULLIF(real_name, ''), nick_name) FROM sys_user WHERE user_id = ?",
                    String.class, userId);
            return realName == null || realName.isBlank() ? fallback : realName;
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    /** 显示姓名：实名快照优先，回退昵称。 */
    private static String displayNameOf(Object realName, Object fallback)
    {
        String value = realName == null ? "" : String.valueOf(realName).trim();
        return value.isEmpty() ? String.valueOf(fallback) : value;
    }

    /** null 安全转字符串。 */
    private static String str(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 保存签名/印章图片到服务器。
     */
    private String saveSignImage(Long contractId, String prefix, MultipartFile file) throws IOException
    {
        if (file == null || file.isEmpty())
        {
            throw new IllegalArgumentException("图片不能为空");
        }
        if (file.getSize() > MAX_SIGN_IMAGE_BYTES)
        {
            throw new IllegalArgumentException("图片大小不能超过 5MB");
        }
        String ext = detectImageExtension(file);
        if (ext == null)
        {
            throw new IllegalArgumentException("印章/签名图片仅支持 PNG/JPG/BMP/GIF/WebP 格式");
        }
        // 保存到若依 profile 目录（uploadPath/contract/{id}），经 /profile/** 静态映射对外可访问
        // 注意：MultipartFile.transferTo 对相对路径会基于 Tomcat 临时目录解析，必须转绝对路径
        String uploadDir = RuoYiConfig.getProfile() + "/contract/" + contractId;
        Path dirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(dirPath))
        {
            Files.createDirectories(dirPath);
        }

        // 文件名不使用用户提供的原始文件名，扩展名由魔数校验推导
        String fileName = prefix + "_" + System.currentTimeMillis() + ext;
        Path filePath = dirPath.resolve(fileName);
        file.transferTo(filePath.toFile());

        return Constants.RESOURCE_PREFIX + "/contract/" + contractId + "/" + fileName;
    }

    /**
     * 校验图片可被 ImageIO（与 PDFBox 同源解码）读回。
     * <p>用于上传件签署前置拦截 WebP 等 JDK ImageIO 无解码器、到落章才会静默失败的格式。
     */
    private static boolean canDecodeImage(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            return false;
        }
        try (java.io.InputStream in = file.getInputStream())
        {
            return javax.imageio.ImageIO.read(in) != null;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /** 签名图片大小上限：5MB */
    private static final long MAX_SIGN_IMAGE_BYTES = 5L * 1024 * 1024;

    /**
     * 按文件头魔数校验图片类型并推导安全扩展名，不信任用户上传的原始文件名。
     *
     * 白名单与印章上传（AppContentFileUploadService）保持一致：bmp/gif/jpg/jpeg/png/webp，
     * 避免「上传得进、签署用不了」的口径分叉。
     *
     * @return 白名单内的扩展名（.png/.jpg/.webp/.bmp/.gif），不匹配返回 null
     */
    private String detectImageExtension(MultipartFile file) throws IOException
    {
        byte[] head = new byte[12];
        int read;
        try (java.io.InputStream in = file.getInputStream())
        {
            read = in.readNBytes(head, 0, head.length);
            if (read < 4)
            {
                return null;
            }
        }
        if ((head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47)
        {
            return ".png";
        }
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF)
        {
            return ".jpg";
        }
        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && read >= 12 && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P')
        {
            return ".webp";
        }
        // BMP：文件头两字节 'B' 'M'
        if (head[0] == 'B' && head[1] == 'M')
        {
            return ".bmp";
        }
        // GIF：文件头四字节 'G' 'I' 'F' '8'（GIF87a / GIF89a）
        if (head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8')
        {
            return ".gif";
        }
        return null;
    }

    /**
     * 重建合同签署 PDF（供管理端补偿调用，异常上抛以便调用方感知失败）。
     */
    public void regenerateSignedPdf(Long contractId) throws IOException
    {
        Map<String, Object> status = jdbc.queryForMap(
                "SELECT status, source_type AS sourceType FROM sys_contract WHERE contract_id = ?", contractId);
        String current = String.valueOf(status.get("status"));
        if (!"completed".equals(current) && !"archived".equals(current))
        {
            throw new IllegalStateException("仅已完成签署的合同可以重建 PDF（当前状态：" + current + "）");
        }
        // 按来源分叉：上传件必须在源 PDF 上重落章（stampedSourcePdf），
        // 否则会用 HTML 渲染结果按同名产物覆盖掉双方的定稿盖章件。
        if ("uploaded".equals(String.valueOf(status.get("sourceType"))))
        {
            stampedSourcePdf(contractId);
        }
        else
        {
            generateSignedPdf(contractId);
        }
    }

    /**
     * 双方签署完成后合成最终 PDF 并回写 signed_pdf_url。
     * <p>
     * 前置：persistSignaturesToContent / rerenderContentWithRealNames 已保证
     * contract_content 中已注入签名/印章图片与签署时间，此处只需解析图片路径后渲染 PDF。
     */
    private void generateSignedPdf(Long contractId) throws IOException
    {
        Map<String, Object> contract = jdbc.queryForMap(
                "SELECT contract_no AS contractNo, contract_content AS content FROM sys_contract WHERE contract_id = ?",
                contractId);

        String html = String.valueOf(contract.get("content"));

        // PDF 渲染无 baseUri，将 /profile/ 相对路径转换为本地文件绝对路径
        html = ContractHtmlSupport.resolveProfileImages(html);

        // 使用 openhtmltopdf 生成 PDF
        byte[] pdfBytes = pdfRenderer.render(html);

        // 保存 PDF
        String uploadDir = RuoYiConfig.getProfile() + "/contract/" + contractId;
        Path dirPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(dirPath))
        {
            Files.createDirectories(dirPath);
        }
        String contractNo = String.valueOf(contract.get("contractNo"));
        String fileName = contractNo + "_signed.pdf";
        Path pdfPath = dirPath.resolve(fileName);
        Files.write(pdfPath, pdfBytes);

        String pdfUrl = Constants.RESOURCE_PREFIX + "/contract/" + contractId + "/" + fileName;
        jdbc.update("UPDATE sys_contract SET signed_pdf_url = ?, update_time = NOW() WHERE contract_id = ?",
                pdfUrl, contractId);

        log.info("合同 {} 签署完成，已生成 PDF: {}", contractNo, pdfUrl);
    }

    /**
     * 获取当前用户 ID（兼容 APP 端和管理端）。
     */
    private Long getCurrentUserId()
    {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof AppIdentityContext identity)
        {
            return identity.getUserId();
        }
        try
        {
            return SecurityUtils.getUserId();
        }
        catch (Exception e)
        {
            throw new RuntimeException("无法获取当前用户身份");
        }
    }

    /** 平台支持的合同模板集。 */
    private static final java.util.Set<String> SUPPORTED_TEMPLATES =
            java.util.Set.of("TPL_COPYRIGHT_TRANSFER", "TPL_COPYRIGHT_LICENSE");

    /** 正文条款纯文本长度上限（字）。 */
    private static final int MAX_CLAUSES_LENGTH = 20000;

    /**
     * 平台合同模板目录（供前端切换模板时填充默认条款）。
     * <p>
     * 传入 contractId 时按该合同的作品名称/许可类型/金额生成贴合的默认条款（仅限买/卖方访问）；
     * 为空时返回通用默认条款。
     *
     * @param contractId 可选，合同 ID
     * @return 模板列表 [{templateId, name, defaultClausesText}]
     */
    public java.util.List<Map<String, Object>> listContractTemplates(Long contractId)
    {
        String workTitle = "";
        String licenseType = "";
        java.math.BigDecimal amount = null;
        if (contractId != null)
        {
            Map<String, Object> d = getById(contractId);
            Long currentUserId = getCurrentUserId();
            Long buyerId = toLong(d.get("buyerId"));
            Long sellerId = toLong(d.get("sellerId"));
            if (!currentUserId.equals(buyerId) && !currentUserId.equals(sellerId))
            {
                throw new IllegalStateException("无权访问该合同");
            }
            workTitle = str(d.get("workTitle"));
            licenseType = str(d.get("licenseType"));
            Object amt = d.get("amount");
            amount = (amt instanceof java.math.BigDecimal) ? (java.math.BigDecimal) amt : null;
        }
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        list.add(templateItem("TPL_COPYRIGHT_TRANSFER", "版权转让合同", workTitle, licenseType, amount));
        list.add(templateItem("TPL_COPYRIGHT_LICENSE", "版权授权合同", workTitle, licenseType, amount));
        return list;
    }

    private Map<String, Object> templateItem(String templateId, String name, String workTitle,
                                             String licenseType, java.math.BigDecimal amount)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("templateId", templateId);
        m.put("name", name);
        m.put("defaultClausesText",
                ContractHtmlSupport.defaultClausesText(templateId, workTitle, licenseType, amount));
        return m;
    }

    /**
     * 编辑合同正文条款（未进入签署时可改，任一方已签即锁定）。
     * <p>
     * 校验当前用户为该订单买/卖；SELECT ... FOR UPDATE；
     * 门禁：status='pending' 且双方 sign_status=0，否则抛 IllegalStateException（锁定）；
     * 校验 templateId 属于平台模板集、clausesText 去空限长；
     * 以订单/合同的固定信息重渲染 HTML，CAS UPDATE（WHERE 再带锁定条件），命中 0 行按锁定报错。
     *
     * @param contractId   合同 ID
     * @param templateId   目标模板
     * @param clausesText  编辑后的正文条款纯文本
     * @return 更新后的合同详情
     */
    @Transactional
    public Map<String, Object> updateContractContent(Long contractId, String templateId, String clausesText)
    {
        if (contractId == null)
        {
            throw new IllegalArgumentException("合同 ID 无效");
        }
        if (templateId == null || !SUPPORTED_TEMPLATES.contains(templateId))
        {
            throw new IllegalArgumentException("模板类型不合法");
        }
        String text = clausesText == null ? "" : clausesText.trim();
        if (text.isEmpty())
        {
            throw new IllegalArgumentException("合同正文条款不能为空");
        }
        if (text.length() > MAX_CLAUSES_LENGTH)
        {
            throw new IllegalArgumentException("合同正文条款过长（最多 " + MAX_CLAUSES_LENGTH + " 字）");
        }

        Long currentUserId = getCurrentUserId();
        Map<String, Object> lock;
        try
        {
            lock = jdbc.queryForMap(
                    "SELECT buyer_id AS buyerId, seller_id AS sellerId, order_id AS orderId, status AS status, "
                            + "source_type AS sourceType, "
                            + "buyer_sign_status AS buyerSignStatus, seller_sign_status AS sellerSignStatus "
                            + "FROM sys_contract WHERE contract_id = ? FOR UPDATE", contractId);
        }
        catch (org.springframework.dao.EmptyResultDataAccessException e)
        {
            throw new IllegalArgumentException("合同不存在");
        }

        Long buyerId = toLong(lock.get("buyerId"));
        Long sellerId = toLong(lock.get("sellerId"));
        if (!currentUserId.equals(buyerId) && !currentUserId.equals(sellerId))
        {
            throw new IllegalStateException("无权修改该合同");
        }
        boolean editable = "pending".equals(str(lock.get("status")))
                && isUnsigned(lock.get("buyerSignStatus")) && isUnsigned(lock.get("sellerSignStatus"));
        if (!editable)
        {
            throw new IllegalStateException("合同已进入签署，内容已锁定，不可修改");
        }
        // 上传件无 HTML 正文、不可在线编辑条款：接口层兜底拦截（前端 UI 已拦，防直连绕过产生脏数据）。
        if ("uploaded".equals(String.valueOf(lock.get("sourceType"))))
        {
            throw new IllegalStateException("上传合同不支持在线编辑，请切回平台模板或重新上传");
        }

        // 取渲染所需固定信息区字段（订单号/作品/双方名/金额/许可类型）
        Map<String, Object> detail = jdbc.queryForMap(CONTRACT_SELECT + " WHERE c.contract_id = ?", contractId);
        Object licenseType = detail.get("licenseType");
        String contractType = "TPL_COPYRIGHT_TRANSFER".equals(templateId) ? "transfer" : "license";
        String html = ContractHtmlSupport.buildContractHtml(
                str(detail.get("contractNo")), str(detail.get("orderNo")), str(detail.get("workTitle")),
                displayNameOf(detail.get("buyerRealName"), detail.get("buyerName")),
                displayNameOf(detail.get("sellerRealName"), detail.get("sellerName")),
                (java.math.BigDecimal) detail.get("amount"),
                licenseType == null ? null : String.valueOf(licenseType),
                templateId, text);

        int updated = jdbc.update(
                "UPDATE sys_contract SET template_id = ?, contract_type = ?, clauses_text = ?, contract_content = ?, "
                        + "update_by = ?, update_time = NOW() "
                        + "WHERE contract_id = ? AND status = 'pending' AND buyer_sign_status = 0 AND seller_sign_status = 0",
                templateId, contractType, text, html, String.valueOf(currentUserId), contractId);
        if (updated == 0)
        {
            // 并发下已被对方签署，按锁定处理（回滚本次事务）
            throw new IllegalStateException("合同已进入签署，内容已锁定，不可修改");
        }
        log.info("合同 {} 正文条款已更新，templateId={}，操作人={}", contractId, templateId, currentUserId);
        return getById(contractId);
    }

    /** 未签署判定：0 或 null 视为未签。 */
    private static boolean isUnsigned(Object signStatus)
    {
        if (signStatus == null)
        {
            return true;
        }
        if (signStatus instanceof Number n)
        {
            return n.intValue() == 0;
        }
        return "0".equals(String.valueOf(signStatus).trim());
    }

    /** null 安全转 Long。 */
    private static Long toLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number n)
        {
            return n.longValue();
        }
        try
        {
            return Long.parseLong(String.valueOf(value).trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    // ==================== 上传 PDF 自助签章（拖拽定位） ====================

    /** 上传合同 PDF 大小上限：10MB（与 application.yml 的 multipart.max-file-size 对齐，否则容器层先行 413）。 */
    private static final long MAX_SOURCE_PDF_BYTES = 10L * 1024 * 1024;

    /** 上传 PDF 页数上限：防扫描件超大文档在行锁事务内耗尽堆内存/CPU。 */
    private static final int MAX_PAGE_COUNT = 50;

    /** 上传件分页图渲染分辨率（DPI）。 */
    private static final int SOURCE_PAGE_DPI = 120;

    /** 签章定位 JSON 长度上限，防滥用。 */
    private static final int MAX_PLACEMENTS_LENGTH = 8000;

    private static final ObjectMapper PLACEMENT_MAPPER = new ObjectMapper();

    /**
     * 上传合同 PDF 替换平台模板：任一方在未签署前均可上传覆盖（以最后一次为准）。
     * 上传件不可在线编辑条款；改为逐页渲染图片供签署方拖拽定位签名/印章。
     *
     * @param contractId 合同 ID
     * @param file       上传的 PDF 文件
     * @return 更新后的合同详情（含分页图 URL）
     */
    @Transactional
    public Map<String, Object> uploadSourcePdf(Long contractId, MultipartFile file)
    {
        if (contractId == null)
        {
            throw new IllegalArgumentException("合同 ID 无效");
        }
        if (file == null || file.isEmpty())
        {
            throw new IllegalArgumentException("合同 PDF 文件不能为空");
        }
        if (file.getSize() > MAX_SOURCE_PDF_BYTES)
        {
            throw new IllegalArgumentException("合同 PDF 大小不能超过 10MB");
        }
        byte[] pdfBytes;
        try
        {
            pdfBytes = file.getBytes();
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("读取上传文件失败");
        }
        if (pdfBytes.length < 5 || !(pdfBytes[0] == '%' && pdfBytes[1] == 'P' && pdfBytes[2] == 'D' && pdfBytes[3] == 'F'))
        {
            throw new IllegalArgumentException("上传文件不是有效的 PDF（须以 %PDF 开头）");
        }

        // 门禁：当前用户为买/卖方、pending 且双方未签（FOR UPDATE）
        Long currentUserId = requireEditableParty(contractId);

        int pageCount;
        try
        {
            pageCount = pdfRenderer.countPages(pdfBytes);
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException("PDF 解析失败：" + e.getMessage());
        }
        if (pageCount <= 0)
        {
            throw new IllegalArgumentException("PDF 无可渲染页面");
        }
        if (pageCount > MAX_PAGE_COUNT)
        {
            throw new IllegalArgumentException("PDF 页数过多（最多 " + MAX_PAGE_COUNT + " 页）");
        }

        Path dirPath = contractDir(contractId);
        cleanContractUploadArtifacts(dirPath);
        String sourceFileName = "source_" + System.currentTimeMillis() + ".pdf";
        try
        {
            Files.write(dirPath.resolve(sourceFileName), pdfBytes);
            List<byte[]> pagePngs = pdfRenderer.renderAllPagesToPng(pdfBytes, SOURCE_PAGE_DPI);
            for (int i = 0; i < pagePngs.size(); i++)
            {
                Files.write(dirPath.resolve("page_" + i + ".png"), pagePngs.get(i));
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("保存合同 PDF 或渲染分页图失败: " + e.getMessage());
        }

        String sourcePdfUrl = Constants.RESOURCE_PREFIX + "/contract/" + contractId + "/" + sourceFileName;
        int updated = jdbc.update(
                "UPDATE sys_contract SET source_type = 'uploaded', source_pdf_url = ?, pdf_page_count = ?, "
                        + "contract_content = NULL, clauses_text = NULL, buyer_sign_pos = NULL, seller_sign_pos = NULL, "
                        + "signed_pdf_url = NULL, update_by = ?, update_time = NOW() "
                        + "WHERE contract_id = ? AND status = 'pending' AND buyer_sign_status = 0 AND seller_sign_status = 0",
                sourcePdfUrl, pageCount, String.valueOf(currentUserId), contractId);
        if (updated == 0)
        {
            throw new IllegalStateException("合同已进入签署，内容已锁定，不可修改");
        }
        log.info("合同 {} 已上传 PDF 替换模板，页数={}，操作人={}", contractId, pageCount, currentUserId);
        return getById(contractId);
    }

    /**
     * 从上传 PDF 切回平台模板：未签署前自由切换；恢复可编辑条款与系统自动落章，清除上传件与定位。
     */
    @Transactional
    public Map<String, Object> switchToTemplate(Long contractId, String templateId)
    {
        if (contractId == null)
        {
            throw new IllegalArgumentException("合同 ID 无效");
        }
        if (templateId == null || !SUPPORTED_TEMPLATES.contains(templateId))
        {
            throw new IllegalArgumentException("模板类型不合法");
        }
        Long currentUserId = requireEditableParty(contractId);

        Map<String, Object> detail = jdbc.queryForMap(CONTRACT_SELECT + " WHERE c.contract_id = ?", contractId);
        Object licenseType = detail.get("licenseType");
        java.math.BigDecimal amount = (java.math.BigDecimal) detail.get("amount");
        String clausesText = ContractHtmlSupport.defaultClausesText(
                templateId, str(detail.get("workTitle")),
                licenseType == null ? null : String.valueOf(licenseType), amount);
        String contractType = "TPL_COPYRIGHT_TRANSFER".equals(templateId) ? "transfer" : "license";
        String html = ContractHtmlSupport.buildContractHtml(
                str(detail.get("contractNo")), str(detail.get("orderNo")), str(detail.get("workTitle")),
                displayNameOf(detail.get("buyerRealName"), detail.get("buyerName")),
                displayNameOf(detail.get("sellerRealName"), detail.get("sellerName")),
                amount, licenseType == null ? null : String.valueOf(licenseType), templateId, clausesText);

        int updated = jdbc.update(
                "UPDATE sys_contract SET source_type = 'template', template_id = ?, contract_type = ?, "
                        + "clauses_text = ?, contract_content = ?, source_pdf_url = NULL, pdf_page_count = NULL, "
                        + "buyer_sign_pos = NULL, seller_sign_pos = NULL, signed_pdf_url = NULL, update_by = ?, update_time = NOW() "
                        + "WHERE contract_id = ? AND status = 'pending' AND buyer_sign_status = 0 AND seller_sign_status = 0",
                templateId, contractType, clausesText, html, String.valueOf(currentUserId), contractId);
        if (updated == 0)
        {
            throw new IllegalStateException("合同已进入签署，内容已锁定，不可修改");
        }
        try
        {
            cleanContractUploadArtifacts(contractDir(contractId));
        }
        catch (Exception e)
        {
            log.warn("合同 {} 切回模板后清理上传件失败（不影响切换结果）: {}", contractId, e.getMessage());
        }
        log.info("合同 {} 已从上传件切回模板 templateId={}，操作人={}", contractId, templateId, currentUserId);
        return getById(contractId);
    }

    /**
     * 内容/来源可编辑门禁：FOR UPDATE 锁行、校验当前用户为买/卖方、status=pending 且双方未签。
     * 不满足抛 IllegalArgument（不存在）/IllegalState（无权/锁定），返回当前用户 ID。
     */
    private Long requireEditableParty(Long contractId)
    {
        Long currentUserId = getCurrentUserId();
        Map<String, Object> lock;
        try
        {
            lock = jdbc.queryForMap(
                    "SELECT buyer_id AS buyerId, seller_id AS sellerId, status AS status, "
                            + "buyer_sign_status AS buyerSignStatus, seller_sign_status AS sellerSignStatus "
                            + "FROM sys_contract WHERE contract_id = ? FOR UPDATE", contractId);
        }
        catch (org.springframework.dao.EmptyResultDataAccessException e)
        {
            throw new IllegalArgumentException("合同不存在");
        }
        Long buyerId = toLong(lock.get("buyerId"));
        Long sellerId = toLong(lock.get("sellerId"));
        if (!currentUserId.equals(buyerId) && !currentUserId.equals(sellerId))
        {
            throw new IllegalStateException("无权修改该合同");
        }
        boolean editable = "pending".equals(str(lock.get("status")))
                && isUnsigned(lock.get("buyerSignStatus")) && isUnsigned(lock.get("sellerSignStatus"));
        if (!editable)
        {
            throw new IllegalStateException("合同已进入签署，内容已锁定，不可修改");
        }
        return currentUserId;
    }

    /**
     * 校验并归一化上传件签章定位 JSON：数组，每项 {kind,page,x,y,w[,h]}。
     * kind∈{signature,seal}；page∈[0,pageCount)；x/y/w∈[0,1] 比例坐标（左上角，y 自顶向下）。
     * 返回紧凑 JSON 字符串落库；非法抛 IllegalArgumentException。
     */
    private String normalizePlacements(String placementsJson, Object pageCountObj)
    {
        if (placementsJson == null || placementsJson.trim().isEmpty())
        {
            throw new IllegalArgumentException("上传合同签署需要提供签章定位信息");
        }
        if (placementsJson.length() > MAX_PLACEMENTS_LENGTH)
        {
            throw new IllegalArgumentException("签章定位信息过长");
        }
        int pageCount = pageCountObj instanceof Number ? ((Number) pageCountObj).intValue() : 0;
        if (pageCount <= 0)
        {
            throw new IllegalStateException("上传合同缺少页数信息");
        }
        JsonNode root;
        try
        {
            root = PLACEMENT_MAPPER.readTree(placementsJson);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("签章定位信息不是合法 JSON");
        }
        if (!root.isArray() || root.size() == 0)
        {
            throw new IllegalArgumentException("签章定位信息须为非空数组");
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (JsonNode node : root)
        {
            String kind = node.path("kind").asText("");
            if (!"signature".equals(kind) && !"seal".equals(kind))
            {
                throw new IllegalArgumentException("签章类型仅支持 signature 或 seal");
            }
            int page = node.path("page").asInt(-1);
            if (page < 0 || page >= pageCount)
            {
                throw new IllegalArgumentException("签章页码越界：" + page);
            }
            double x = node.path("x").asDouble(Double.NaN);
            double y = node.path("y").asDouble(Double.NaN);
            double w = node.path("w").asDouble(Double.NaN);
            if (!inUnitRange(x) || !inUnitRange(y) || !inUnitRange(w) || w <= 0)
            {
                throw new IllegalArgumentException("签章坐标须为 0~1 之间的比例且宽度大于 0");
            }
            if (x + w > 1.0 + 1e-6)
            {
                throw new IllegalArgumentException("签章超出页面右边界（x + w 须 ≤ 1）");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("kind", kind);
            item.put("page", page);
            item.put("x", x);
            item.put("y", y);
            item.put("w", w);
            normalized.add(item);
        }
        try
        {
            return PLACEMENT_MAPPER.writeValueAsString(normalized);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("签章定位序列化失败");
        }
    }

    private static boolean inUnitRange(double v)
    {
        return !Double.isNaN(v) && v >= 0 && v <= 1;
    }

    /**
     * 上传件签署完成：读取源 PDF + 双方签章定位与图片，按坐标盖章合成最终 PDF 存 signed_pdf_url。
     */
    private void stampedSourcePdf(Long contractId) throws IOException
    {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT contract_no AS contractNo, source_pdf_url AS sourcePdfUrl, "
                        + "buyer_sign_pos AS buyerSignPos, seller_sign_pos AS sellerSignPos, "
                        + "buyer_signature_image AS buyerSignatureImage, buyer_seal_image AS buyerSealImage, "
                        + "seller_signature_image AS sellerSignatureImage, seller_seal_image AS sellerSealImage "
                        + "FROM sys_contract WHERE contract_id = ?", contractId);
        String sourcePdfUrl = str(row.get("sourcePdfUrl"));
        if (sourcePdfUrl.isEmpty())
        {
            throw new IOException("上传合同源 PDF 不存在，无法盖章");
        }
        byte[] pdf = readProfileBytes(sourcePdfUrl);

        List<ContractPdfRenderer.Stamp> stamps = new ArrayList<>();
        appendStamps(stamps, "buyer", str(row.get("buyerSignPos")),
                str(row.get("buyerSignatureImage")), str(row.get("buyerSealImage")));
        appendStamps(stamps, "seller", str(row.get("sellerSignPos")),
                str(row.get("sellerSignatureImage")), str(row.get("sellerSealImage")));
        if (stamps.isEmpty())
        {
            throw new IOException("上传合同无任何有效签章定位，无法生成盖章 PDF");
        }

        byte[] stamped = pdfRenderer.stampImages(pdf, stamps);
        Path dirPath = contractDir(contractId);
        String fileName = str(row.get("contractNo")) + "_signed.pdf";
        Files.write(dirPath.resolve(fileName), stamped);
        String pdfUrl = Constants.RESOURCE_PREFIX + "/contract/" + contractId + "/" + fileName;
        jdbc.update("UPDATE sys_contract SET signed_pdf_url = ?, update_time = NOW() WHERE contract_id = ?",
                pdfUrl, contractId);
        log.info("合同 {} 上传件签署完成，已盖章生成 PDF: {}（共 {} 枚签章）", str(row.get("contractNo")), pdfUrl, stamps.size());
    }

    /** 解析一方定位 JSON 并结合其签名/印章图片 URL 构造 Stamp 列表。图片缺失或解析失败跳过并记日志。 */
    private void appendStamps(List<ContractPdfRenderer.Stamp> out, String party, String posJson,
                              String signatureUrl, String sealUrl)
    {
        if (posJson == null || posJson.trim().isEmpty())
        {
            return;
        }
        JsonNode root;
        try
        {
            root = PLACEMENT_MAPPER.readTree(posJson);
        }
        catch (Exception e)
        {
            log.warn("合同 {} 方签章定位解析失败，跳过: {}", party, e.getMessage());
            return;
        }
        if (!root.isArray())
        {
            return;
        }
        for (JsonNode node : root)
        {
            String kind = node.path("kind").asText("");
            String imageUrl = "signature".equals(kind) ? signatureUrl : ("seal".equals(kind) ? sealUrl : null);
            if (imageUrl == null || imageUrl.isEmpty())
            {
                continue;
            }
            byte[] image;
            try
            {
                image = readProfileBytes(imageUrl);
            }
            catch (Exception e)
            {
                log.warn("合同 {} 方签章图片读取失败，跳过 kind={} url={}: {}", party, kind, imageUrl, e.getMessage());
                continue;
            }
            out.add(new ContractPdfRenderer.Stamp(
                    image, node.path("page").asInt(0),
                    node.path("x").asDouble(0), node.path("y").asDouble(0),
                    node.path("w").asDouble(0.1), kind));
        }
    }

    /**
     * 详情出口分叉：上传件不返回 HTML 正文，改返回分页图 URL 列表供前端翻页拖拽。
     */
    private Map<String, Object> decorateSource(Map<String, Object> detail)
    {
        if (detail == null)
        {
            return null;
        }
        if ("uploaded".equals(str(detail.get("sourceType"))))
        {
            Object pc = detail.get("pageCount");
            int count = pc instanceof Number ? ((Number) pc).intValue() : 0;
            Long id = toLong(detail.get("contractId"));
            List<String> pageUrls = new ArrayList<>();
            for (int i = 0; i < count; i++)
            {
                pageUrls.add(Constants.RESOURCE_PREFIX + "/contract/" + id + "/page_" + i + ".png");
            }
            detail.put("pageImageUrls", pageUrls);
            detail.put("content", null);
        }
        else
        {
            detail.putIfAbsent("pageImageUrls", new ArrayList<String>());
        }
        return detail;
    }

    /** 合同产物目录（profile/contract/{id}），确保存在并返回绝对路径。 */
    private Path contractDir(Long contractId)
    {
        Path dirPath = Paths.get(RuoYiConfig.getProfile() + "/contract/" + contractId).toAbsolutePath().normalize();
        try
        {
            if (!Files.exists(dirPath))
            {
                Files.createDirectories(dirPath);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("创建合同目录失败: " + e.getMessage());
        }
        return dirPath;
    }

    /** 删除合同目录下的源 PDF 与分页图（source_*.pdf / page_*.png），用于重新上传或切回模板前清场；保留最终 signed pdf。 */
    private void cleanContractUploadArtifacts(Path dirPath)
    {
        java.io.File[] files = dirPath.toFile().listFiles();
        if (files == null)
        {
            return;
        }
        for (java.io.File f : files)
        {
            String name = f.getName();
            if (name.startsWith("page_") || name.startsWith("source_"))
            {
                if (!f.delete())
                {
                    log.warn("清理合同上传产物失败: {}", f.getAbsolutePath());
                }
            }
        }
    }

    /** 将 /profile 相对 URL 转为本地绝对文件路径（与静态资源映射一致）。 */
    private String profileUrlToLocalPath(String url)
    {
        String prefix = Constants.RESOURCE_PREFIX;
        String relative = url.startsWith(prefix) ? url.substring(prefix.length()) : url;
        return RuoYiConfig.getProfile() + relative;
    }

    /** 读取 /profile 相对 URL 指向的本地文件字节。 */
    private byte[] readProfileBytes(String url) throws IOException
    {
        return Files.readAllBytes(Paths.get(profileUrlToLocalPath(url)));
    }
}
