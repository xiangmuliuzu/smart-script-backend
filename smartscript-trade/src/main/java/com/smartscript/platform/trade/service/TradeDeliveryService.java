package com.smartscript.platform.trade.service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.Seq;
import com.smartscript.platform.trade.domain.SysDelivery;
import com.smartscript.platform.trade.domain.SysDeliveryFile;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.mapper.SysDeliveryFileMapper;
import com.smartscript.platform.trade.mapper.SysDeliveryMapper;

/**
 * 版权交割：买方付款进入交割中后建单，卖方上传材料，买方确认后完成订单。
 */
@Service
public class TradeDeliveryService
{
    private static final Logger log = LoggerFactory.getLogger(TradeDeliveryService.class);

    @Autowired
    private SysDeliveryMapper deliveryMapper;

    @Autowired
    private SysDeliveryFileMapper deliveryFileMapper;

    @Autowired
    @Lazy
    private TradeOrderService orderService;

    @Autowired
    private TradeContractService contractService;

    @Autowired
    private JdbcTemplate jdbc;

    public List<SysDelivery> selectDeliveryList(SysDelivery query)
    {
        return deliveryMapper.selectDeliveryList(query);
    }

    public SysDelivery selectDeliveryById(Long deliveryId)
    {
        SysDelivery delivery = deliveryMapper.selectDeliveryById(deliveryId);
        if (delivery != null)
        {
            delivery.setFiles(deliveryFileMapper.selectFilesByDeliveryId(deliveryId));
        }
        return delivery;
    }

    public SysDelivery selectDeliveryByOrderId(Long orderId)
    {
        SysDelivery delivery = deliveryMapper.selectDeliveryByOrderId(orderId);
        if (delivery != null)
        {
            delivery.setFiles(deliveryFileMapper.selectFilesByDeliveryId(delivery.getDeliveryId()));
            return delivery;
        }
        SysOrder order = orderService.selectOrderById(orderId);
        if (orderNeedsDelivery(order))
        {
            SysDelivery created = createDeliveryForOrder(order);
            return selectDeliveryById(created.getDeliveryId());
        }
        return null;
    }

    /**
     * 打开交割列表前补建当前用户缺失的交割单。须在 PageHelper.startPage 之前调用。
     */
    public void ensureDeliveriesForUser(Long userId)
    {
        if (userId == null)
        {
            return;
        }
        List<Long> orderIds = jdbc.queryForList(
                "SELECT o.order_id FROM sys_order o "
                        + "WHERE (o.buyer_id = ? OR o.seller_id = ?) "
                        + "AND o.status IN (?, ?) "
                        + "AND NOT EXISTS (SELECT 1 FROM sys_delivery d WHERE d.order_id = o.order_id)",
                Long.class,
                userId,
                userId,
                TradeOrderService.ORDER_STATUS_DELIVERING,
                TradeOrderService.ORDER_STATUS_COMPLETED);
        for (Long orderId : orderIds)
        {
            try
            {
                createDeliveryForOrder(orderService.selectOrderById(orderId));
            }
            catch (RuntimeException e)
            {
                log.warn("补建交割单失败 orderId={}: {}", orderId, e.getMessage());
            }
        }
    }

    /**
     * 买方付款进入交割中后创建交割单（幂等）。
     */
    @Transactional
    public SysDelivery createDeliveryForOrder(SysOrder order)
    {
        if (order == null || order.getOrderId() == null)
        {
            throw new RuntimeException("订单不存在，无法创建交割单");
        }
        SysDelivery existing = deliveryMapper.selectDeliveryByOrderId(order.getOrderId());
        if (existing != null)
        {
            return existing;
        }

        SysDelivery delivery = new SysDelivery();
        delivery.setDeliveryNo("DLV" + Seq.getId());
        delivery.setOrderId(order.getOrderId());
        delivery.setWorkId(order.getWorkId());
        delivery.setBuyerId(order.getBuyerId());
        delivery.setSellerId(order.getSellerId());
        delivery.setAmount(order.getTotalAmount());
        delivery.setLicenseType(order.getLicenseType());
        delivery.setStatus(deliveryStatusForOrder(order.getStatus()));
        delivery.setCreateBy(safeOperatorName());
        delivery.setCreateTime(new Date());

        fillContractSnapshot(delivery, order);
        fillCertNo(delivery, order.getWorkId());

        deliveryMapper.insertDelivery(delivery);
        if (SysDelivery.STATUS_COMPLETED.equals(delivery.getStatus()))
        {
            SysDelivery patch = new SysDelivery();
            patch.setDeliveryId(delivery.getDeliveryId());
            patch.setCompletedTime(new Date());
            deliveryMapper.updateDelivery(patch);
            delivery.setCompletedTime(patch.getCompletedTime());
        }
        return delivery;
    }

    /**
     * 订单确认收货前：卖方必须已提交交割材料。若交割单仍待确认则一并完成。
     */
    public void prepareForComplete(Long orderId)
    {
        SysOrder order = orderService.selectOrderById(orderId);
        SysDelivery delivery = deliveryMapper.selectDeliveryByOrderId(orderId);
        if (delivery == null)
        {
            if (orderNeedsDelivery(order))
            {
                createDeliveryForOrder(order);
            }
            throw new RuntimeException("卖方尚未提交交割材料，无法确认收货");
        }
        if (SysDelivery.STATUS_PENDING_UPLOAD.equals(delivery.getStatus()))
        {
            throw new RuntimeException("卖方尚未提交交割材料，无法确认收货");
        }
        if (SysDelivery.STATUS_CANCELLED.equals(delivery.getStatus()))
        {
            throw new RuntimeException("交割单已取消，无法确认收货");
        }
        if (SysDelivery.STATUS_PENDING_CONFIRM.equals(delivery.getStatus()))
        {
            markDeliveryCompleted(delivery.getDeliveryId());
        }
    }

    /**
     * 卖方提交交割材料。订单已处于 delivering，此处只推进交割单。
     */
    @Transactional
    public SysDelivery uploadDelivery(Long deliveryId, Long operatorId, List<Map<String, Object>> files, String remark)
    {
        SysDelivery delivery = requireDelivery(deliveryId);
        if (!operatorId.equals(delivery.getSellerId()))
        {
            throw new RuntimeException("只有卖方可以上传交割材料");
        }
        if (!SysDelivery.STATUS_PENDING_UPLOAD.equals(delivery.getStatus()))
        {
            throw new RuntimeException("当前交割状态不可上传，需为 pending_upload");
        }
        validateFiles(files);

        deliveryFileMapper.deleteFilesByDeliveryId(deliveryId);
        Date now = new Date();
        for (Map<String, Object> item : files)
        {
            SysDeliveryFile file = new SysDeliveryFile();
            file.setDeliveryId(deliveryId);
            file.setFileName(str(item.get("fileName")));
            file.setFileType(str(item.get("fileType")));
            file.setFileUrl(str(item.get("fileUrl")));
            file.setFileSize(toLong(item.get("fileSize")));
            file.setCreateBy(safeOperatorName());
            file.setCreateTime(now);
            deliveryFileMapper.insertDeliveryFile(file);
        }

        SysDelivery update = new SysDelivery();
        update.setDeliveryId(deliveryId);
        update.setStatus(SysDelivery.STATUS_PENDING_CONFIRM);
        update.setRemark(remark);
        update.setUploadTime(now);
        update.setUpdateBy(safeOperatorName());
        update.setUpdateTime(now);
        deliveryMapper.updateDelivery(update);

        return selectDeliveryById(deliveryId);
    }

    /**
     * 买方确认收货：交割单完成，并完成订单。
     */
    @Transactional
    public SysDelivery confirmDelivery(Long deliveryId, Long operatorId)
    {
        SysDelivery delivery = requireDelivery(deliveryId);
        if (!operatorId.equals(delivery.getBuyerId()))
        {
            throw new RuntimeException("只有买方可以确认收货");
        }
        if (!SysDelivery.STATUS_PENDING_CONFIRM.equals(delivery.getStatus()))
        {
            throw new RuntimeException("当前交割状态不可确认，需为 pending_confirm");
        }
        orderService.completeOrder(delivery.getOrderId(), operatorId);
        return selectDeliveryById(deliveryId);
    }

    static boolean orderNeedsDelivery(SysOrder order)
    {
        if (order == null)
        {
            return false;
        }
        String status = order.getStatus();
        return TradeOrderService.ORDER_STATUS_DELIVERING.equals(status)
                || TradeOrderService.ORDER_STATUS_COMPLETED.equals(status);
    }

    private String deliveryStatusForOrder(String orderStatus)
    {
        if (TradeOrderService.ORDER_STATUS_COMPLETED.equals(orderStatus))
        {
            return SysDelivery.STATUS_COMPLETED;
        }
        return SysDelivery.STATUS_PENDING_UPLOAD;
    }

    private void markDeliveryCompleted(Long deliveryId)
    {
        Date now = new Date();
        SysDelivery update = new SysDelivery();
        update.setDeliveryId(deliveryId);
        update.setStatus(SysDelivery.STATUS_COMPLETED);
        update.setConfirmTime(now);
        update.setCompletedTime(now);
        update.setUpdateBy(safeOperatorName());
        update.setUpdateTime(now);
        deliveryMapper.updateDelivery(update);
    }

    private void validateFiles(List<Map<String, Object>> files)
    {
        if (files == null || files.isEmpty())
        {
            throw new RuntimeException("请至少上传 1 个交割文件");
        }
        boolean hasCert = false;
        for (Map<String, Object> item : files)
        {
            String fileName = str(item.get("fileName"));
            String fileType = str(item.get("fileType"));
            String fileUrl = str(item.get("fileUrl"));
            if (StringUtils.isEmpty(fileName) || StringUtils.isEmpty(fileType) || StringUtils.isEmpty(fileUrl))
            {
                throw new RuntimeException("交割文件缺少 fileName / fileType / fileUrl");
            }
            if (!isAllowedType(fileType))
            {
                throw new RuntimeException("不支持的文件类型：" + fileType + "，允许 cert/source/doc/other");
            }
            if (SysDeliveryFile.TYPE_CERT.equals(fileType))
            {
                hasCert = true;
            }
        }
        if (!hasCert)
        {
            throw new RuntimeException("必须上传版权证书文件（fileType=cert）");
        }
    }

    private boolean isAllowedType(String fileType)
    {
        return SysDeliveryFile.TYPE_CERT.equals(fileType)
                || SysDeliveryFile.TYPE_SOURCE.equals(fileType)
                || SysDeliveryFile.TYPE_DOC.equals(fileType)
                || SysDeliveryFile.TYPE_OTHER.equals(fileType);
    }

    private void fillContractSnapshot(SysDelivery delivery, SysOrder order)
    {
        if (order.getContractId() == null)
        {
            return;
        }
        try
        {
            Map<String, Object> contract = contractService.getById(order.getContractId());
            if (contract == null)
            {
                return;
            }
            Object no = contract.get("contractNo");
            if (no != null)
            {
                delivery.setContractNo(String.valueOf(no));
            }
            Date signTime = coalesceDate(contract.get("sellerSignTime"), contract.get("buyerSignTime"));
            delivery.setContractSignTime(signTime);
        }
        catch (Exception ignored)
        {
            // 合同快照失败不阻断建单
        }
    }

    private void fillCertNo(SysDelivery delivery, Long workId)
    {
        if (workId == null)
        {
            return;
        }
        try
        {
            List<String> rows = jdbc.query(
                    "SELECT cert_no FROM sys_copyright_cert WHERE work_id = ? ORDER BY cert_id DESC LIMIT 1",
                    (rs, rowNum) -> rs.getString(1),
                    workId);
            if (rows != null && !rows.isEmpty() && StringUtils.isNotEmpty(rows.get(0)))
            {
                delivery.setCertNo(rows.get(0));
            }
        }
        catch (Exception ignored)
        {
            // 证书表可能尚未就绪
        }
    }

    private SysDelivery requireDelivery(Long deliveryId)
    {
        SysDelivery delivery = deliveryMapper.selectDeliveryById(deliveryId);
        if (delivery == null)
        {
            throw new RuntimeException("交割单不存在");
        }
        return delivery;
    }

    private Date coalesceDate(Object first, Object second)
    {
        Date a = toDate(first);
        return a != null ? a : toDate(second);
    }

    private Date toDate(Object value)
    {
        if (value instanceof Date date)
        {
            return date;
        }
        return null;
    }

    private String str(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private Long toLong(Object value)
    {
        if (value == null)
        {
            return 0L;
        }
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        try
        {
            return Long.parseLong(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return 0L;
        }
    }

    private String safeOperatorName()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "system";
        }
    }

    public Map<String, Object> toAppMap(SysDelivery d)
    {
        Map<String, Object> m = new HashMap<>();
        m.put("deliveryId", d.getDeliveryId());
        m.put("deliveryNo", d.getDeliveryNo());
        m.put("orderId", d.getOrderId());
        m.put("orderNo", d.getOrderNo() != null ? d.getOrderNo() : "");
        m.put("workId", d.getWorkId());
        m.put("workTitle", d.getWorkTitle() != null ? d.getWorkTitle() : "");
        m.put("workCover", d.getWorkCover() != null ? d.getWorkCover() : "");
        m.put("buyerId", d.getBuyerId());
        m.put("buyerName", d.getBuyerName() != null ? d.getBuyerName() : "");
        m.put("sellerId", d.getSellerId());
        m.put("sellerName", d.getSellerName() != null ? d.getSellerName() : "");
        m.put("amount", d.getAmount());
        m.put("licenseType", d.getLicenseType() != null ? d.getLicenseType() : "");
        m.put("status", d.getStatus());
        m.put("contractNo", d.getContractNo() != null ? d.getContractNo() : "");
        m.put("contractSignTime", d.getContractSignTime());
        m.put("certNo", d.getCertNo());
        m.put("remark", d.getRemark());
        m.put("uploadTime", d.getUploadTime());
        m.put("confirmTime", d.getConfirmTime());
        m.put("completedTime", d.getCompletedTime());
        m.put("createTime", d.getCreatedAt() != null ? d.getCreatedAt() : d.getCreateTime());
        if (d.getFiles() != null)
        {
            m.put("files", d.getFiles().stream().map(f -> {
                Map<String, Object> fm = new HashMap<>();
                fm.put("fileName", f.getFileName());
                fm.put("fileType", f.getFileType());
                fm.put("fileUrl", f.getFileUrl());
                fm.put("fileSize", f.getFileSize() != null ? f.getFileSize() : 0);
                return fm;
            }).toList());
        }
        return m;
    }
}
