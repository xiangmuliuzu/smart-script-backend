package com.smartscript.platform.content.service.impl;

import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.SysCopyrightCert;
import com.smartscript.platform.content.domain.SysCopyrightApplication;
import com.smartscript.platform.content.mapper.SysCopyrightCertMapper;
import com.smartscript.platform.content.service.ISysCopyrightCertService;

/**
 * 版权电子证书Service业务层处理
 * 
 * 功能：
 * 1. 查询证书列表
 * 2. 查看证书详情
 * 3. 下载证书文件
 * 4. 从版权申请自动创建证书（当申请状态变为 certificated 时）
 * 
 * @author SmartScript Team
 * @since 2024-01
 */
@Service
public class SysCopyrightCertServiceImpl implements ISysCopyrightCertService
{
    private static final Logger log = LoggerFactory.getLogger(SysCopyrightCertServiceImpl.class);
    
    @Autowired
    private SysCopyrightCertMapper certMapper;

    @Override
    public List<SysCopyrightCert> selectCertList(String status, String keyword)
    {
        return certMapper.selectCertList(status, keyword);
    }

    @Override
    public List<SysCopyrightCert> selectCertListByUserId(Long userId, String status, String keyword)
    {
        return certMapper.selectCertListByUserId(userId, status, keyword);
    }

    @Override
    public SysCopyrightCert selectCertById(Long certId)
    {
        return certMapper.selectCertById(certId);
    }

    /**
     * 下载证书文件
     * 
     * 当前实现：返回Mock PDF文件用于开发测试
     * 生产环境：需要从真实文件存储系统（OSS/本地存储）或版权中心下载实际PDF
     * 
     * @param certId 证书ID
     * @return PDF文件字节数组
     * @throws RuntimeException 当证书不存在或文件不存在时抛出
     */
    @Override
    public byte[] downloadCert(Long certId)
    {
        SysCopyrightCert cert = certMapper.selectCertById(certId);
        if (cert == null || cert.getCertUrl() == null) {
            log.error("证书文件不存在, certId={}", certId);
            throw new RuntimeException("证书文件不存在");
        }
        
        // 当前使用Mock PDF实现，便于开发测试
        // 生产环境需要根据 cert.getCertUrl() 从实际存储下载PDF文件
        return generateMockPdf(cert);
    }

    /**
     * 从版权申请创建电子证书
     * 当申请状态变为 certificated 时自动调用此方法
     * 
     * @param application 版权申请对象（必须包含 certNo、certUrl、certTime 等证书信息）
     * @return 创建的记录数
     */
    @Override
    public int createCertFromApplication(SysCopyrightApplication application)
    {
        log.info("从版权申请创建电子证书: applyId={}, certNo={}", 
                 application.getApplyId(), application.getCertNo());
        
        // 检查是否已存在该作品的证书，避免重复创建
        SysCopyrightCert existingCert = certMapper.selectCertByWorkId(application.getWorkId());
        if (existingCert != null)
        {
            log.warn("作品 {} 的证书已存在，跳过创建, certId={}", application.getWorkId(), existingCert.getCertId());
            return 0;
        }
        
        // 创建证书记录
        SysCopyrightCert cert = new SysCopyrightCert();
        cert.setWorkId(application.getWorkId());
        cert.setUserId(application.getUserId());
        cert.setCertNo(application.getCertNo());
        cert.setCertUrl(application.getCertUrl());
        cert.setStatus("issued"); // 已签发
        cert.setApplyTime(application.getCreateTime()); // 使用申请创建时间作为申请时间
        cert.setCertTime(application.getCertTime()); // 证书颁发时间
        
        // 生成区块链哈希（模拟）
        String blockchainHash = generateBlockchainHash(application);
        cert.setBlockchainHash(blockchainHash);
        
        int result = certMapper.insertCert(cert);
        log.info("电子证书创建完成: certId={}, certNo={}", cert.getCertId(), cert.getCertNo());
        
        return result;
    }
    
    /**
     * 生成区块链哈希
     * 模拟实现：基于时间戳和申请ID生成十六进制哈希
     * 实际应该是提交到区块链后返回的交易哈希
     * 
     * @param application 版权申请
     * @return 区块链哈希值（0x开头的十六进制字符串）
     */
    private String generateBlockchainHash(SysCopyrightApplication application)
    {
        // 模拟生成区块链哈希
        // 实际应该是提交到区块链后返回的交易哈希
        return String.format("0x%s%s", 
            Long.toHexString(System.currentTimeMillis()),
            Integer.toHexString(application.getApplyId().intValue()));
    }

    /**
     * 生成模拟的PDF证书文件
     * 包含基本PDF结构和证书信息
     * 
     * @param cert 证书对象
     * @return PDF文件字节数组
     */
    private byte[] generateMockPdf(SysCopyrightCert cert) {
        // 最简单的PDF格式 - 包含基本PDF头和可显示文本
        StringBuilder pdf = new StringBuilder();
        pdf.append("%PDF-1.4\n");
        pdf.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        pdf.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        pdf.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /Resources 4 0 R /MediaBox [0 0 612 792] /Contents 5 0 R >>\nendobj\n");
        pdf.append("4 0 obj\n<< /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >>\nendobj\n");
        
        String content = String.format(
            "BT /F1 24 Tf 50 700 Td (Copyright Certificate) Tj ET " +
            "BT /F1 14 Tf 50 650 Td (Certificate No: %s) Tj ET " +
            "BT /F1 14 Tf 50 620 Td (Work ID: %s) Tj ET " +
            "BT /F1 14 Tf 50 590 Td (Blockchain Hash:) Tj ET " +
            "BT /F1 10 Tf 50 570 Td (%s) Tj ET " +
            "BT /F1 14 Tf 50 540 Td (Status: %s) Tj ET " +
            "BT /F1 14 Tf 50 510 Td (Apply Time: %s) Tj ET ",
            cert.getCertNo() != null ? cert.getCertNo() : "",
            cert.getWorkId() != null ? cert.getWorkId().toString() : "",
            cert.getBlockchainHash() != null ? cert.getBlockchainHash() : "N/A",
            cert.getStatus() != null ? cert.getStatus() : "",
            cert.getApplyTime() != null ? cert.getApplyTime().toString() : ""
        );
        
        String streamContent = String.format("5 0 obj\n<< /Length %d >>\nstream\n%s\nendstream\nendobj\n", 
            content.length(), content);
        pdf.append(streamContent);
        
        pdf.append("xref\n0 6\n");
        pdf.append("0000000000 65535 f \n");
        pdf.append("0000000009 00000 n \n");
        pdf.append("0000000058 00000 n \n");
        pdf.append("0000000115 00000 n \n");
        pdf.append("0000000214 00000 n \n");
        pdf.append("0000000301 00000 n \n");
        pdf.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n");
        pdf.append(String.valueOf(pdf.length()));
        pdf.append("\n%%EOF");
        
        return pdf.toString().getBytes();
    }
}
