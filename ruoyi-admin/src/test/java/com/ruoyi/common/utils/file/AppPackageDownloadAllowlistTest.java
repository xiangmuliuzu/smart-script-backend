package com.ruoyi.common.utils.file;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * 下载白名单：应用内更新只放行 {@code app/} 目录下的 apk，别处一概不放。
 *
 * <p>放在 ruoyi-admin 测试模块：ruoyi-common 本身没有引入测试依赖，
 * 而被测代码与授权接口依赖它，这里用同一份产物覆盖。</p>
 */
class AppPackageDownloadAllowlistTest
{
    @Test
    void allowsAppPackageUnderAppDirectory()
    {
        assertTrue(FileUtils.checkAllowDownload("app/script-app-1.0.1-2.apk"));
        assertTrue(FileUtils.checkAllowDownload("/app/script-app-1.0.1-2.apk"));
        assertTrue(FileUtils.checkAllowDownload("app/2026/11/script-app.APK"));
    }

    @Test
    void deniesAppPackageOutsideAppDirectory()
    {
        assertFalse(FileUtils.checkAllowDownload("upload/2026/11/17/sneaky.apk"));
        assertFalse(FileUtils.checkAllowDownload("contract/9/sneaky.apk"));
        assertFalse(FileUtils.checkAllowDownload("appdata/sneaky.apk"));
        assertFalse(FileUtils.checkAllowDownload("app/sneaky.exe"));
    }

    @Test
    void keepsRejectingTraversalAndUnknownTypes()
    {
        assertFalse(FileUtils.checkAllowDownload("app/../app/sneaky.apk"));
        assertFalse(FileUtils.checkAllowDownload("upload/2026/11/17/unknown.bin"));
        assertTrue(FileUtils.checkAllowDownload("upload/2026/11/17/cover.png"));
    }
}
