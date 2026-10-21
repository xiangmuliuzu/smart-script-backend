package com.smartscript.platform.trade.support;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
