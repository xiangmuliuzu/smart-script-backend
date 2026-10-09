package com.ruoyi.web.controller.pc;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.smartscript.platform.content.domain.SysCopyrightCert;
import com.smartscript.platform.content.service.ISysCopyrightCertService;

/** PC 电子证书管理 */
@RestController
@RequestMapping("/pc/copyright/cert")
public class CopyrightCertController extends BaseController
{
    @Autowired
    private ISysCopyrightCertService certService;

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:cert')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword)
    {
        String safeStatus = "pending".equals(status) || "issued".equals(status) || "expired".equals(status) 
                ? status : null;
        String safeKeyword = StringUtils.left(StringUtils.trimToNull(keyword), 100);
        startPage();
        List<SysCopyrightCert> rows = certService.selectCertList(safeStatus, safeKeyword);
        return getDataTable(rows);
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:cert')")
    @GetMapping("/{certId}")
    public AjaxResult detail(@PathVariable Long certId)
    {
        SysCopyrightCert cert = certService.selectCertById(certId);
        return cert != null ? success(cert) : error("证书不存在");
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:cert')")
    @GetMapping("/{certId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long certId)
    {
        try {
            byte[] pdfBytes = certService.downloadCert(certId);
            SysCopyrightCert cert = certService.selectCertById(certId);
            String filename = "证书_" + (cert != null ? cert.getCertNo() : certId) + ".pdf";
            ByteArrayResource resource = new ByteArrayResource(pdfBytes);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(pdfBytes.length)
                    .body(resource);
        } catch (Exception e) {
            logger.error("证书下载失败: certId={}", certId, e);
            return ResponseEntity.status(500).build();
        }
    }
}
