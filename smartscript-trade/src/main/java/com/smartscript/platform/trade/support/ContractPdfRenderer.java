package com.smartscript.platform.trade.support;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 合同 PDF 统一渲染器（openhtmltopdf）。
 * <p>
 * APP 端（TradeContractService）与管理端（ContractManageController）共用，
 * 中文字体定位顺序：系统属性 smartscript.contract.pdf.font
 * → classpath fonts/contract-cjk.ttf（部署方可自行放置，jar 内资源会释放到临时目录后加载）
 * → Windows 等线 → Linux 常见中文字体。定位结果进程内缓存。
 */
@Component
public class ContractPdfRenderer
{
    private static final Logger log = LoggerFactory.getLogger(ContractPdfRenderer.class);

    private static final String CLASSPATH_FONT = "/fonts/contract-cjk.ttf";

    private volatile Path cachedFont;

    private volatile boolean resolved;

    /**
     * 渲染 HTML 为 PDF 字节流。
     *
     * @param html 合同完整 HTML（图片路径需已通过 ContractHtmlSupport.resolveProfileImages 处理）
     */
    public byte[] render(String html) throws IOException
    {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        Path font = resolveChineseFont();
        if (font != null)
        {
            builder.useFont(font.toFile(), "ContractCjk");
        }
        builder.withHtmlContent(html, null);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        builder.toStream(output);
        builder.run();
        return output.toByteArray();
    }

    /**
     * 上传件签章落位描述：一枚签名或印章图片在某一页的摆放。
     * 坐标以页面比例(0..1)给出，x/y 为浮块左上角（y 自页面顶部向下），w 为宽度比例，
     * 高度按图片自身宽高比推算，落章时换算为页面 points。
     */
    public static final class Stamp
    {
        public final byte[] image;
        public final int page;
        public final double xFrac;
        public final double yFrac;
        public final double wFrac;
        public final String kind;

        public Stamp(byte[] image, int page, double xFrac, double yFrac, double wFrac, String kind)
        {
            this.image = image;
            this.page = page;
            this.xFrac = xFrac;
            this.yFrac = yFrac;
            this.wFrac = wFrac;
            this.kind = kind;
        }
    }

    /** 上传 PDF 页数（用于逐页渲染与前端翻页）；解析失败抛 IOException。 */
    public int countPages(byte[] pdf) throws IOException
    {
        try (PDDocument doc = PDDocument.load(pdf))
        {
            return doc.getNumberOfPages();
        }
    }

    /**
     * 将上传 PDF 的第 pageIndex（0 基）页渲染为 PNG 字节，供前端展示与拖拽定位。
     *
     * @param dpi 渲染分辨率，建议 100~150，越大越清晰但图片越大
     */
    public byte[] renderPageToPng(byte[] pdf, int pageIndex, float dpi) throws IOException
    {
        try (PDDocument doc = PDDocument.load(pdf))
        {
            if (pageIndex < 0 || pageIndex >= doc.getNumberOfPages())
            {
                throw new IOException("PDF 页码越界: " + pageIndex + "/" + doc.getNumberOfPages());
            }
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }

    /**
     * 一次性把整个 PDF 逐页渲染为 PNG 字节列表（索引即页号，0 基）。
     * <p>仅 {@code PDDocument.load} 一次并复用同一 {@link PDFRenderer}，避免逐页调用 {@code renderPageToPng}
     * 导致的 O(页数²) 全量解析开销。渲染过程中每页 BufferedImage 用完即可被 GC，堆占用不随页数线性堆积。
     */
    public List<byte[]> renderAllPagesToPng(byte[] pdf, float dpi) throws IOException
    {
        try (PDDocument doc = PDDocument.load(pdf))
        {
            int pageCount = doc.getNumberOfPages();
            PDFRenderer renderer = new PDFRenderer(doc);
            List<byte[]> pages = new ArrayList<>(pageCount);
            for (int i = 0; i < pageCount; i++)
            {
                BufferedImage image = renderer.renderImageWithDPI(i, dpi);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                pages.add(out.toByteArray());
            }
            return pages;
        }
    }

    /**
     * 在上传 PDF 上按坐标盖章：把每枚签名/印章图片绘制到指定页指定位置，输出新 PDF 字节。
     * <p>坐标换算基于页面 CropBox（多数合同 CropBox==MediaBox 且原点为 0，此时精确）；
     * PDF 用户空间原点在左下角，故 y 需自顶部翻转为自底部。页面 /Rotate≠0 暂不特殊处理。
     */
    public byte[] stampImages(byte[] pdf, List<Stamp> stamps) throws IOException
    {
        try (PDDocument doc = PDDocument.load(pdf))
        {
            int pageCount = doc.getNumberOfPages();
            for (Stamp stamp : stamps)
            {
                if (stamp.image == null || stamp.image.length == 0)
                {
                    continue;
                }
                if (stamp.page < 0 || stamp.page >= pageCount)
                {
                    log.warn("签章页码越界，已跳过: kind={} page={}/{}", stamp.kind, stamp.page, pageCount);
                    continue;
                }
                PDPage page = doc.getPage(stamp.page);
                PDImageXObject xobj = PDImageXObject.createFromByteArray(doc, stamp.image, stamp.kind);
                PDRectangle box = page.getCropBox() != null ? page.getCropBox() : page.getMediaBox();
                float llx = box.getLowerLeftX();
                float lly = box.getLowerLeftY();
                float boxW = box.getWidth();
                float boxH = box.getHeight();
                float drawW = (float) (stamp.wFrac * boxW);
                float drawH = drawW * ((float) xobj.getHeight() / (float) Math.max(1, xobj.getWidth()));
                float drawX = llx + (float) stamp.xFrac * boxW;
                float topY = lly + (1f - (float) stamp.yFrac) * boxH;
                float drawY = topY - drawH;
                try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                        PDPageContentStream.AppendMode.APPEND, true, true))
                {
                    cs.drawImage(xobj, drawX, drawY, drawW, drawH);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** 中文字体定位（进程内缓存）。找不到返回 null，由 openhtmltopdf 使用内置兜底字体。 */
    private Path resolveChineseFont() throws IOException
    {
        if (resolved)
        {
            return cachedFont;
        }
        synchronized (this)
        {
            if (resolved)
            {
                return cachedFont;
            }
            cachedFont = findChineseFont();
            resolved = true;
            if (cachedFont != null)
            {
                log.info("合同 PDF 中文字体: {}", cachedFont);
            }
            else
            {
                log.warn("未找到合同 PDF 中文字体，中文内容可能无法正确渲染。"
                        + "可通过系统属性 smartscript.contract.pdf.font 或 classpath fonts/contract-cjk.ttf 指定");
            }
        }
        return cachedFont;
    }

    private Path findChineseFont() throws IOException
    {
        String configured = System.getProperty("smartscript.contract.pdf.font");
        if (configured != null && !configured.isBlank())
        {
            Path configuredPath = Paths.get(configured);
            if (Files.isRegularFile(configuredPath))
            {
                return configuredPath;
            }
        }
        Path fromClasspath = extractClasspathFont();
        if (fromClasspath != null)
        {
            return fromClasspath;
        }
        List<Path> candidates = new ArrayList<>();
        candidates.add(Paths.get("C:/Windows/Fonts/Deng.ttf"));
        candidates.add(Paths.get("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttf"));
        candidates.add(Paths.get("/usr/share/fonts/truetype/arphic/uming.ttf"));
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
    }

    /** 将 classpath 打包字体释放到临时目录（jar 内资源无法直接以文件形式交给 openhtmltopdf）。 */
    private Path extractClasspathFont() throws IOException
    {
        try (InputStream in = ContractPdfRenderer.class.getResourceAsStream(CLASSPATH_FONT))
        {
            if (in == null)
            {
                return null;
            }
            Path target = Files.createTempFile("contract-cjk", ".ttf");
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();
            return target;
        }
    }
}
