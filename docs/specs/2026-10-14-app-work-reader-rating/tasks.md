# 实施任务清单：作品读者评分通道

依赖顺序自上而下；每步完成即打勾。

## [x] T1 数据库迁移脚本（前置，阻塞后端联调）
- 文件：`sql/migrations/b/B_20261014_001__create_work_rating_table.sql`
- 内容：`CREATE TABLE IF NOT EXISTS sys_work_rating`（rating_id / work_id / user_id / score / created_at / updated_at；UNIQUE `uk_work_user(work_id,user_id)`；KEY `idx_work_id`；`utf8mb4_general_ci`）
- 校验闸门：表存在 + 6 列齐备 + 唯一键（复合 2 列且 NON_UNIQUE=0）+ 排序规则，末行输出 `PASS fail_cnt=0`
- 执行结果：`CHECK table=1, columns=6, uk_work_user=1, collation=1` → `PASS fail_cnt=0`；`SHOW CREATE TABLE` 已核对（唯一键/索引/排序规则全部符合）

## [x] T2 后端数据层
- `AppWorkRatingMapper.java`：`upsertRating` / `selectMyScore` / `countByWork` / `selectAvgRating` / `refreshWorkRating`
- `AppWorkRatingMapper.xml`：`INSERT ... ON DUPLICATE KEY UPDATE score=values(score), updated_at=now()`；`selectAvgRating` = `ROUND(AVG(score),1)`；`refreshWorkRating` 回写 `sys_work.rating`（无评分 `COALESCE(...,0)`）
- 注意：`refreshWorkRating` 子查询取自 `sys_work_rating`，与 `sys_work` 不同表，规避同表更新限制

## [x] T3 后端 DTO
- `AppWorkRatingDto`：workId / rating / ratingCount / myScore
- `AppWorkRatingRequest`：score

## [x] T4 后端服务层
- `IAppWorkRatingService`：`getRating(Long)`、`rate(Long, Integer)`
- `AppWorkRatingServiceImpl`：可见性复用 `SysContentWorkMapper.selectAppWorkById`；`rate` = upsert → `refreshWorkRating` → 重读作品后组装 DTO；`rating` 一律取自 `sys_work.rating`（与详情/列表/榜单同源，无第二口径）

## [x] T5 后端控制层 + 鉴权
- `AppWorkRatingController`：`@RequestMapping("/api/v1/content/works")` + `GET/POST("/{workId}/rating")`
  - `GET`：作品不可见 → 404；否则 `{workId, rating, ratingCount, myScore}`
  - `POST`：`score` 为空或越界 → 400；作品不可见 → 404；否则 upsert 覆盖并返回新聚合
- `AppAuthSecurityConfig`：已在公开 permitAll 之前插入 `GET /api/v1/content/works/*/rating` → `authenticated()`（首匹配优先，防误放为游客接口）

## [x] T6 后端单测
- 新增 `AppWorkRatingServiceTest`（6 例）：不可见不落库、读态口径、无评分回落 0、提交后回写与返回值、覆盖提交评分数不增长
- 命令：`mvn -q -pl smartscript-content -am test` → 退出码 0；surefire 汇总 53 例全过（含新增 6 例），0 失败 0 错误

## [x] T7 App 数据层
- `api_endpoints.dart`：`contentWorkRating(int workId)`
- `bookstore_models.dart`：`WorkRating`（rating / ratingCount / myScore + fromJson）
- `content_repository.dart`：`workRating(workId)` GET、`submitRating(workId, score)` POST（body `{score}`）
- `content_providers.dart`：`workRatingProvider`（autoDispose family，公开页游客不 watch）

## [x] T8 App 详情页入口
- `work_detail_page.dart`：新增 `_RatingEntry`，置于 `_Header` 与 `_ChapterEntry` 之间（五星 + `平均 x.x 分 · N 人评分` + `我的评分 y 分`/`点击星星评分`/`登录后可评分`）
- 交互：游客点击 → `AuthGuard.pushProtected(target: RoutePath.workDetailUrl(workId))`；登录态 → `submitRating` → `invalidate(workRatingProvider)` + `invalidate(workDetailProvider)` + SnackBar
- 校验：`flutter analyze` → 12 项均为既有 `features/trade/**` 的 info 级提示，改动文件 0 error / 0 warning / 0 新增 info

## [x] T9 证据核对
- 迁移脚本 PASS fail_cnt=0，建表结构经 `SHOW CREATE TABLE` 核对
- SQL 层事务验证（已回滚、零残留）：
  - 两用户评 4/5 星 → `count=2, avg=4.5`，`sys_work.rating` 回写为 `4.5`
  - 其中一人覆盖为 2 星 → `count=2`（未增长）、`avg=3.5`、`rating=3.5`
  - `ROLLBACK` 后 `sys_work_rating` 残留 0 行
- 结论：主编排 SQL（唯一键覆盖 + 聚合回写）行为与设计一致

## [x] T11 刻度纠正（每星 2 分 / 满分 10 分，用户反馈驱动）
- 背景：初版按 1~5 直存，与库中存量 `sys_work.rating` 十分制口径（实测 7.9~9.7）冲突，用户明确「每颗星应该代表两分」
- 迁移脚本：`score` 列注释改为「满分 10 分，每星 2 分：2/4/6/8/10」；新增幂等 `ALTER TABLE ... MODIFY COLUMN score` 同步已建表；校验闸门新增 `score_10pt`（`COLUMN_COMMENT LIKE '%10 分%'`），5 项校验全绿 → `PASS fail_cnt=0`
- 后端：`AppWorkRatingController` 常量 `MIN_SCORE=2 / MAX_SCORE=10` 与 400 文案同步；DTO / Mapper / Service 的 javadoc 全部改为 2~10 口径（库表量纲不变，`score` 仍为 tinyint + `sys_work.rating` 为 `decimal(3,1)`）
- App：星星点击提交 `star * 2`，填充条件改 `star * 2 <= myScore`；单测数值改为 8/6
- 一次性存量转换：对用户测试期以 1~5 语义写入的 3 行（`rating_id IN (4,9,10)`）做 ×2 修正（5→10、3→6、5→10）并刷新聚合，结果 work 48 → `10.0`、work 73 → `8.0`
- 新增测试：`AppWorkRatingControllerTest`（2 例：`null/0/1/11/12` → 400、`2/10` → 200）
- 回归证据：`mvn -q -pl smartscript-content -am test` 退出码 0；surefire 汇总 55 例全过（5+2+10+27+6+5），0 失败 0 错误

## [ ] T10 用户侧收尾（需用户执行 / 确认）
- 重启后端使其加载新代码（新增接口与鉴权规则）
- 端到端复核：App 登录态提交 4 星（8 分）→ 详情页平均分与「N 人评分」变化；再提交 5 星（10 分）→ 覆盖且评分数不增；游客点星 → 引导登录；书城列表 / 评分榜该作品 `rating` 同步
- 提交推送：后端（含迁移脚本）与 App 分别提交，提交信息说明「新增读者评分通道」——待用户点头后执行
