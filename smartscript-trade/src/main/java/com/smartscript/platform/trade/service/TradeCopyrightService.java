package com.smartscript.platform.trade.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.trade.domain.BuyerCopyrightRecord;
import com.smartscript.platform.trade.domain.SysDelivery;
import com.smartscript.platform.trade.domain.SysDeliveryFile;
import com.smartscript.platform.trade.mapper.SysDeliveryFileMapper;
import com.smartscript.platform.trade.mapper.SysDeliveryMapper;

/**
 * 买方「版权」只读台账：确认收货后的交割 cert 文件。
 * 契约：docs/contracts/2026-11-02-enterprise-buyer-copyright.md
 */
@Service
public class TradeCopyrightService
{
    private final SysDeliveryFileMapper deliveryFileMapper;
    private final SysDeliveryMapper deliveryMapper;

    public TradeCopyrightService(SysDeliveryFileMapper deliveryFileMapper, SysDeliveryMapper deliveryMapper)
    {
        this.deliveryFileMapper = deliveryFileMapper;
        this.deliveryMapper = deliveryMapper;
    }

    public Map<String, Object> listForBuyer(Long buyerId, String keyword, int pageNum, int pageSize)
    {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        PageHelper.startPage(safePage, safeSize);
        List<BuyerCopyrightRecord> rows = deliveryFileMapper.selectBuyerCopyrightList(buyerId, trim(keyword));
        PageInfo<BuyerCopyrightRecord> pageInfo = new PageInfo<>(rows);
        List<Map<String, Object>> list = rows.stream().map(this::toMap).toList();
        Map<String, Object> result = new HashMap<>();
        result.put("total", pageInfo.getTotal());
        result.put("list", list);
        return result;
    }

    /**
     * 买方查看单条 cert 材料；非 cert / 非买方 / 交割未完成均拒绝。
     */
    public Map<String, Object> detailForBuyer(Long buyerId, Long fileId)
    {
        SysDeliveryFile file = deliveryFileMapper.selectFileById(fileId);
        if (file == null)
        {
            throw new CopyrightNotFoundException("版权材料不存在");
        }
        if (!SysDeliveryFile.TYPE_CERT.equals(file.getFileType()))
        {
            throw new CopyrightNotFoundException("版权材料不存在");
        }
        SysDelivery delivery = deliveryMapper.selectDeliveryById(file.getDeliveryId());
        if (delivery == null)
        {
            throw new CopyrightNotFoundException("交割单不存在");
        }
        if (!buyerId.equals(delivery.getBuyerId()))
        {
            throw new CopyrightForbiddenException("无权查看该版权材料");
        }
        if (!SysDelivery.STATUS_COMPLETED.equals(delivery.getStatus()))
        {
            throw new CopyrightForbiddenException("确认收货后才可查看版权材料");
        }
        BuyerCopyrightRecord record = new BuyerCopyrightRecord();
        record.setFileId(file.getFileId());
        record.setFileName(file.getFileName());
        record.setFileUrl(file.getFileUrl());
        record.setFileSize(file.getFileSize());
        record.setWorkId(delivery.getWorkId());
        record.setWorkTitle(delivery.getWorkTitle());
        record.setWorkCover(delivery.getWorkCover());
        record.setSellerId(delivery.getSellerId());
        record.setSellerName(delivery.getSellerName());
        record.setDeliveryId(delivery.getDeliveryId());
        record.setDeliveryNo(delivery.getDeliveryNo());
        record.setOrderId(delivery.getOrderId());
        record.setOrderNo(delivery.getOrderNo());
        record.setBuyerId(delivery.getBuyerId());
        record.setCompletedTime(delivery.getCompletedTime());
        record.setConfirmTime(delivery.getConfirmTime());
        return toMap(record);
    }

    private Map<String, Object> toMap(BuyerCopyrightRecord r)
    {
        Map<String, Object> m = new HashMap<>();
        m.put("fileId", r.getFileId());
        m.put("fileName", r.getFileName() != null ? r.getFileName() : "");
        m.put("fileUrl", r.getFileUrl() != null ? r.getFileUrl() : "");
        m.put("fileSize", r.getFileSize() != null ? r.getFileSize() : 0L);
        m.put("workId", r.getWorkId());
        m.put("workTitle", r.getWorkTitle() != null ? r.getWorkTitle() : "");
        m.put("workCover", r.getWorkCover() != null ? r.getWorkCover() : "");
        m.put("sellerId", r.getSellerId());
        m.put("sellerName", r.getSellerName() != null ? r.getSellerName() : "");
        m.put("deliveryId", r.getDeliveryId());
        m.put("deliveryNo", r.getDeliveryNo() != null ? r.getDeliveryNo() : "");
        m.put("orderId", r.getOrderId());
        m.put("orderNo", r.getOrderNo() != null ? r.getOrderNo() : "");
        Date deliveryTime = r.getCompletedTime() != null ? r.getCompletedTime() : r.getConfirmTime();
        m.put("deliveryTime", formatTime(deliveryTime));
        m.put("previewKind", previewKind(r.getFileName(), r.getFileUrl()));
        return m;
    }

    static String previewKind(String fileName, String fileUrl)
    {
        String name = (fileName != null && !fileName.isEmpty()) ? fileName : (fileUrl != null ? fileUrl : "");
        String lower = name.toLowerCase(Locale.ROOT);
        int q = lower.indexOf('?');
        if (q >= 0)
        {
            lower = lower.substring(0, q);
        }
        if (lower.endsWith(".pdf"))
        {
            return "pdf";
        }
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp"))
        {
            return "image";
        }
        return "other";
    }

    private static String formatTime(Date time)
    {
        if (time == null)
        {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time);
    }

    private static String trim(String value)
    {
        if (value == null)
        {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    public static class CopyrightNotFoundException extends RuntimeException
    {
        public CopyrightNotFoundException(String message) { super(message); }
    }

    public static class CopyrightForbiddenException extends RuntimeException
    {
        public CopyrightForbiddenException(String message) { super(message); }
    }
}
