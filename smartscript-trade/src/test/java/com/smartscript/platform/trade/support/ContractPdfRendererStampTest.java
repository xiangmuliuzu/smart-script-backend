package com.smartscript.platform.trade.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

/**
 * 上传件落章单测：锁定「rot 角度真的进了 PDF，并且是绕章心转」。
 * 摆放层给了旋转手柄、库里也存了 rot，但渲染端一旦漏掉角度，
 * 表现就是「预览里斜的、签完的 PDF 是正的」——用户以为签错了位置，只能重签。
 */
class ContractPdfRendererStampTest
{
    /** 居中横条：上下留白对称，墨迹包围盒中心即章心；旋转 90° 后包围盒横竖互换。 */
    private static byte[] barPng() throws Exception
    {
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 200, 100);
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 30, 200, 40);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static byte[] blankPdf() throws Exception
    {
        try (PDDocument doc = new PDDocument())
        {
            doc.addPage(new PDPage(PDRectangle.A4));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** 渲染首页并统计深色像素的包围盒（int[]{minX,minY,maxX,maxY,中心x,中心y}）。 */
    private static int[] inkBox(byte[] pdf) throws Exception
    {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf)))
        {
            BufferedImage page = new PDFRenderer(doc).renderImageWithDPI(0, 72f);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = -1, maxY = -1;
            for (int y = 0; y < page.getHeight(); y++)
            {
                for (int x = 0; x < page.getWidth(); x++)
                {
                    int rgb = page.getRGB(x, y) & 0xFFFFFF;
                    int lum = (rgb >> 16) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
                    if (lum < 300)
                    {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            assertTrue(maxX > minX && maxY > minY, "墨迹缺失，落章未生效");
            return new int[] { minX, minY, maxX, maxY, (minX + maxX) / 2, (minY + maxY) / 2 };
        }
    }

    private byte[] stampWithRotation(double rotation) throws Exception
    {
        ContractPdfRenderer.Stamp stamp = new ContractPdfRenderer.Stamp(
                barPng(), 0, 0.2, 0.3, 0.4, rotation, "seal");
        return new ContractPdfRenderer().stampImages(blankPdf(), List.of(stamp));
    }

    @Test
    void zeroRotationDrawsWideBar() throws Exception
    {
        int[] box = inkBox(stampWithRotation(0));
        assertTrue(box[2] - box[0] > 2 * (box[3] - box[1]), "未旋转时应是横条：宽远大于高");
    }

    @Test
    void ninetyRotationTurnsBarVertical() throws Exception
    {
        int[] box = inkBox(stampWithRotation(90));
        assertTrue(box[3] - box[1] > 2 * (box[2] - box[0]), "旋转 90° 后应竖起来：高远大于宽");
    }

    @Test
    void rotationPivotsOnStampCenter() throws Exception
    {
        int[] flat = inkBox(stampWithRotation(0));
        int[] turned = inkBox(stampWithRotation(90));
        // 允许 6px 抗锯齿误差：中心不重合说明绕的是左上角，摆放层与成品会对不上
        assertEquals(flat[4], turned[4], 6, "旋转后横向中心应保持不变");
        assertEquals(flat[5], turned[5], 6, "旋转后纵向中心应保持不变");
    }
}
