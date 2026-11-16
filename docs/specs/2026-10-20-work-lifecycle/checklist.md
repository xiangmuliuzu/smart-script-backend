# Checklist：作品生命周期重构（后端 1~5 步）

对应规格：[spec.md](./spec.md) ｜ 任务：[tasks.md](./tasks.md)

## 模型与不变式

- [x] `WorkStatusDeriver` 纯静态工具类上线，常量与 `derive/isReviewing/isReaderVisible/isChapterReaderVisible` 与 spec §5.1 一致
- [x] `sys_work.status` 的所有写入点只写 `on_shelf`/`off_shelf`（`createWork` 写 `off_shelf`；`changeShelfStatus` 校验合法值）
- [x] 生命周期状态仅由 `sys_review_record`（`target_type='work'`）最新一条派生（`getReviewStatus` 不再读 `sys_work.status`）

## 审核与上下架

- [x] 审核流转到 `approved` 时自动把 `sys_work.status` 置 `on_shelf`（review 模块 Mapper 钩子 + `operateReview` 事务内调用，返回体不变）
- [x] 作者下架无前置条件；重新上架要求 `reject_reason` 为空且最新审核为 `approved`
- [x] 非法 `status` 入参抛 `IllegalArgumentException`

## 章节两态

- [x] `AppChapterAuthorServiceImpl.DEFAULT_STATUS` = `"1"`（草稿）
- [x] `AppUserWorkServiceImpl.addChapter` 章节 `status` = `"1"`（草稿）

## 读者可见性口径

- [x] 新增共享 `<sql id="readerVisibleWork">`，`selectAppWorkVo`/`selectAppWorkById`/`selectAppRankingList` 均改用 `<where>` + include（`selectAppWorkById` 补齐最新审核判定）
- [x] `SysRankingSnapshotMapper.selectWorkMetricStats`、`SysTagMapper.selectAppTagList`（categoryId 子查询）改用跨命名空间 include
- [x] `selectDraftWorks` 改为「本人 + 未删除 + 从未提交审核」
- [x] 4 个「我的」列表（书架/收藏/订阅/播放历史）未被扩散收紧（有意保留 `w.status='on_shelf'`）
- [x] `TradeWorkService.enableTrade` 只判 `on_shelf`（去掉历史 `approved` 兼容）

## 范围与验证

- [x] 未新增数据库列/表，无迁移脚本；未改 App 端 Dart / PC 端 Vue
- [x] `mvn -pl smartscript-content -am test` 通过（0 failures / 0 errors）
- [x] `mvn -pl smartscript-review -am -DskipTests package` 与 `mvn -pl smartscript-trade -am -DskipTests package` 编译通过
