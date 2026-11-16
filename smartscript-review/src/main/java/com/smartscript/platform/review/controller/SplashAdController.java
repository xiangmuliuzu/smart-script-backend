package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.AdConfig;
import com.smartscript.platform.review.mapper.AdConfigMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** 只返回当前可展示的自建开屏素材，游客也可访问。 */
@RestController
@RequestMapping("/api/v1/ad")
public class SplashAdController {
    private final AdConfigMapper mapper;
    public SplashAdController(AdConfigMapper mapper) { this.mapper = mapper; }

    @GetMapping("/splash")
    public Map<String, Object> splash() {
        AdConfig ad = mapper.selectActiveSplash();
        if (ad == null) return Map.of("code", 200, "data", Map.of());
        int seconds = ad.getDurationSeconds() == null ? 5 : Math.max(1, Math.min(10, ad.getDurationSeconds()));
        return Map.of("code", 200, "data", Map.of(
            "imageUrl", ad.getImageUrl(), "linkUrl", ad.getLinkUrl() == null ? "" : ad.getLinkUrl(),
            "durationSeconds", seconds));
    }
}
