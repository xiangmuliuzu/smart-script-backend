package com.ruoyi.web.controller.common;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;

/**
 * A0-R1 运行时健康探针（匿名可访问，供验收脚本使用）。
 */
@RestController
public class HealthController
{
    @GetMapping("/health")
    public AjaxResult health()
    {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("app", "smart-script-backend");
        data.put("framework", "RuoYi-Vue");
        return AjaxResult.success(data);
    }
}
