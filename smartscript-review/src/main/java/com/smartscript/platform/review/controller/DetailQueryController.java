package com.smartscript.platform.review.controller;

import com.smartscript.platform.review.service.DetailQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 明细数据查询Controller
 *
 * @author smartscript
 */
@RestController
@RequestMapping("/api/v1/admin/statistics/detail")
public class DetailQueryController {

    @Autowired
    private DetailQueryService detailQueryService;

    /**
     * 作品明细
     */
    @GetMapping("/works")
    public Map<String, Object> works(@RequestParam(value = "keyword", required = false) String keyword,
                                     @RequestParam(value = "status", required = false) String status,
                                     @RequestParam(value = "startDate", required = false) String startDate,
                                     @RequestParam(value = "endDate", required = false) String endDate) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> list = detailQueryService.selectWorkDetail(keyword, status, startDate, endDate);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 用户明细
     */
    @GetMapping("/users")
    public Map<String, Object> users(@RequestParam(value = "keyword", required = false) String keyword,
                                     @RequestParam(value = "userType", required = false) String userType,
                                     @RequestParam(value = "startDate", required = false) String startDate,
                                     @RequestParam(value = "endDate", required = false) String endDate) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> list = detailQueryService.selectUserDetail(keyword, userType, startDate, endDate);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 明细导出（works=作品明细 / users=用户明细），返回CSV
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam("type") String type,
                                         @RequestParam(value = "keyword", required = false) String keyword,
                                         @RequestParam(value = "status", required = false) String status,
                                         @RequestParam(value = "userType", required = false) String userType,
                                         @RequestParam(value = "startDate", required = false) String startDate,
                                         @RequestParam(value = "endDate", required = false) String endDate) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        if ("works".equals(type)) {
            List<Map<String, Object>> list = detailQueryService.selectWorkDetail(keyword, status, startDate, endDate);
            sb.append("作品ID,作品标题,作者,类型,状态,浏览量,收藏量,销量,价格,创建时间\n");
            for (Map<String, Object> r : list) {
                sb.append(csv(r.get("workId"))).append(',').append(csv(r.get("title"))).append(',')
                        .append(csv(r.get("authorName"))).append(',').append(csv(r.get("workType"))).append(',')
                        .append(csv(r.get("status"))).append(',').append(csv(r.get("viewCount"))).append(',')
                        .append(csv(r.get("favoriteCount"))).append(',').append(csv(r.get("saleCount"))).append(',')
                        .append(csv(r.get("price"))).append(',').append(csv(r.get("createdAt"))).append('\n');
            }
        } else {
            List<Map<String, Object>> list = detailQueryService.selectUserDetail(keyword, userType, startDate, endDate);
            sb.append("用户ID,用户名,昵称,类型,手机号,邮箱,状态,注册时间\n");
            for (Map<String, Object> r : list) {
                sb.append(csv(r.get("userId"))).append(',').append(csv(r.get("userName"))).append(',')
                        .append(csv(r.get("nickName"))).append(',').append(csv(r.get("userType"))).append(',')
                        .append(csv(r.get("phonenumber"))).append(',').append(csv(r.get("email"))).append(',')
                        .append(csv(r.get("status"))).append(',').append(csv(r.get("createTime"))).append('\n');
            }
        }
        byte[] body = ("\uFEFF" + sb.toString()).getBytes(StandardCharsets.UTF_8);
        String filename = ("works".equals(type) ? "作品明细" : "用户明细") + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, "UTF-8"))
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    private String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v).replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\n")) s = "\"" + s + "\"";
        return s;
    }

    /**
     * 广告收益明细（预留：sys_ad_revenue 联 sys_work）
     */
    @GetMapping("/ad-revenue")
    public Map<String, Object> adRevenue(@RequestParam(value = "startDate", required = false) String startDate,
                                         @RequestParam(value = "endDate", required = false) String endDate) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> list = detailQueryService.selectAdRevenue(startDate, endDate);
        result.put("code", 200);
        result.put("msg", "操作成功");
        result.put("rows", list);
        result.put("total", list.size());
        return result;
    }
}
