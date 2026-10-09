package com.ruoyi.web.controller.pc;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.web.service.pc.ContractManageService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pc/copyright/contract")
public class ContractManageController extends BaseController {
    private final ContractManageService contractService;

    public ContractManageController(ContractManageService contractService) {
        this.contractService = contractService;
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
            return success(Map.of("content", contract.get("content"), "contractNo", contract.get("contractNo")));
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
        byte[] pdf = buildContractPdf(String.valueOf(contract.get("content")));
        response.setContentType("application/pdf");
        response.setContentLength(pdf.length);
        FileUtils.setAttachmentResponseHeader(response, contractNo + ".pdf");
        response.getOutputStream().write(pdf);
    }

    private byte[] buildContractPdf(String html) throws IOException {
        Path fontPath = findChineseFont();
        if (fontPath == null) {
            throw new IOException("未找到中文字体，请配置 smartscript.contract.pdf.font");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new PdfRendererBuilder().useFastMode().useFont(fontPath.toFile(), "ContractCjk")
                .withHtmlContent(html, null).toStream(output).run();
        return output.toByteArray();
    }

    private Path findChineseFont() {
        String configuredFont = System.getProperty("smartscript.contract.pdf.font");
        List<Path> candidates = new ArrayList<>();
        if (configuredFont != null && !configuredFont.isBlank()) candidates.add(Path.of(configuredFont));
        candidates.add(Path.of("C:/Windows/Fonts/Deng.ttf"));
        candidates.add(Path.of("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttf"));
        candidates.add(Path.of("/usr/share/fonts/truetype/arphic/uming.ttf"));
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
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
