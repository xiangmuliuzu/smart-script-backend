package com.smartscript.platform.content.controller.admin;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.service.ISysBookstoreService;

/**
 * 书城作品管理（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_work 表 + PC 功能清单（书城作品管理页：
 * 列表、详情、上下架状态、交易开关、扩展JSON 调整；清单未列删除，故不提供）。
 * 路由前缀 /api/v1/admin/content/bookstore（附件6.1 统一版本前缀 /api/v1 + 后台 /admin）。
 * 鉴权：若依 RBAC，@PreAuthorize + Bearer Token。
 * 返回：列表 TableDataInfo，其余 AjaxResult。
 * 反推处理点：03 PC 接口文档未列 B 模块书城接口，按云端表结构 + PC 功能清单反推；
 * changeStatus/changeTrade/updateExt 均为局部更新，不做 @Validated 全量校验。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/bookstore")
public class SysBookstoreController extends BaseController
{
    @Autowired
    private ISysBookstoreService bookstoreService;

    @Autowired
    private com.smartscript.platform.content.mapper.SysContentWorkMapper workMapper;

    /**
     * 获取书城作品列表
     * 入参（query，均可选）：title 模糊、recommendStatus 走 JSON_EXTRACT、status/tradeEnabled 精确；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:bookstore:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysWork query)
    {
        startPage();
        List<SysWork> list = bookstoreService.selectBookstoreList(query);
        return getDataTable(list);
    }

    /**
     * 通过作品ID获取书城作品详情（含从 ext_json 解析的 recommendStatus/showScope）
     */
    @PreAuthorize("@ss.hasPermi('content:bookstore:query')")
    @GetMapping("/{workId}")
    public AjaxResult getInfo(@PathVariable Long workId)
    {
        return success(bookstoreService.selectBookstoreById(workId));
    }

    /**
     * 修改作品上下架状态（单一职责：只改 status）
     */
    @PreAuthorize("@ss.hasPermi('content:bookstore:edit')")
    @Log(title = "书城作品", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysWork work)
    {
        work.setUpdateBy(getUsername());
        return toAjax(bookstoreService.updateWorkStatus(work));
    }

    /**
     * 修改作品交易开关（单一职责：只改 trade_enabled）
     */
    @PreAuthorize("@ss.hasPermi('content:bookstore:edit')")
    @Log(title = "书城作品", businessType = BusinessType.UPDATE)
    @PutMapping("/changeTrade")
    public AjaxResult changeTrade(@RequestBody SysWork work)
    {
        work.setUpdateBy(getUsername());
        return toAjax(bookstoreService.updateWorkTradeEnabled(work));
    }

    /**
     * 修改作品扩展JSON（整体替换 ext_json）。
     *
     * 首页热门（bookstore / recommendWeight，最多 30 本）与搜索页（search / searchWeight，
     * 最多 6 本）是两套相互独立的位次，各自做插入式自动重排与上限校验；一本书可同时勾选。
     */
    @PreAuthorize("@ss.hasPermi('content:bookstore:edit')")
    @Log(title = "书城作品", businessType = BusinessType.UPDATE)
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @PutMapping("/updateExt")
    public AjaxResult updateExt(@RequestBody SysWork work)
    {
        String extJson = work.getExtJson();
        if (extJson != null && !extJson.isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode node = om.readTree(extJson);

                // 新状态：showScope 勾选了哪些展示位
                java.util.List<String> newScope = new java.util.ArrayList<>();
                if (node.has("showScope") && node.get("showScope").isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode s : node.get("showScope")) {
                        newScope.add(s.asText());
                    }
                }
                boolean newBook = newScope.contains("bookstore");
                boolean newSearch = newScope.contains("search");
                boolean anyRecommended = newBook || newSearch;

                // 业务规则：搜索页推荐从属于首页热门——不允许只勾搜索、不勾首页
                if (newSearch && !newBook) {
                    return AjaxResult.error("推荐到搜索页的作品必须先推荐到首页热门");
                }

                SysWork dbWork = workMapper.selectWorkById(work.getWorkId());
                if (dbWork == null) {
                    return AjaxResult.error("作品不存在");
                }

                // 解析旧状态：是否在各展示位、原位次
                java.util.List<String> oldScope = new java.util.ArrayList<>();
                int oldBookWeight = 0;
                int oldSearchWeight = 0;
                String oldExtJson = dbWork.getExtJson();
                if (oldExtJson != null && !oldExtJson.isEmpty()) {
                    com.fasterxml.jackson.databind.JsonNode oldNode = om.readTree(oldExtJson);
                    if (oldNode.has("showScope") && oldNode.get("showScope").isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode s : oldNode.get("showScope")) {
                            oldScope.add(s.asText());
                        }
                    }
                    if (oldScope.contains("bookstore")
                            && oldNode.has("recommendWeight") && oldNode.get("recommendWeight").isNumber()) {
                        oldBookWeight = oldNode.get("recommendWeight").asInt();
                    }
                    if (oldScope.contains("search")
                            && oldNode.has("searchWeight") && oldNode.get("searchWeight").isNumber()) {
                        oldSearchWeight = oldNode.get("searchWeight").asInt();
                    }
                }

                if (anyRecommended) {
                    // 失效时间（两个展示位共用一个失效时间）
                    if (!node.has("recommendExpireAt") || node.get("recommendExpireAt").asText().isEmpty()) {
                        return AjaxResult.error("请选择推荐失效时间");
                    }
                    // 必须已上架
                    if (!"on_shelf".equals(dbWork.getStatus())) {
                        return AjaxResult.error("请先上架作品再设置推荐");
                    }
                }

                com.fasterxml.jackson.databind.node.ObjectNode on =
                        (com.fasterxml.jackson.databind.node.ObjectNode) node;

                // 首页热门位次（最多 30）
                AjaxResult bookErr = applySlot(on, "recommendWeight", "bookstore", "首页热门", 30,
                        newBook, oldScope.contains("bookstore"), oldBookWeight);
                if (bookErr != null) {
                    return bookErr;
                }

                // 搜索页位次（最多 6）
                AjaxResult searchErr = applySlot(on, "searchWeight", "search", "搜索页", 6,
                        newSearch, oldScope.contains("search"), oldSearchWeight);
                if (searchErr != null) {
                    return searchErr;
                }

                work.setExtJson(om.writeValueAsString(on));
            } catch (com.fasterxml.jackson.core.JsonProcessingException pe) {
                return AjaxResult.error("推荐信息格式错误");
            } catch (Exception e) {
                // 重排/落库异常：抛出让事务回滚，避免位次错乱
                throw new RuntimeException("推荐信息处理失败：" + e.getMessage(), e);
            }
        }

        work.setUpdateBy(getUsername());
        return toAjax(bookstoreService.updateWorkExtJson(work));
    }

    /**
     * 处理单个推荐展示位的位次夹取、插入式自动重排、上限校验。
     *
     * @param on        待落库的 ext_json 对象
     * @param field     位次字段名（recommendWeight / searchWeight）
     * @param slot      展示位标识（bookstore / search）
     * @param slotName  展示位中文名（用于错误提示）
     * @param limit     该展示位推荐数量上限
     * @param nowOn     本次提交是否勾选该展示位
     * @param wasOn     数据库原是否在该展示位
     * @param oldWeight 该展示位原位次（0 表示无）
     * @return 校验失败返回错误结果；成功返回 null
     */
    private AjaxResult applySlot(com.fasterxml.jackson.databind.node.ObjectNode on, String field,
                                 String slot, String slotName, int limit,
                                 boolean nowOn, boolean wasOn, int oldWeight)
    {
        if (nowOn) {
            int newWeight = on.has(field) && on.get(field).isNumber() ? on.get(field).asInt() : 1;
            if (newWeight < 1) {
                return AjaxResult.error("推荐位次必须为大于 0 的整数");
            }
            int total = countSlot(slot);
            if (!wasOn) {
                // 新增到该展示位：上限校验
                if (total >= limit) {
                    return AjaxResult.error(slotName + "最多推荐 " + limit + " 本作品，已达上限");
                }
                int maxPos = total + 1;
                if (newWeight > maxPos) {
                    newWeight = maxPos;
                }
                // 原 >= newWeight 的作品整体后移，腾出 newWeight
                shiftSlot(slot, newWeight, 9999, 1);
            } else {
                // 调整该展示位现有位次
                int maxPos = total > 0 ? total : 1;
                if (newWeight > maxPos) {
                    newWeight = maxPos;
                }
                if (oldWeight > 0) {
                    if (newWeight < oldWeight) {
                        // 前移：[newWeight, oldWeight-1] 后移
                        shiftSlot(slot, newWeight, oldWeight - 1, 1);
                    } else if (newWeight > oldWeight) {
                        // 后移：[oldWeight+1, newWeight] 前移
                        shiftSlot(slot, oldWeight + 1, newWeight, -1);
                    }
                }
            }
            on.put(field, newWeight);
        } else {
            // 本次未勾选该展示位：清除其位次；原在该位则其后作品前移
            on.remove(field);
            if (wasOn && oldWeight > 0) {
                shiftSlot(slot, oldWeight + 1, 9999, -1);
            }
        }
        return null;
    }

    /** 统计某展示位当前推荐作品数。 */
    private int countSlot(String slot) {
        return "search".equals(slot) ? workMapper.countSearchWorks() : workMapper.countBookstoreWorks();
    }

    /** 对某展示位的位次区间做平移。 */
    private int shiftSlot(String slot, int start, int end, int delta) {
        if ("search".equals(slot)) {
            return workMapper.shiftSearchWeight(start, end, delta);
        }
        return workMapper.shiftRecommendWeight(start, end, delta);
    }
}
