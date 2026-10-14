# 作品读者评分通道（App 读者侧）

状态：待评审 → 待实现
范围：B 模块（内容与作品）后端 `smartscript-content` + App `smart-script-app` 书城作品详情页
提出人诉求：每本书作为读者来说，应该有评分的通道。

## 1. 背景与现状（已核实，不含推断）

| 事实 | 证据 |
| --- | --- |
| 设计文档只有作品平均分列 `sys_work.rating decimal(3,1)`，**没有**读者评分表，也**没有**提交评分接口 | 附件5.1 表3-14（第 17309 行 `rating/评分`）；App 接口文档 2.7.2 表2-82 只有 `rating Float 评分`（第 4578 行） |
| 云端库 `script_platform_dev` 实际无任何 `%rating%` 表；`sys_work%` 只有 work / access_grant / chapter / file / tag / version | `SHOW TABLES LIKE '%rating%'` 返回空 |
| `sys_work.rating` 为 `decimal(3,1) NOT NULL`，当前无任何写入方（仅建作品时置 0） | `information_schema.COLUMNS` 查询 + 代码 grep（`AppWorkManageServiceImpl` 仅 `setRating(ZERO)`） |
| 库内无评分相关触发器；`favorite_count` 由 `trg_sys_favorite_ai/ad_work_count` 维护，评分平均分**不**走触发器 | `information_schema.TRIGGERS` 查询结果 |
| 已存在完全同构的读者互动通道可作模板：收藏（`AppFavoriteController` / `IAppFavoriteService` / `AppFavoriteMapper` + XML，路径 `/api/v1/content/favorites/{workId}`，GET 查状态 / POST 建立 / DELETE 取消） | 代码 |
| App 详情页已展示平均分（`_Stat(label: '评分', value: work.rating.toStringAsFixed(1))`），但无任何评分入口 | `work_detail_page.dart#L264` |
| 榜单 rating（评分榜）与 PC 排行榜均以 `sys_work.rating` 为指标，评分回写后两处自动同步，无需额外改动 | `SysRankingSnapshotServiceImpl`、`AppRankingItem` |

结论：读者评分**必须新增存储**（一人一评），并按已有约定把聚合平均值回写 `sys_work.rating`，从而一次性打通 App 详情页、书城列表、评分榜、PC 排行榜的展示闭环。

## 2. 口径与决策（decision-complete）

- **评分刻度**：满分 10 分、每星 2 分（score 取 2/4/6/8/10）。理由：`sys_work.rating` 存量本就是十分制（实测 7.9~9.7），5 分制会与既有量纲冲突并把均分拉低到 5 分档；`decimal(3,1)` 容纳 10.0，App `toStringAsFixed(1)` 直接可用。
- **一人一评**：`(work_id, user_id)` 唯一；再次提交即**覆盖**（更新分值），不做多评累加。
- **聚合口径**：`sys_work.rating = ROUND(AVG(score), 1)`，无评分时为 `0`（该列 NOT NULL）。由服务层显式回写，**不**新增触发器（与 `refreshWorkAggregates` 同思路：聚合列由代码维护）。
- **身份**：归属一律取当前登录身份，请求体不接受 `userId`；未登录（游客）点击评分走 `AuthGuard.pushProtected` 引导登录并回跳，不为必然 401 的请求往返。
- **接口形态**：镜像收藏通道，同路径双方法。
  - `GET /api/v1/content/works/{workId}/rating` → 需登录，返回 `{workId, rating, ratingCount, myScore}`
  - `POST /api/v1/content/works/{workId}/rating`，body `{"score": 2..10 的整数，每星 2 分}` → 需登录，提交/覆盖后返回同一结构
- **可见性**：两方法均复用 `SysContentWorkMapper.selectAppWorkById` 判「未删除 + 已上架」，未上架/不存在一律 404，与 2.7.2 详情口径一致。
- **不改动**：`AppWorkDto` 不加字段（详情页平均分继续取自 `sys_work.rating`）；不新增详情页专用接口；不改榜单与 PC 端。
- **鉴权登记**：`POST` 天然落入 `AppAuthSecurityConfig` 的 `/api/v1/content/**` → `authenticated()` 规则；`GET` 因 `/api/v1/content/works/**` 被 permitAll，**必须在公开白名单之前**声明 `GET /api/v1/content/works/*/rating` → `authenticated()`（Spring Security 首匹配优先），与既有 `works/drafts`、`works/*/versions` 的写法一致。`OPTIONAL_IDENTITY_PATHS` 无需改动（`/content/works` 已登记，该路径无 Token 时由授权层按 401 拒绝）。

## 3. 数据库变更

新增表 `sys_work_rating`（作品读者评分表），迁移脚本幂等可重跑：

```sql
CREATE TABLE IF NOT EXISTS sys_work_rating (
  rating_id  bigint   NOT NULL AUTO_INCREMENT COMMENT '评分记录ID',
  work_id    bigint   NOT NULL COMMENT '作品ID',
  user_id    bigint   NOT NULL COMMENT '评分用户ID',
  score      tinyint  NOT NULL COMMENT '评分（1~5 星）',
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次评分时间',
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近评分时间',
  PRIMARY KEY (rating_id),
  UNIQUE KEY uk_work_user (work_id, user_id),
  KEY idx_work_id (work_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作品读者评分表';
```

- 排序规则与内容域表一致（`utf8mb4_general_ci`，实测 `sys_favorite` 即此排序规则）。
- 唯一键 `uk_work_user` 是「一人一评 + 覆盖更新」的落地点（`INSERT ... ON DUPLICATE KEY UPDATE`）。
- 脚本末行为 `PASS fail_cnt=0` 闸门：校验表存在、6 列齐备、唯一键存在。

## 4. 后端改动（精确到文件）

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | 新增 `sql/migrations/b/B_20261014_001__create_work_rating_table.sql` | 建表 + 幂等校验闸门（PASS fail_cnt=0） |
| 2 | 新增 `smartscript-content/.../content/mapper/AppWorkRatingMapper.java` | `int upsertRating(Long userId, Long workId, Integer score)`；`Integer selectMyScore(Long userId, Long workId)`；`int countByWork(Long workId)`；`BigDecimal selectAvgRating(Long workId)`；`int refreshWorkRating(Long workId)` |
| 3 | 新增 `smartscript-content/resources/mapper/content/AppWorkRatingMapper.xml` | `insert ... on duplicate key update score=values(score), updated_at=now()`；`select score ...`；`select count(1) ...`；`select round(avg(score),1) ...`；`update sys_work w set rating = coalesce((select round(avg(r.score),1) from sys_work_rating r where r.work_id = w.work_id), 0) where w.work_id = #{workId}` |
| 4 | 新增 `.../content/dto/AppWorkRatingDto.java` | `workId(Long)`、`rating(BigDecimal 平均分)`、`ratingCount(Integer)`、`myScore(Integer，null=未评分)` |
| 5 | 新增 `.../content/dto/AppWorkRatingRequest.java` | `score(Integer)`（请求体，`@RequestBody(required=false)`） |
| 6 | 新增 `.../content/service/IAppWorkRatingService.java` | `AppWorkRatingDto getRating(Long workId)`；`AppWorkRatingDto rate(Long workId, Integer score)` |
| 7 | 新增 `.../content/service/impl/AppWorkRatingServiceImpl.java` | 可见性判定 → `upsertRating` → `refreshWorkRating` → 组装 DTO（刻度常量 `MIN_SCORE=2/MAX_SCORE=10` 定义在控制层，越界按 400 拒绝） |
| 8 | 新增 `.../content/controller/app/AppWorkRatingController.java` | `@RequestMapping("/api/v1/content/works/{workId}/rating")`；`GET`（404 当作品不可见）；`POST`（`score` 为空或非 2~10 → 400；作品不可见 → 404），返回 `AppApiResponse<AppWorkRatingDto>` |
| 9 | 修改 `smartscript-user/.../config/AppAuthSecurityConfig.java` | 在公开 permitAll PUT 之前插入 `GET /api/v1/content/works/*/rating` → `authenticated()`，并同步注释 |
| 10 | 新增后端单测 `AppWorkRatingServiceTest` + `AppWorkRatingControllerTest` | 覆盖：刻度边界 2/10 通过、0/1/11/空 拒绝；覆盖更新仅一行且评分数不增；`refreshWorkRating` 后 `sys_work.rating` = 平均值；无评分回落 0 |

约定遵循：项目 `@author xiangsipeng`、`created_at/updated_at` 不映射、`long→Long`、单一接口只做一件事、统一 `AppApiResponse` 信封、Mapper XML 与接口同名同命名空间。

## 5. App 改动（精确到文件）

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | `lib/core/constants/api_endpoints.dart` | `static String contentWorkRating(int workId) => '/content/works/$workId/rating';` |
| 2 | `lib/features/bookstore/data/bookstore_models.dart` | 新增 `WorkRating{ double rating; int ratingCount; int? myScore; fromJson }` |
| 3 | `lib/features/bookstore/data/content_repository.dart` | `Future<WorkRating> workRating(int workId)`（GET）；`Future<WorkRating> submitRating(int workId, int score)`（POST，`data: {'score': score}`） |
| 4 | `lib/features/bookstore/data/content_providers.dart` | `workRatingProvider = FutureProvider.autoDispose.family<WorkRating, int>`（与 `favoriteStatusProvider` 同注释口径：公开页游客不 watch） |
| 5 | `lib/features/bookstore/pages/work_detail_page.dart` | 新增 `_RatingEntry` 区块（置于 `_Header` 与 `_ChapterEntry` 之间）：五星行 + `平均 x.x 分 · N 人评分` + `我的评分 y 分`；点击星星 → `AuthGuard.pushProtected(target: RoutePath.workDetailUrl(workId))` 或 `submitRating` → 成功后 `ref.invalidate(workRatingProvider)` + `ref.invalidate(workDetailProvider)`（平均分与书城列表同步刷新）→ SnackBar 提示 |

不改动：作品详情/列表模型字段、收藏与书架链路、榜单页。

## 6. 验证步骤

1. 对云端库执行迁移脚本（口令经 `$env:MYSQL_PWD` 从 `.env.local` 读入，不进 argv）：
   `...\mysql.exe -h 124.221.131.38 -u dev_xiang_sipeng script_platform_dev -e "source sql/migrations/b/B_20261014_001__create_work_rating_table.sql"` → 期望末行 `PASS fail_cnt=0`。
2. 核实建表结果：`SHOW CREATE TABLE sys_work_rating`（列、唯一键、排序规则）。
3. 后端构建 + 单测：`mvn -pl smartscript-content -am test` → 期望 0 失败（含新增评分用例）。
4. App 静态检查：`d:\code\smart-script-app` 执行 `flutter analyze` → 期望 0 error。
5. 端到端（后端由用户重启后）：App 登录身份对某作品提交 8 分（4 星）→ 详情页平均分与「N 人评分」变化；再提交 10 分（5 星）→ 平均值按覆盖更新、评分数不增；游客点星星 → 引导登录而非报错；书城列表/评分榜该作品 `rating` 同步变化。

## 7. 风险与回滚

- 低风险：新增表 + 新增接口，不触碰既有列与既有链路；`sys_work.rating` 由本通道独占写入（此前无写入方），不影响其它模块读口径。
- 回滚：停用 Controller/前端入口即可；表可保留（无外部依赖）。
- 唯一需要人工把关的点：`GET /api/v1/content/works/*/rating` 必须在 permitAll 之前声明，否则会被当游客接口放开（安全影响：读到他人平均分与评分数，但读不到他人个人评分）。

## 8. 待批准

以上方案确认后即按 `tasks.md` 顺序实施。
