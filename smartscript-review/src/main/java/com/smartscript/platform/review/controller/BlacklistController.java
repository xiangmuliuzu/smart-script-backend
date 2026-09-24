package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.domain.Blacklist;
import com.smartscript.platform.review.service.BlacklistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 黑名单Controller
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/admin/review/blacklist")
public class BlacklistController {

    @Autowired
    private BlacklistService blacklistService;

    /**
     * 查询黑名单列表
     */
    @GetMapping("/list")
    public Map<String, Object> list(Blacklist blacklist) {
        Map<String, Object> result = new HashMap<>();
        List<Blacklist> list = blacklistService.selectBlacklistList(blacklist);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 查询黑名单详情
     */
    @GetMapping("/detail/{id}")
    public Map<String, Object> detail(@PathVariable("id") Long id) {
        Map<String, Object> result = new HashMap<>();
        Blacklist blacklist = blacklistService.selectBlacklistById(id);
        if (blacklist == null) {
            result.put("code", 404);
            result.put("msg", "未找到该记录");
        } else {
            result.put("code", 200);
            result.put("msg", "操作成功");
            result.put("data", blacklist);
        }
        return result;
    }

    /**
     * 新增黑名单
     */
    @PostMapping("/create")
    public Map<String, Object> add(@RequestBody Blacklist blacklist) {
        Map<String, Object> result = new HashMap<>();
        blacklistService.insertBlacklist(blacklist);
        result.put("code", 200);
        result.put("msg", "添加黑名单成功");
        return result;
    }

    /**
     * 修改黑名单
     */
    @PutMapping
    public Map<String, Object> edit(@RequestBody Blacklist blacklist) {
        Map<String, Object> result = new HashMap<>();
        blacklistService.updateBlacklist(blacklist);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }

    /**
     * 删除黑名单
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> remove(@PathVariable("id") Long id) {
        Map<String, Object> result = new HashMap<>();
        blacklistService.deleteBlacklistById(id);
        result.put("code", 200);
        result.put("msg", "操作成功");
        return result;
    }
}
