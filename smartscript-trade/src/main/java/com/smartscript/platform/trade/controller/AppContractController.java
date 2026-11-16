package com.smartscript.platform.trade.controller;

import com.ruoyi.common.core.domain.AjaxResult;
import com.smartscript.platform.trade.domain.SysOrder;
import com.smartscript.platform.trade.service.TradeContractService;
import com.smartscript.platform.trade.service.TradeOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * APP 端合同接口。
 * <p>
 * 基础路径：/api/v1/trade/contracts
 * <ul>
 *   <li>GET /list — 合同列表（可按买卖角色与状态过滤，分页）</li>
 *   <li>GET /{orderId} — 按订单取合同详情</li>
 *   <li>POST /{contractId}/sign — 签署合同（multipart：signatureImage 可选、sealImage 可选、placements 上传件拖拽定位 JSON 可选）</li>
 *   <li>GET /templates — 平台合同模板目录（可按 contractId 返回贴合默认条款）</li>
 *   <li>PUT /{contractId}/content — 编辑合同正文条款（进入签署即锁定）</li>
 *   <li>POST /{contractId}/source-pdf — 上传 PDF 替换模板（进入签署即锁定）</li>
 *   <li>PUT /{contractId}/source-pdf/off — 从上传件切回平台模板（进入签署即锁定）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/trade/contracts")
public class AppContractController
{
    @Autowired
    private TradeContractService contractService;

    @Autowired
    private TradeOrderService orderService;

    /**
     * 合同列表。
     * <p>
     * role=seller 取我作为卖方的合同，role=buyer 取我作为买方的合同，为空取两者并集。
     * 列表不返回合同正文（大字段），查看详情仍走 /{orderId}。
     */
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(value = "role", required = false) String role,
                           @RequestParam(value = "status", required = false) String status,
                           @RequestParam(value = "keyword", required = false) String keyword,
                           @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                           @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize)
    {
        try
        {
            return success(contractService.listContracts(currentUserId(), role, status, keyword, pageNum, pageSize));
        }
        catch (Exception e)
        {
            return error(e.getMessage());
        }
    }

    /**
     * 按订单 ID 取合同详情。
     */
    @GetMapping("/{orderId}")
    public AjaxResult getByOrderId(@PathVariable Long orderId)
    {
        try
        {
            // 仅订单买方/卖方可查看合同（含双方签名/印章与金额），与订单详情接口权限口径一致
            Long currentUserId = currentUserId();
            SysOrder order = orderService.selectOrderById(orderId);
            if (order == null)
            {
                return error("订单不存在");
            }
            if (!currentUserId.equals(order.getBuyerId()) && !currentUserId.equals(order.getSellerId()))
            {
                return error("您不是该订单的买方或卖方");
            }

            Map<String, Object> contract = contractService.getByOrderId(orderId);
            if (contract == null)
            {
                return error("该订单尚未生成合同");
            }
            return success(contract);
        }
        catch (Exception e)
        {
            return error(e.getMessage());
        }
    }

    /**
     * 签署合同。
     * <p>
     * 请求参数（multipart/form-data）：
     * <ul>
     *   <li>signatureImage — 手写签名图片（可选）</li>
     *   <li>sealImage — 印章图片（可选）</li>
     *   <li>placements — 上传件签章定位 JSON 数组 [{kind,page,x,y,w,h}]（source_type=uploaded 时必传）</li>
     * </ul>
     * 至少传一项签名或印章。
     */
    @PostMapping("/{contractId}/sign")
    public AjaxResult sign(@PathVariable Long contractId,
                           @RequestParam(value = "signatureImage", required = false) MultipartFile signatureImage,
                           @RequestParam(value = "sealImage", required = false) MultipartFile sealImage,
                           @RequestParam(value = "placements", required = false) String placements)
    {
        try
        {
            Map<String, Object> result = contractService.sign(contractId, signatureImage, sealImage, placements);
            return success(result);
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return error(e.getMessage());
        }
        catch (Exception e)
        {
            return error("签署失败：" + e.getMessage());
        }
    }

    /**
     * 平台合同模板目录（供前端切换模板时填充默认条款）。
     *
     * @param contractId 可选，传入时按该合同的作品/许可/金额生成贴合默认条款
     */
    @GetMapping("/templates")
    public AjaxResult templates(@RequestParam(value = "contractId", required = false) Long contractId)
    {
        try
        {
            return success(contractService.listContractTemplates(contractId));
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return error(e.getMessage());
        }
        catch (Exception e)
        {
            return error("获取模板失败：" + e.getMessage());
        }
    }

    /**
     * 编辑合同正文条款（未进入签署时可改，任一方已签即锁定）。
     * <p>
     * 请求体：{ templateId, clausesText }；锁定/非法参数返回 code=500 + message，前端据此弹提示。
     */
    @PutMapping("/{contractId}/content")
    public AjaxResult updateContent(@PathVariable Long contractId,
                                    @RequestBody Map<String, String> body)
    {
        try
        {
            String templateId = body == null ? null : body.get("templateId");
            String clausesText = body == null ? null : body.get("clausesText");
            return success(contractService.updateContractContent(contractId, templateId, clausesText));
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return error(e.getMessage());
        }
        catch (Exception e)
        {
            return error("保存合同内容失败：" + e.getMessage());
        }
    }

    /**
     * 上传 PDF 替换平台模板（multipart：file）。未签署前买卖双方均可上传覆盖，以最后一次为准；上传件不可在线编辑。
     */
    @PostMapping("/{contractId}/source-pdf")
    public AjaxResult uploadSourcePdf(@PathVariable Long contractId,
                                      @RequestParam(value = "file", required = false) MultipartFile file)
    {
        try
        {
            return success(contractService.uploadSourcePdf(contractId, file));
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return error(e.getMessage());
        }
        catch (Exception e)
        {
            return error("上传合同失败：" + e.getMessage());
        }
    }

    /**
     * 从上传件切回平台模板（请求体：{ templateId }）。仅在未签署时可用。
     */
    @PutMapping("/{contractId}/source-pdf/off")
    public AjaxResult switchToTemplate(@PathVariable Long contractId,
                                       @RequestBody Map<String, String> body)
    {
        try
        {
            String templateId = body == null ? null : body.get("templateId");
            return success(contractService.switchToTemplate(contractId, templateId));
        }
        catch (IllegalArgumentException | IllegalStateException e)
        {
            return error(e.getMessage());
        }
        catch (Exception e)
        {
            return error("切回模板失败：" + e.getMessage());
        }
    }

    private AjaxResult success(Object data)
    {
        AjaxResult result = new AjaxResult();
        result.put("code", 200);
        result.put("message", "操作成功");
        result.put("data", data);
        return result;
    }

    private AjaxResult error(String message)
    {
        AjaxResult result = new AjaxResult();
        result.put("code", 500);
        result.put("message", message);
        return result;
    }

    /**
     * 取当前登录用户 ID（APP 链路 principal 为 AppIdentityContext）。
     */
    private Long currentUserId()
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof com.smartscript.platform.user.security.AppIdentityContext identity)
        {
            return identity.getUserId();
        }
        throw new RuntimeException("未登录或身份上下文异常");
    }
}
