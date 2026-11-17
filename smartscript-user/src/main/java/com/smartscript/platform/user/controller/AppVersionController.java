package com.smartscript.platform.user.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.system.service.ISysConfigService;
import com.smartscript.platform.api.AppApiResponse;

/**
 * App 版本检查（不上架自更新）。
 *
 * 公开接口：App 启动时、以及用户点「检查更新」时调用，不依赖登录态——
 * 冷启动的登录页还没有 App Token，若要求身份则永远拿不到版本信息。
 *
 * 配置全部来自 RuoYi 参数配置表 sys_config（管理端「系统管理 → 参数设置」可改，
 * 改完无需重启后端；值为空时回退默认值）：
 * <pre>
 *   app.version.android.versionName   最新版本名，如 1.0.1
 *   app.version.android.versionCode   最新 versionCode（整数，比较用这个）
 *   app.version.android.minVersionCode 强制更新的最低 versionCode；本机低于它时不可跳过
 *   app.version.android.url           APK 下载直链（https 或服务器 /profile/ 路径）
 *   app.version.android.size          APK 字节数（可选，null 表示未配置）
 *   app.version.android.notes         更新说明（纯文本，可含换行）
 *   app.version.android.enabled       false 时强制视为已是最新（灰度关闭开关）
 * </pre>
 * App 只按 {@code versionCode} 判断新旧，versionName 仅用于展示。
 */
@RestController
@RequestMapping("/api/v1/app")
public class AppVersionController
{
    /** 未配置版本信息时的平台侧默认值。 */
    static final int DEFAULT_VERSION_CODE = 1;
    static final String DEFAULT_VERSION_NAME = "1.0.0";

    private final ISysConfigService configService;

    public AppVersionController(ISysConfigService configService)
    {
        this.configService = configService;
    }

    @GetMapping("/version")
    public AppApiResponse<Map<String, Object>> latest(@RequestParam(defaultValue = "android") String platform)
    {
        String prefix = "app.version." + normalizePlatform(platform) + ".";
        boolean enabled = readBoolean(prefix + "enabled", true);
        int versionCode = enabled ? readInt(prefix + "versionCode", DEFAULT_VERSION_CODE) : DEFAULT_VERSION_CODE;
        int minVersionCode = enabled ? readInt(prefix + "minVersionCode", DEFAULT_VERSION_CODE) : DEFAULT_VERSION_CODE;
        String versionName = enabled ? read(prefix + "versionName", DEFAULT_VERSION_NAME) : DEFAULT_VERSION_NAME;

        Map<String, Object> data = new HashMap<>();
        data.put("versionCode", versionCode);
        data.put("versionName", versionName);
        data.put("minVersionCode", minVersionCode);
        data.put("url", enabled ? read(prefix + "url", "") : "");
        data.put("size", readLong(prefix + "size", null));
        data.put("notes", enabled ? read(prefix + "notes", "") : "");
        return AppApiResponse.ok(data);
    }

    /** 仅保留字母数字，避免平台参数拼出任意参数键。 */
    private static String normalizePlatform(String platform)
    {
        if (platform == null)
        {
            return "android";
        }
        String cleaned = platform.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        return cleaned.isEmpty() ? "android" : cleaned;
    }

    private String read(String key, String defaultValue)
    {
        try
        {
            String value = configService.selectConfigByKey(key);
            return value == null || value.isBlank() ? defaultValue : value.trim();
        }
        catch (Exception e)
        {
            // 配置表不可用时不应让 App 启动检查失败：按「无更新」处理。
            return defaultValue;
        }
    }

    private int readInt(String key, int defaultValue)
    {
        try
        {
            String value = configService.selectConfigByKey(key);
            return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value.trim());
        }
        catch (Exception e)
        {
            return defaultValue;
        }
    }

    private Long readLong(String key, Long defaultValue)
    {
        try
        {
            String value = configService.selectConfigByKey(key);
            return value == null || value.isBlank() ? defaultValue : Long.parseLong(value.trim());
        }
        catch (Exception e)
        {
            return defaultValue;
        }
    }

    private boolean readBoolean(String key, boolean defaultValue)
    {
        try
        {
            String value = configService.selectConfigByKey(key);
            return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value.trim());
        }
        catch (Exception e)
        {
            return defaultValue;
        }
    }
}
