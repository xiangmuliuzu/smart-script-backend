package com.smartscript.platform.trade.support;

import com.ruoyi.common.constant.Constants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 合同产物在 /profile 存储下的目录结构。
 *
 * <p>统一收在 {@code upload/contract/{yyyy}/{MM}/c{contractId}/} 下，与运行期资源同一棵子树，
 * 便于按时间归档、按合同定位；批次、签章、最终 PDF 各自再分一层：</p>
 *
 * <pre>
 * upload/contract/2026/11/c16/u3f9a1b2c_source.pdf   上传的原件
 * upload/contract/2026/11/c16/u3f9a1b2c_page_0.png   原件分页图
 * upload/contract/2026/11/c16/stamp/buyer_signature_ab12cd34.png
 * upload/contract/2026/11/c16/signed/CT20261116_signed.pdf
 * </pre>
 *
 * <p>上传件用批次号做文件名前缀而不是再建一层目录：ProfileStorageService#deletePrefix
 * 的本地实现是「目录内按文件名前缀删除」，重新上传时一次就能清掉上一批次的原件与分页图，
 * 不会留下空目录，也不会误删同合同的签章图与已签署 PDF。</p>
 *
 * <p>旧版平铺在 {@code contract/{contractId}/} 的文件不在这里生成，只负责识别：
 * 数据库存的是完整相对地址，读取一律按存量地址走，所以历史合同不改路径也照样能看。</p>
 */
public final class ContractStoragePaths
{
    /** 合同产物根目录（相对 profile 根）。 */
    public static final String CONTRACT_ROOT = "upload/contract";

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");

    /** 相对地址前缀，例如 /profile/upload/contract/...。 */
    private static final String URL_PREFIX = Constants.RESOURCE_PREFIX + "/";

    private ContractStoragePaths()
    {
    }

    /** 合同产物目录：upload/contract/{yyyy}/{MM}/c{contractId}。 */
    public static String contractDir(Long contractId, LocalDate date)
    {
        if (contractId == null || contractId <= 0)
        {
            throw new IllegalArgumentException("contract id is required");
        }
        LocalDate day = date == null ? LocalDate.now() : date;
        return CONTRACT_ROOT + "/" + day.format(YEAR) + "/" + day.format(MONTH) + "/c" + contractId;
    }

    /** 合同产物目录（按当天归档）。 */
    public static String contractDir(Long contractId)
    {
        return contractDir(contractId, LocalDate.now());
    }

    /** 新生成一个上传批次号。文件名不再带可猜的时间戳，避免按目录枚举。 */
    public static String newBatch()
    {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /** 上传批次内产物的文件名前缀，同时是清理该批次时传给 deletePrefix 的键。 */
    public static String batchPrefix(Long contractId, String batch)
    {
        return batchPrefix(contractDir(contractId), batch);
    }

    /** 已知目录时的批次前缀（清理存量批次目录用）。 */
    public static String batchPrefix(String dir, String batch)
    {
        requireBatch(batch);
        return dir + "/" + batch + "_";
    }

    /** 上传原件的对象 key。 */
    public static String sourcePdfKey(Long contractId, String batch)
    {
        return batchPrefix(contractId, batch) + "source.pdf";
    }

    /** 原件第 index 页分页图的对象 key（页码从 0 开始）。 */
    public static String pagePngKey(Long contractId, String batch, int index)
    {
        return batchPrefix(contractId, batch) + "page_" + index + ".png";
    }

    /**
     * 签名/印章图片的对象 key。
     *
     * @param prefix 方别 + 种类，如 buyer_signature、seller_seal
     * @param ext    含点扩展名，如 .png
     */
    public static String stampKey(Long contractId, String prefix, String ext)
    {
        return contractDir(contractId) + "/stamp/" + prefix + "_" + token() + (ext == null ? ".png" : ext);
    }

    /** 双方签署完成的最终 PDF 的对象 key。 */
    public static String signedPdfKey(Long contractId, String contractNo)
    {
        String no = contractNo == null ? "" : contractNo.trim();
        no = no.replaceAll("[^A-Za-z0-9_-]", "");
        return contractDir(contractId) + "/signed/" + (no.isEmpty() ? "contract" : no) + "_signed.pdf";
    }

    /** 对象 key 转成入库/下发的相对地址。 */
    public static String urlOf(String key)
    {
        return URL_PREFIX + key;
    }

    /**
     * 由存量原件地址推导其第 index 页分页图地址。
     *
     * <p>新布局与旧平铺都在原件同目录下，仅文件名不同：新为 {@code u{batch}_page_i.png}，
     * 旧为 {@code page_i.png}。分页图不入库，只能这样反推，所以两种布局都要认。</p>
     */
    public static String pageUrlOf(String sourcePdfUrl, int index)
    {
        String key = localKey(sourcePdfUrl);
        int slash = key.lastIndexOf('/');
        if (slash <= 0)
        {
            return "";
        }
        String dir = key.substring(0, slash);
        String name = key.substring(slash + 1);
        int head = name.indexOf('_');
        // 新布局文件名形如 u3f9a1b2c_source.pdf，批次段即分页图的共同前缀；旧平铺分页图就叫 page_i.png
        String prefix = head > 0 && name.substring(0, head).matches("u[0-9a-f]{8}")
                ? name.substring(0, head + 1) : "";
        return URL_PREFIX + dir + "/" + prefix + "page_" + index + ".png";
    }

    /**
     * 重新上传/切回模板前需要清理的键列表（每项交给 deletePrefix）。
     *
     * <p>新布局一个批次前缀即可清干净；旧平铺目录的原件与分页图前缀不同，各清一次。</p>
     */
    public static List<String> cleanupKeysOf(String sourcePdfUrl)
    {
        List<String> keys = new ArrayList<>(2);
        String key = localKey(sourcePdfUrl);
        int slash = key.lastIndexOf('/');
        if (slash <= 0)
        {
            return keys;
        }
        String dir = key.substring(0, slash);
        String name = key.substring(slash + 1);
        int head = name.indexOf('_');
        if (head > 0 && name.substring(0, head).matches("u[0-9a-f]{8}"))
        {
            keys.add(dir + "/" + name.substring(0, head));
        }
        else
        {
            keys.add(dir + "/source_");
            keys.add(dir + "/page_");
        }
        return keys;
    }

    private static void requireBatch(String batch)
    {
        if (batch == null || !batch.matches("u[0-9a-f]{8}"))
        {
            throw new IllegalArgumentException("invalid contract upload batch");
        }
    }

    private static String token()
    {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
    }

    /** 去掉 /profile 前缀与可能的查询串，得到对象 key；非本域地址返回空串。 */
    private static String localKey(String url)
    {
        if (url == null)
        {
            return "";
        }
        String value = url.trim();
        int query = value.indexOf('?');
        if (query >= 0)
        {
            value = value.substring(0, query);
        }
        return value.startsWith(URL_PREFIX) ? value.substring(URL_PREFIX.length()) : "";
    }
}
