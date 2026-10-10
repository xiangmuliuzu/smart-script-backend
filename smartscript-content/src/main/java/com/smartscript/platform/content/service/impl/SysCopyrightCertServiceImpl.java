package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.SysCopyrightCert;
import com.smartscript.platform.content.mapper.SysCopyrightCertMapper;
import com.smartscript.platform.content.service.ISysCopyrightCertService;

@Service
public class SysCopyrightCertServiceImpl implements ISysCopyrightCertService
{
    @Autowired
    private SysCopyrightCertMapper certMapper;

    @Override
    public List<SysCopyrightCert> selectCertList(String status, String keyword)
    {
        return certMapper.selectCertList(status, keyword);
    }

    @Override
    public SysCopyrightCert selectCertById(Long certId)
    {
        return certMapper.selectCertById(certId);
    }

    @Override
    public byte[] downloadCert(Long certId)
    {
        SysCopyrightCert cert = certMapper.selectCertById(certId);
        if (cert == null || cert.getCertUrl() == null) {
            throw new RuntimeException("证书文件不存在");
        }
        // TODO: 实际从文件存储或版权中心下载PDF
        // 当前返回简单的可查看PDF mock数据
        return generateMockPdf(cert);
    }

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
