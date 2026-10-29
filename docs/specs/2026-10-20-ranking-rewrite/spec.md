# 排行榜重写 Spec（时间窗口 + 分级）

## Why

现有排行榜口径粗糙且与 PRD 脱节：

- 只有 4 个榜（`view`/`favorite`/`sale`/`rating`），且按 **累计值** 排序 —— 老作品永久霸榜
- 不满足 [PRD 5.2]「榜单数据按规则排序并**标明更新时间**」与 PC 分工「榜单数据查看、榜单排序、**榜单更新时间**」
- `sys_ranking_snapshot` 快照表 + 手动重算接口闲置，页面全走实时查询，PRD「通过快照表降低压力」未落地
- 接口文档 2.7.7 规定返回 `data.list`，实现返回裸数组，前端解包踩坑（PC 页因此恒空）

## What Changes

改为 **5 个榜 + 时间窗口**（用户指定方案 A）：

| 榜 | `type` | 口径 |
|---|---|---|
| 热门榜 | `view` | 累计浏览量 Top N（`sys_work.view_count`，2026-10-29 调整：不再用近 7 天阅读量） |
| 收藏榜 | `favorite` | 最近 **7 天**收藏数 Top N |
| 交易热度榜 | `sale` | 最近 **7 天**已支付订单数 Top N（两端 UI 已下线，接口保留兼容） |
| 评分榜 | `rating` | **平均评分** Top N（`sys_work.rating`，无评分不进榜） |
| 新书榜 | `new` | 首次上架（审核通过）在最近 **30 天**内，按**上架时间倒序**（2026-10-29 调整：不再按阅读量） |

附带：
- 响应体改为 `{ list: [...], updatedAt: <榜单更新时间> }`，对齐接口文档 2.7.7 的 `data.list`，并满足 PC「榜单更新时间」
- **补写 `sys_read_record`（阅读埋点）** —— 见下方数据来源
- 统一保留「读者可见性」条件（`is_deleted=0` + `status='on_shelf'` + 最新审核 `approved`）

## 数据来源（已逐条核实，禁止编造）

来源：`sql/smartscript_full_init.sql`、`sql/migrations/b/*.sql`、`开发资料/_extracted/`

| 指标 | 数据源 | 结论 |
|---|---|---|
| 最近 7 天阅读量 | `sys_read_record(user_id, work_id, chapter_id, read_duration, read_progress, created_at, ...)` | ⚠️ **表存在，但应用层从不写入**（仅迁移/种子 SQL 引用），需补埋点 |
| 最近 7 天收藏数 | `sys_favorite(user_id, work_id, created_at)` —— **注意：这是"收藏"表（附件5.1 表3-28）；`sys_bookshelf_record` 是"加入书架"，两表不可混用** | ✅ `AppFavoriteMapper.xml` 已写入，且有触发器 `trg_sys_favorite_ai_work_count` 回写 `sys_work.favorite_count` |
| 最近 7 天销量 | `sys_order(order_id, work_id, buyer_id, status, pay_time, created_at, ...)`；索引 `idx_work_id/idx_status/idx_created_at/idx_pay_time` | ✅ 已有写入 |
| 评分榜（平均分） | `sys_work_rating`（`B_20261014_001` 创建）+ `AppWorkRatingMapper`（`upsertRating/countByWork/selectAvgRating/refreshWorkRating`）；均分由 `refreshWorkRating` 回写 `sys_work.rating` | ✅ 直接取回写的平均分 |
| 新书榜「30 天内上架」 | `sys_review_record`（`target_type='work'`、`status='approved'`）最早一条的 `coalesce(review_end_time, updated_at, created_at)` | ⚠️ `sys_work` 无 `shelf_time` 列；`sys_work.review_time` **只有种子 SQL 写过，审核通过流程不回写**，App 新建作品该列为 NULL，会把真正的新书漏掉，故改用审核记录派生 |
| 累计兜底 | `sys_work.view_count / favorite_count / sale_count`（int NOT NULL，均为**累计值**）、`rating` decimal(3,1) | 仅作兜底 |
| 快照 | `sys_ranking_snapshot(ranking_type, period_start, period_end, work_id, view_count, bookshelf_count, growth_score, snapshot_time, status)`，唯一键 `(ranking_type, period_start, period_end, work_id)` | 本轮不改用它 |

**关键结论**：`sys_read_record` 表在库里已存在（无需改表结构），但**没有任何后端代码写入它**。所以「最近 7 天阅读量」必须先补埋点才成立，否则该榜恒为空。

## 决策（按推荐执行）

1. **阅读埋点**：在「读者读取章节正文 / 试读正文」链路写入一条 `sys_read_record`（登录用户才写，游客不写）。零表结构变更 —— 表已在库中。
   - 2026-10-29 起热门榜改为累计 `view_count`，该埋点仅保留为阅读行为统计，不再供榜单取数。
2. **热门榜排序**：直接按 `sys_work.view_count` 累计浏览量倒序，不做时间窗口聚合。
3. **响应体**：改为 `{ list, updatedAt }`，**必须同步改 PC 与 App 两端前端**（PC 已按裸数组修过，需一并调整）。

## 影响

- 后端 `smartscript-content`：`AppRankingController`、`AppBookstoreServiceImpl.listRankings`、`SysContentWorkMapper.xml#selectAppRankingList`、`AppRankingItem`、榜单类型白名单、新增阅读埋点写入
- PC `smart-script-web-admin`：`views/content/ranking/index.vue`（6→5 个 tab、更新时间列）、`api/content.js#rankingApi`、`constants/contentEnum.js#RANKING_TYPE`
- App `smart-script-app`：`bookstore_models.dart#RankingType`（4→5）、榜单页与 repository
- 测试：三端排行榜相关用例同步更新

## 明确不做

- 不新增/修改任何表结构（`sys_read_record` 已存在）
- 不启用 `sys_ranking_snapshot` 定时快照（周期榜需求未提出）
- 不动「书城列表/详情/榜单」之外的可见性口径
- 不引入 UI 端"榜单排序"配置项（PC 分工的"榜单排序"理解为"按规则排序展示"）
