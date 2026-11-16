package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.Blacklist;
import com.smartscript.platform.review.service.BlacklistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 异常账号处理Controller
 *
 * 异常账号 = 处于黑名单（target_type=user 且 status=enabled）的用户，
 * 提供列表查询与处理（解除黑名单/保持观察）。
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/admin/review/abnormal")
public class AbnormalAccountController {

    @Autowired
    private BlacklistService blacklistService;

    /**
     * 异常账号列表（黑名单中的用户账号）
     */
    @GetMapping("/list")
    public Map<String, Object> list() {
        Map<String, Object> result = new HashMap<>();
        Blacklist q = new Blacklist();
        q.setTargetType("user");
        q.setStatus("enabled");
        List<Blacklist> list = blacklistService.selectBlacklistList(q);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Blacklist b : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", b.getId());
            m.put("account", b.getTargetValue());
            m.put("reason", b.getReason());
            m.put("blackType", b.getBlackType());
            m.put("targetType", b.getTargetType());
            m.put("createTime", b.getCreateTime() == null ? "" : b.getCreateTime());
            m.put("status", b.getStatus());
            rows.add(m);
        }
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", rows);
        result.put("total", rows.size());
        return result;
    }

    /**
     * 异常账号处理
     * action=release 解除拉黑（status置为disabled）；action=keep 保持观察
     */
    @PostMapping("/handle")
    public Map<String, Object> handle(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        Long id = Long.valueOf(params.get("id").toString());
        String action = params.get("action") != null ? params.get("action").toString() : "release";
        Blacklist blacklist = new Blacklist();
        blacklist.setId(id);
        blacklist.setStatus("release".equals(action) ? "disabled" : "enabled");
        blacklistService.updateBlacklist(blacklist);
        result.put("code", 200);
        result.put("msg", "release".equals(action) ? "已解除该异常账号黑名单" : "已保持观察");
        return result;
    }
}
