# Tasks：作品生命周期重构（后端 1~5 步）

对应规格：[spec.md](./spec.md)
执行顺序：S1 → S2 → S3 → S4 → S5 → 测试 → 验证。每步完成即勾选。

- [x] **T1 步骤 1｜状态派生工具类**
  - [x] T1.1 新增 `smartscript-content/src/main/java/com/smartscript/platform/content/util/WorkStatusDeriver.java`
        （作品常量 + 章节常量 `CHAPTER_PUBLISHED/CHAPTER_DRAFT` + `derive` / `isReviewing` /
        `isReaderVisible` / `isChapterReaderVisible`）
  - [x] T1.2 改 `AppUserWorkServiceImpl`：删 `REVIEWING_STATUS` 与 switch 版 `deriveStatus`，委托 `WorkStatusDeriver`；
        `requireEditable` 用 `isReviewing`；构造器签名不变；类注释改正
  - [x] T1.3 改 `AppWorkManageServiceImpl`：注入 `SysWorkReviewRecordMapper`；
        `getReviewStatus` 由审核记录派生 `status`/`reviewResult`（删 `deriveReviewResult(sys_work.status)`）；
        `createWork` 写 `OFF_SHELF`（删 `STATUS_DRAFT`）
  - [x] T1.4 改 `AppReviewStatusDto` 字段注释为新派生口径

- [x] **T2 步骤 2｜审核通过钩子（自动上架）**
  - [x] T2.1 `smartscript-review` `ReviewRecordMapper.java` 新增 `markWorkOnShelf(workId, updateBy)`
  - [x] T2.2 `ReviewRecordMapper.xml` 新增同 id 的 `UPDATE sys_work ... SET status='on_shelf'`
  - [x] T2.3 `ReviewService.operateReview` 加 `@Transactional(rollbackFor=Exception.class)`；
        `approved` + `target_type='work'` 时调用 `markWorkOnShelf`（返回体不变）

- [x] **T3 步骤 3｜作者「申请下架 / 重新上架」**
  - [x] T3.1 重写 `AppUserWorkServiceImpl.changeShelfStatus`：下架无前置；重新上架要求
        `reject_reason` 为空且最新审核为 `approved`；非法 status 抛 `IllegalArgumentException`
  - [x] T3.2 改 `IAppUserWorkService` 与 `AppUserWorkController` 对应方法注释

- [x] **T4 步骤 4｜章节两态**
  - [x] T4.1 `AppChapterAuthorServiceImpl.DEFAULT_STATUS` 改 `WorkStatusDeriver.CHAPTER_DRAFT`（"1"）+ 注释
  - [x] T4.2 `AppUserWorkServiceImpl.addChapter` 章节 status 改 `CHAPTER_DRAFT` + 类注释

- [x] **T5 步骤 5｜收敛可见性口径**
  - [x] T5.1 `SysContentWorkMapper.xml` 新增 `<sql id="readerVisibleWork">`（带 alias 属性）
  - [x] T5.2 `selectAppWorkVo` / `selectAppWorkById` / `selectAppRankingList` 改用 `<where>` + include
        （`selectAppWorkById` 为新增判定，补齐与列表的口径差）
  - [x] T5.3 `selectDraftWorks` 改为「本人 + 未删除 + 无审核记录」
  - [x] T5.4 `SysRankingSnapshotMapper.xml selectWorkMetricStats` 改跨命名空间 include
  - [x] T5.5 `SysTagMapper.xml selectAppTagList` 的 categoryId 子查询改用 include
  - [x] T5.6 `TradeWorkService.enableTrade`：只判 `on_shelf`（去掉 `approved` 兼容），文案与注释同步

- [x] **T6 测试**
  - [x] T6.1 新增 `WorkStatusDeriverTest`（derive 全分支 / isReviewing / isReaderVisible 真值表 / 章节可见性）
  - [x] T6.2 扩 `AppUserWorkServiceTest`：6 个新用例（下架成功、上架成功、reject_reason 拦截、
        未过审拦截、非法 status、addChapter 默认草稿）+ `workOf()` 带 `rejectReason` 重载
  - [x] T6.3 新增 `AppChapterAuthorServiceTest`（`ReflectionTestUtils` 注入 mock，断言 status="1"）

- [x] **T7 验证**
  - [x] T7.1 `mvn -pl smartscript-content -am test` 通过（0 failures/0 errors）
  - [x] T7.2 `mvn -pl smartscript-review -am -DskipTests package`、`mvn -pl smartscript-trade -am -DskipTests package` 编译通过
  - [x] T7.3 汇报改动清单（含 file 链接）与「需整仓重建后端后重启」提示
  - [x] T7.4 交由用户做端到端手测（spec.md §7.4 清单）

---

## 备注

- 不做数据库迁移脚本；不做新增列/表；不改 App 端 Dart / PC 端 Vue。
- 若 T7.1 出现失败，先修测试再继续，不得跳过。
- 云端库慢：任何依赖真库的验证若超时，用 `-j 1 --timeout 240s`。
