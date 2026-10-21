# Tasks — 排行榜重写

- [x] T1 阅读埋点：补写 `sys_read_record`
  - [x] T1.1 定位「读者读取章节正文 / 试读正文」链路（`AppBookstoreServiceImpl.getChapterContent`，入口 `AppChapterController#chapterDetail`）
  - [x] T1.2 新增 `AppReadRecordMapper` + XML `insertReadRecordIfAbsent`，列：`user_id/work_id/chapter_id/read_duration/read_progress/created_at/create_by/create_time/remark`
  - [x] T1.3 仅登录用户写入；游客不写；失败不影响正文返回
  - [x] T1.4 单测：登录读章节写 1 条；游客读不写；不可读不写；写失败不影响返回（`AppBookstoreReadRecordTest`）

- [x] T2 榜单查询重写（`SysContentWorkMapper.xml#selectAppRankingList`）
  - [x] T2.1 `view` 热门榜：`sys_read_record` 近 7 天计数 DESC；无数据时回退累计 `view_count`
  - [x] T2.2 `favorite` 收藏榜：`sys_favorite` 近 7 天计数 DESC（**不是** `sys_bookshelf_record`）
  - [x] T2.3 `sale` 交易热度榜：`sys_order` 近 7 天已支付订单数 DESC（`pay_time` 已落地即视为已支付）
  - [x] T2.4 `rating` 评分榜：贝叶斯 `(C*m + sum)/(C+n)`，`C=10`、`m=6.0`（十分制中位）
  - [x] T2.5 `new` 新书榜：首次审核通过时间（`sys_review_record` 最早一条 approved 的 `coalesce(review_end_time, updated_at, created_at)`）`>= now()-30d`，按累计阅读量排序（原 `sys_work.review_time` 无人回写，已弃用）
  - [x] T2.6 全部保留 `readerVisibleWork` 可见性条件 + `limit`

- [x] T3 契约与模型
  - [x] T3.1 返回体改 `{ list, updatedAt }`（新增 `AppRankingResult`，对齐接口文档 2.7.7 的 `data.list`）
  - [x] T3.2 `AppBookstoreServiceImpl` 白名单扩为 `view/favorite/sale/rating/new`
  - [x] T3.3 `AppRankingItem` 补 `updatedAt` 或由外层承载（由 `AppRankingResult.updatedAt` 承载）；清理 `score` 语义注释（改为 SQL `rank_score` 映射）

- [x] T4 PC 管理端适配
  - [x] T4.1 `views/content/ranking/index.vue`：tab 改 5 个、取 `res.list`、新增「更新时间」列（并修正标题列 `workTitle`→`title`）
  - [x] T4.2 `constants/contentEnum.js#RANKING_TYPE` 对齐 5 类（已含 `new`）
  - [x] T4.3 `api/content.js#rankingApi` 注释/契约更新

- [x] T5 App 端适配
  - [x] T5.1 `bookstore_models.dart#RankingType` 扩为 5 类 + 中文名
  - [x] T5.2 榜单页/ repository 按 `{list}` 取值，展示更新时间（新增 `RankingPayload`）
  - [x] T5.3 前端单测更新（`test/bookstore/ranking_models_test.dart`，3 用例）

- [x] T6 后端测试
  - [x] T6.1 榜单服务单测：5 种 type 的排序/回退/白名单（`AppRankingServiceTest`：类型透传/非法回落/rankNo/limit 收敛/updatedAt）
  - [x] T6.2 埋点单测（见 T1.4）

- [ ] T7 验证
  - [x] T7.1 `mvn -pl smartscript-content -am test` 0 失败 0 错误（133 tests，0 fail 0 error）
  - [x] T7.2 PC `npm test`（75 tests / 74 pass / 0 fail / 1 skipped）+ `npm run lint` 通过；App `flutter analyze` 无 error + `flutter test test/bookstore/ranking_models_test.dart` 3 用例通过
  - [ ] T7.3 手测清单：5 个榜各有数据、更新时间展示、游客/登录可见性一致（需真实库/端到端，尚未执行）

# Task Dependencies

- T2 依赖 T1（热门榜窗口指标来自埋点），但 T2 可先按兜底实现并并行开发
- T3 依赖 T2（响应体定稿）
- T4 / T5 依赖 T3
- T6 依赖 T1、T2
- T7 依赖全部
