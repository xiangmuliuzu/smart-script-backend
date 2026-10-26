package com.smartscript.platform.trade.service;

import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.uuid.Seq;
import com.smartscript.platform.trade.support.ContractHtmlSupport;
import com.smartscript.platform.trade.support.ContractPdfRenderer;
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
import java.util.Date;
import java.util.LinkedHashMap;
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
            + "c.status AS status, c.contract_content AS content, "
            + "w.title AS workTitle, o.license_type AS licenseType, "
            + "DATE_FORMAT(c.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt "
            + "FROM sys_contract c JOIN sys_order o ON o.order_id = c.order_id "
            + "LEFT JOIN sys_user ub ON ub.user_id = c.buyer_id "
            + "LEFT JOIN sys_user us ON us.user_id = c.seller_id "
            + "LEFT JOIN sys_work w ON w.work_id = o.work_id ";

    /**
     * 合同列表查询（不带 contract_content 大字段，额外带作品封面）。
     */
    private static final String CONTRACT_LIST_SELECT =
            "SELECT c.contract_id AS contractId, c.contract_no AS contractNo, c.amount AS amount, "
            + "c.order_id AS orderId, o.order_no AS orderNo, c.buyer_id AS buyerId, ub.nick_name AS buyerName, "
            + "c.seller_id AS sellerId, us.nick_name AS sellerName, "
            + "c.buyer_sign_status AS buyerSignStatus, c.seller_sign_status AS sellerSignStatus, "
            + "c.buyer_sign_time AS buyerSignTime, c.seller_sign_time AS sellerSignTime, "
            + "c.signed_pdf_url AS signedPdfUrl, c.status AS status, "
            + "w.title AS workTitle, w.cover AS workCover, o.license_type AS licenseType, "
            + "DATE_FORMAT(c.created_at, '%Y-%m-%d %H:%i:%s') AS createdAt "
            + "FROM sys_contract c JOIN sys_order o ON o.order_id = c.order_id "
            + "LEFT JOIN sys_user ub ON ub.user_id = c.buyer_id "
            + "LEFT JOIN sys_user us ON us.user_id = c.seller_id "
            + "LEFT JOIN sys_work w ON w.work_id = o.work_id ";

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

    /**
     * 按订单 ID 取合同详情。
     */
    public Map<String, Object> getByOrderId(Long orderId)
    {
        try
        {
            return enrichWithSignatures(jdbc.queryForMap(CONTRACT_SELECT + " WHERE c.order_id = ?", orderId));
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
            return enrichWithSignatures(jdbc.queryForMap(CONTRACT_SELECT + " WHERE c.contract_id = ?", contractId));
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
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return {total, list}
     */
    public Map<String, Object> listContracts(Long userId, String role, String status, int pageNum, int pageSize)
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

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM sys_contract c " + where, Long.class, args.toArray());
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
        // 署名优先用实名姓名快照：下单时可能已实名，未实名的仍回退昵称（签署时点另有双签重渲染兑底）
        String html = ContractHtmlSupport.buildContractHtml(contractNo, orderNo, workTitle,
                displayName(buyerId, buyerName), displayName(sellerId, sellerName),
                amount, licenseType, templateId);

        jdbc.update("INSERT INTO sys_contract (contract_no, contract_type, amount, order_id, buyer_id, seller_id, "
                        + "template_id, contract_content, contract_url, buyer_sign_status, seller_sign_status, status, create_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, '', 0, 0, 'pending', ?)",
                contractNo, contractType, amount, orderId, buyerId, sellerId,
                templateId, html, "system");

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
    @Transactional
    public Map<String, Object> sign(Long contractId, MultipartFile signatureImage, MultipartFile sealImage)
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
        // sign_status = 0 乐观条件与行锁双重保险，防止并发抢占后重复写入
        int changed;
        if ("buyer".equals(party))
        {
            changed = jdbc.update("UPDATE sys_contract SET buyer_sign_status = 1, buyer_sign_time = NOW(), "
                    + "buyer_signature_image = ?, buyer_seal_image = ?, update_time = NOW(), status = ? "
                    + "WHERE contract_id = ? AND buyer_sign_status = 0", signatureUrl, sealUrl, newStatus, contractId);
        }
        else
        {
            changed = jdbc.update("UPDATE sys_contract SET seller_sign_status = 1, seller_sign_time = NOW(), "
                    + "seller_signature_image = ?, seller_seal_image = ?, update_time = NOW(), status = ? "
                    + "WHERE contract_id = ? AND seller_sign_status = 0", signatureUrl, sealUrl, newStatus, contractId);
        }
        if (changed != 1)
        {
            throw new IllegalStateException("合同状态已变化，请刷新后重试");
        }

        // 每次签署后立即将签名/印章持久化到 contract_content，使 DB 中的 HTML 始终反映最新签署状态
        persistSignaturesToContent(contractId);

        // 如果双方都签完，按实名姓名重渲染正文并生成最终 PDF（生成失败不影响签署事务，可由管理端补偿重建）
        if ("completed".equals(newStatus))
        {
            try
            {
                rerenderContentWithRealNames(contractId);
                generateSignedPdf(contractId);
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
                str(detail.get("templateId")));
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
        if (html.isBlank())
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
                "SELECT status FROM sys_contract WHERE contract_id = ?", contractId);
        String current = String.valueOf(status.get("status"));
        if (!"completed".equals(current) && !"archived".equals(current))
        {
            throw new IllegalStateException("仅已完成签署的合同可以重建 PDF（当前状态：" + current + "）");
        }
        generateSignedPdf(contractId);
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
}
