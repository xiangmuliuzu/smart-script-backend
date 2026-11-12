package com.smartscript.platform.trade.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 合同产物目录结构单测：锁定分层口径、批次命名、存量地址反推分页图与清理键，
 * 这三件事一旦跑偏，表现分别是「文件散落找不到」「重新上传删不掉旧件」「历史合同翻页图 404」。
 */
class ContractStoragePathsTest
{
    private static final LocalDate DAY = LocalDate.of(2026, 11, 11);

    @Test
    void nestsArtifactsUnderYearMonthAndContract()
    {
        assertEquals("upload/contract/2026/11/c16", ContractStoragePaths.contractDir(16L, DAY));
        assertEquals("upload/contract/2026/11/c16/u3f9a1b2c_source.pdf",
                ContractStoragePaths.sourcePdfKey(16L, "u3f9a1b2c"));
        assertEquals("upload/contract/2026/11/c16/u3f9a1b2c_page_0.png",
                ContractStoragePaths.pagePngKey(16L, "u3f9a1b2c", 0));
        assertEquals("upload/contract/2026/11/c16/signed/CT20261111A001_signed.pdf",
                ContractStoragePaths.signedPdfKey(16L, "CT20261111A001"));
        assertTrue(ContractStoragePaths.stampKey(16L, "buyer_signature", ".png")
                .startsWith("upload/contract/2026/11/c16/stamp/buyer_signature_"));
    }

    @Test
    void monthIsZeroPaddedSoDirectoriesSortByTime()
    {
        assertEquals("upload/contract/2026/03/c7", ContractStoragePaths.contractDir(7L, LocalDate.of(2026, 3, 9)));
    }

    @Test
    void rejectsMissingIdAndForgedBatch()
    {
        assertThrows(IllegalArgumentException.class, () -> ContractStoragePaths.contractDir(null, DAY));
        // 批次段参与目录拼接，放开格式等于放开路径穿越
        assertThrows(IllegalArgumentException.class,
                () -> ContractStoragePaths.sourcePdfKey(16L, "../../etc"));
    }

    @Test
    void contractNoIsStrippedBeforeBecomingFileName()
    {
        assertTrue(ContractStoragePaths.signedPdfKey(16L, "CT-001/../x").endsWith("/signed/CT-001x_signed.pdf"));
        assertEquals("upload/contract/2026/11/c16/signed/contract_signed.pdf",
                ContractStoragePaths.signedPdfKey(16L, null));
    }

    @Test
    void stampNamesDoNotCollide()
    {
        assertNotEquals(ContractStoragePaths.stampKey(16L, "buyer_seal", ".png"),
                ContractStoragePaths.stampKey(16L, "buyer_seal", ".png"));
    }

    @Test
    void urlsCarryTheProfilePrefix()
    {
        assertEquals("/profile/upload/contract/2026/11/c16/u3f9a1b2c_source.pdf",
                ContractStoragePaths.urlOf("upload/contract/2026/11/c16/u3f9a1b2c_source.pdf"));
    }

    @Test
    void derivesPageUrlFromStoredSourceUrlForBothLayouts()
    {
        // 新布局：分页图与原件同目录，共用批次前缀
        assertEquals("/profile/upload/contract/2026/11/c16/u3f9a1b2c_page_2.png",
                ContractStoragePaths.pageUrlOf("/profile/upload/contract/2026/11/c16/u3f9a1b2c_source.pdf", 2));
        // 旧平铺：老数据没搬家，必须继续按老命名反推，否则历史合同翻页图打不开
        assertEquals("/profile/contract/9/page_2.png",
                ContractStoragePaths.pageUrlOf("/profile/contract/9/source_1793084248937.pdf", 2));
        assertEquals("", ContractStoragePaths.pageUrlOf("", 0));
        assertEquals("", ContractStoragePaths.pageUrlOf("https://cdn.example.com/a.pdf", 0));
    }

    @Test
    void pageUrlIgnoresSignatureQuery()
    {
        // 出口签过名的地址也可能被回读，参数不能污染文件名推导
        assertEquals("/profile/upload/contract/2026/11/c16/u3f9a1b2c_page_0.png",
                ContractStoragePaths.pageUrlOf(
                        "/profile/upload/contract/2026/11/c16/u3f9a1b2c_source.pdf?exp=1&sig=abc", 0));
    }

    @Test
    void cleanupCoversWholeBatchButNothingElse()
    {
        List<String> keys = ContractStoragePaths.cleanupKeysOf(
                "/profile/upload/contract/2026/11/c16/u3f9a1b2c_source.pdf");
        // 一个批次前缀就能带走原件与全部分页图
        assertEquals(List.of("upload/contract/2026/11/c16/u3f9a1b2c"), keys);

        // 旧平铺目录原件与分页图前缀不同，得分两次清
        assertEquals(List.of("contract/9/source_", "contract/9/page_"),
                ContractStoragePaths.cleanupKeysOf("/profile/contract/9/source_1793084248937.pdf"));

        assertTrue(ContractStoragePaths.cleanupKeysOf("").isEmpty());
    }
}
