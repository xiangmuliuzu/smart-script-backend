package com.ruoyi.web.controller.pc;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.web.service.pc.ContractManageService;
import com.smartscript.platform.trade.service.TradeContractService;
import com.smartscript.platform.trade.support.ContractHtmlSupport;
import com.smartscript.platform.trade.support.ContractPdfRenderer;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/copyright/contract")
public class ContractManageController extends BaseController {
    private final ContractManageService contractService;
    private final ContractPdfRenderer pdfRenderer;
    private final TradeContractService tradeContractService;

    public ContractManageController(ContractManageService contractService, ContractPdfRenderer pdfRenderer,
                                    TradeContractService tradeContractService) {
        this.contractService = contractService;
        this.pdfRenderer = pdfRenderer;
        this.tradeContractService = tradeContractService;
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:contract:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String status,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "10") int pageSize) {
        int page = Math.max(1, pageNum);
        int size = Math.min(200, Math.max(1, pageSize));
        List<Map<String, Object>> rows = contractService.list(status, keyword, page, size);
        TableDataInfo result = new TableDataInfo(rows, contractService.count(status, keyword));
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        return result;
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:contract:list')")
    @GetMapping("/available-orders")
    public AjaxResult availableOrders() {
        return success(contractService.availableOrders());
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:contract:query')")
    @GetMapping("/{contractId}")
    public AjaxResult getInfo(@PathVariable Long contractId) {
        try {
            return success(contractService.get(contractId));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:contract:generate')")
    @Log(title = "合同生成", businessType = BusinessType.INSERT)
    @PostMapping("/generate")
    public AjaxResult generate(@RequestBody Map<String, Object> params) {
        try {
            Object rawOrderId = params == null ? null : params.get("orderId");
            if (!(rawOrderId instanceof Number orderNumber) || params.get("templateId") == null
                    || orderNumber.longValue() <= 0 || orderNumber.doubleValue() != orderNumber.longValue()) {
                return error("请选择有效订单和合同模板");
            }
            return success(contractService.generate(orderNumber.longValue(), params.get("templateId").toString()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:contract:list')")
    @GetMapping("/{contractId}/preview")
    public AjaxResult preview(@PathVariable Long contractId) {
        try {
            Map<String, Object> contract = contractService.get(contractId);
            Object contentObj = contract.get("content");
            if (contentObj == null || String.valueOf(contentObj).isBlank()) {
                return error("合同正文缺失，无法预览");
            }
            // 预览时将已签署的签名/印章图片注入签名区域
            String html = contractService.injectSignatureImages(String.valueOf(contentObj), contract);
            return success(Map.of("content", html, "contractNo", contract.get("contractNo")));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:contract:list')")
    @GetMapping("/{contractId}/download")
    public void download(@PathVariable Long contractId, HttpServletResponse response) throws IOException {
        final Map<String, Object> contract;
        try {
            contract = contractService.get(contractId);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpStatus.NOT_FOUND, e.getMessage());
            return;
        }
        String contractNo = String.valueOf(contract.get("contractNo"));
        Object contentObj = contract.get("content");
        if (contentObj == null || String.valueOf(contentObj).isBlank()) {
            response.sendError(HttpStatus.NOT_FOUND, "合同正文缺失，无法下载");
            return;
        }
        // 下载时合成包含签名/印章的 PDF，并将 /profile/ 图片路径转为本地文件路径供渲染读取
        String html = contractService.injectSignatureImages(String.valueOf(contentObj), contract);
        byte[] pdf = pdfRenderer.render(ContractHtmlSupport.resolveProfileImages(html));
        response.setContentType("application/pdf");
        response.setContentLength(pdf.length);
        FileUtils.setAttachmentResponseHeader(response, contractNo + ".pdf");
        response.getOutputStream().write(pdf);
    }

    /**
     * 重建签署完成后的 PDF（补偿签署时生成失败、或旧数据未生成 PDF 的场景）。
     */
    @PreAuthorize("@ss.hasPermi('smartscript:copyright:contract:list')")
    @Log(title = "合同PDF重建", businessType = BusinessType.UPDATE)
    @PostMapping("/{contractId}/regenerate-pdf")
    public AjaxResult regeneratePdf(@PathVariable Long contractId) {
        try {
            tradeContractService.regenerateSignedPdf(contractId);
            return success("PDF 已重建");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        } catch (IOException e) {
            return error("PDF 重建失败：" + e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:contract:sign')")
    @Log(title = "合同签署登记", businessType = BusinessType.UPDATE)
    @PostMapping("/{contractId}/sign")
    public AjaxResult sign(@PathVariable Long contractId, @RequestBody Map<String, String> params) {
        try {
            contractService.sign(contractId, params == null ? null : params.get("party"));
            return success("签署状态登记成功");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:contract:archive')")
    @Log(title = "合同归档", businessType = BusinessType.UPDATE)
    @PostMapping("/{contractId}/archive")
    public AjaxResult archive(@PathVariable Long contractId) {
        try {
            contractService.archive(contractId);
            return success("归档成功");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:contract:cancel')")
    @Log(title = "合同作废", businessType = BusinessType.UPDATE)
    @PostMapping("/{contractId}/cancel")
    public AjaxResult cancel(@PathVariable Long contractId) {
        try {
            contractService.cancel(contractId);
            return success("作废成功");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('smartscript:copyright:contract:edit')")
    @Log(title = "合同编辑", businessType = BusinessType.UPDATE)
    @PutMapping("/{contractId}")
    public AjaxResult update(@PathVariable Long contractId, @RequestBody Map<String, Object> params) {
        try {
            String templateId = params == null ? null : (String) params.get("templateId");
            if (templateId == null || templateId.isBlank()) {
                return error("合同模板不能为空");
            }
            
            Object amountObj = params.get("amount");
            Double amount = null;
            if (amountObj instanceof Number) {
                amount = ((Number) amountObj).doubleValue();
            }
            if (amount == null || amount <= 0) {
                return error("合同金额必须大于0");
            }
            
            contractService.update(contractId, templateId, amount);
            return success("合同更新成功");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return error(e.getMessage());
        }
    }
}
