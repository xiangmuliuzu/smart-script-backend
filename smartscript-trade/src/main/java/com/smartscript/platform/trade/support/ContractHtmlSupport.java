package com.smartscript.platform.trade.support;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Paths;

/**
 * 合同 HTML 统一支撑：模板生成、签名/印章注入、profile 图片路径解析。
 * <p>
 * APP 端（TradeContractService）与管理端（ContractManageService）共用，
 * 消除"双模板双注入"的分叉维护（历史上曾在独立副本上出现 Map key 笔误导致 PDF 缺图）。
 * <p>
 * 模板签名区含占位注释；对管理端历史生成的旧模板（无占位符）按签名区 strong 标记定位插入。
 */
public final class ContractHtmlSupport
{
    private ContractHtmlSupport()
    {
    }

    /**
     * 生成版权授权/转让合同 HTML（签名区含占位注释）。
     *
     * @param templateId TPL_COPYRIGHT_TRANSFER / TPL_COPYRIGHT_LICENSE
     */
    public static String buildContractHtml(String contractNo, String orderNo, String workTitle,
                                           String buyerName, String sellerName, BigDecimal amount,
                                           String licenseType, String templateId)
    {
        boolean isTransfer = "TPL_COPYRIGHT_TRANSFER".equals(templateId);
        String title = isTransfer ? "版权转让合同" : "版权授权合同";

        // openhtmltopdf 不支持 flex，签名区双栏用 table 布局；页边距交给 @page
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\" />"
                + "<title>" + title + "</title>"
                + "<style>"
                + "@page { size: A4; margin: 22mm 18mm; }"
                + "body { font-family: 'ContractCjk', SimSun, serif; font-size: 14px; line-height: 1.8; margin: 0; }"
                + "h1 { text-align: center; font-size: 24px; margin-bottom: 30px; font-weight: bold; }"
                + ".contract-no { text-align: center; font-size: 12px; color: #666; margin-bottom: 20px; }"
                + ".section { margin-bottom: 20px; }"
                + ".section-title { font-weight: bold; font-size: 16px; margin-bottom: 10px; }"
                + ".info-row { margin-bottom: 8px; }"
                + ".label { display: inline-block; width: 120px; font-weight: bold; }"
                + ".content { margin: 15px 0; text-indent: 2em; text-align: justify; }"
                + ".signature { margin-top: 60px; }"
                + ".signature-row { margin-top: 40px; display: table; width: 100%; table-layout: fixed; }"
                + ".signature-box { display: table-cell; width: 50%; vertical-align: top; padding-right: 24px; }"
                + ".signature-image { margin-top: 10px; }"
                + "</style></head><body>"
                + "<h1>" + title + "</h1>"
                + "<div class=\"contract-no\">合同编号：" + escape(contractNo) + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">基本信息</div>"
                + "<div class=\"info-row\"><span class=\"label\">订单编号：</span>" + escape(orderNo) + "</div>"
                + "<div class=\"info-row\"><span class=\"label\">作品名称：</span>" + escape(workTitle) + "</div>"
                + "<div class=\"info-row\"><span class=\"label\">甲方（买方）：</span>" + escape(buyerName) + "</div>"
                + "<div class=\"info-row\"><span class=\"label\">乙方（卖方）：</span>" + escape(sellerName) + "</div>"
                + "<div class=\"info-row\"><span class=\"label\">合同金额：</span>人民币 " + escape(amount) + " 元</div>"
                + "<div class=\"info-row\"><span class=\"label\">许可类型：</span>" + escape(licenseType) + "</div>"
                + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">第一条 合同标的</div>"
                + "<div class=\"content\">甲乙双方经友好协商，就作品《" + escape(workTitle) + "》的版权"
                + (isTransfer ? "转让" : "授权使用") + "事宜达成如下协议。</div>"
                + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">第二条 " + (isTransfer ? "转让内容" : "授权范围") + "</div>"
                + "<div class=\"content\">"
                + (isTransfer
                    ? "乙方同意将作品的全部著作权（包括但不限于复制权、发行权、信息网络传播权、改编权等）转让给甲方。转让后，甲方享有完整的著作权，乙方不再保留任何权利。"
                    : "乙方授权甲方在约定范围内使用作品，授权类型为：" + escape(licenseType) + "。甲方应在授权范围内使用作品，不得超范围使用或转授权给第三方。")
                + "</div>"
                + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">第三条 合同金额及支付</div>"
                + "<div class=\"content\">甲方应向乙方支付" + (isTransfer ? "转让" : "授权") + "费用人民币 "
                + escape(amount) + " 元。该费用由甲方按平台托管流程支付，在订单完成后由平台结算给乙方。</div>"
                + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">第四条 权利与义务</div>"
                + "<div class=\"content\">1. 乙方保证对作品享有完整的著作权，不存在侵犯第三方权益的情形。</div>"
                + "<div class=\"content\">2. 甲方应按约定支付费用，并在授权范围内合法使用作品。</div>"
                + "<div class=\"content\">3. 双方应遵守国家相关法律法规，履行合同义务。</div>"
                + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">第五条 违约责任</div>"
                + "<div class=\"content\">任何一方违反本合同约定的，应承担违约责任，赔偿对方因此遭受的损失。</div>"
                + "</div>"
                + "<div class=\"section\">"
                + "<div class=\"section-title\">第六条 争议解决</div>"
                + "<div class=\"content\">因本合同引起的争议，双方应友好协商解决；协商不成的，可向平台所在地人民法院提起诉讼。</div>"
                + "</div>"
                + "<div class=\"signature\">"
                + "<div class=\"signature-row\">"
                + "<div class=\"signature-box\">"
                // 签名区只留签字盖章空间，不预填姓名（姓名在基本信息区展示）
                + "<strong>甲方（买方）签字：</strong>"
                + "<div class=\"signature-image\"><!-- buyer_signature_placeholder --></div>"
                + "<div class=\"signature-image\"><!-- buyer_seal_placeholder --></div>"
                + "<br/>签署时间：___________"
                + "</div>"
                + "<div class=\"signature-box\">"
                + "<strong>乙方（卖方）签字：</strong>"
                + "<div class=\"signature-image\"><!-- seller_signature_placeholder --></div>"
                + "<div class=\"signature-image\"><!-- seller_seal_placeholder --></div>"
                + "<br/>签署时间：___________"
                + "</div>"
                + "</div>"
                + "</div>"
                + "</body></html>";
    }

    /**
     * 在合同 HTML 的签名区域注入双方签名/印章图片。
     * <p>
     * 兼容两种模板：新模板含占位注释（直接替换）；
     * 旧模板无占位符（在各方 signature-box 的"签署时间"前插入图片块）。
     */
    public static String injectSignatures(String html, String buyerSig, String buyerSeal,
                                          String sellerSig, String sellerSeal)
    {
        if (html == null || html.isBlank())
        {
            return html;
        }
        boolean hasPlaceholder = html.contains("<!-- buyer_signature_placeholder -->")
                || html.contains("<!-- seller_signature_placeholder -->");
        if (hasPlaceholder)
        {
            // 新模板：替换签名区占位符
            if (notBlank(buyerSig))
            {
                html = html.replace("<!-- buyer_signature_placeholder -->", img(buyerSig, 200, 80));
            }
            if (notBlank(buyerSeal))
            {
                html = html.replace("<!-- buyer_seal_placeholder -->", img(buyerSeal, 100, 100));
            }
            if (notBlank(sellerSig))
            {
                html = html.replace("<!-- seller_signature_placeholder -->", img(sellerSig, 200, 80));
            }
            if (notBlank(sellerSeal))
            {
                html = html.replace("<!-- seller_seal_placeholder -->", img(sellerSeal, 100, 100));
            }
            return html;
        }
        // 旧模板（管理端历史生成）：按签名区 strong 标记定位后插入（避免命中基本信息区的同名文本）
        html = insertImagesBeforeSignTime(html, buyerSig, buyerSeal, "<strong>甲方（买方）：</strong>");
        html = insertImagesBeforeSignTime(html, sellerSig, sellerSeal, "<strong>乙方（卖方）：</strong>");
        return html;
    }

    /** 在指定方签名区"签署时间"前插入签名/印章图片。 */
    private static String insertImagesBeforeSignTime(String html, String signature, String seal, String partyMarker)
    {
        boolean hasSig = notBlank(signature);
        boolean hasSeal = notBlank(seal);
        if (!hasSig && !hasSeal)
        {
            return html;
        }
        int marker = html.indexOf(partyMarker);
        if (marker < 0)
        {
            return html;
        }
        int signTime = html.indexOf("签署时间", marker);
        if (signTime < 0)
        {
            return html;
        }
        StringBuilder imgs = new StringBuilder();
        if (hasSig)
        {
            imgs.append(img(signature, 200, 80));
        }
        if (hasSeal)
        {
            imgs.append(img(seal, 100, 100));
        }
        return html.substring(0, signTime) + imgs + html.substring(signTime);
    }

    /**
     * PDF 渲染无 baseUri，将 /profile/ 相对路径替换为本地文件绝对路径供离线读取。
     */
    public static String resolveProfileImages(String html)
    {
        if (html == null || html.isEmpty())
        {
            return html;
        }
        URI base = Paths.get(com.ruoyi.common.config.RuoYiConfig.getProfile())
                .toAbsolutePath().normalize().toUri();
        return html.replace("src=\"/profile/", "src=\"" + base + "/");
    }

    private static String img(String url, int maxWidth, int maxHeight)
    {
        return "<img src=\"" + url + "\" style=\"max-width:" + maxWidth + "px;max-height:" + maxHeight
                + "px;margin-top:8px;\" />";
    }

    private static boolean notBlank(String value)
    {
        return value != null && !value.isEmpty();
    }

    private static String escape(Object value)
    {
        if (value == null) return "—";
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
