# Spec：作品生命周期重构（后端 1~5 步）

日期：2026-10-20
范围：`smart-script-backend`（A/B 模块后端）。**不含 App 端 / PC 前端页面改动**。
作者：向思鹏（B 模块内容与作品）

---

## 1. 目标

把「作品生命周期状态」与「上架开关」拆成两个正交维度，消除当前把 `sys_work.status`
当审核状态用的病根（`AppWorkManageServiceImpl.deriveReviewResult`），并落实用户已认可的三条核心规则：

- 上下架不是作者的日常动作；
- 审核只在**作品级**做一次，审核通过即自动上架；
- 章节必须有**草稿 / 发布**两态。

## 2. 模型与不变式（后续所有改动必须守住）

| # | 不变式 |
|---|--------|
| 1 | `sys_work.status` ∈ `{on_shelf, off_shelf}`，**只表示可见性开关**，不承载生命周期 |
| 2 | 生命周期状态由 `sys_review_record`（`target_type='work'`）**最新一条**（`created_at desc` 第一条）的 `status` 派生：无记录→`draft`；`pending`/`ai_reviewing`→`reviewing`；`approved`→`published`；`revision`/`rejected` 原样返回 |
| 3 | 读者可见公式：`is_deleted=0` **AND** `status='on_shelf'` **AND** 最新审核=`approved`；章节再加 `sys_work_chapter.status='0'` |
| 4 | `sys_work_chapter.status`：`0`=已发布（读者可见）/ `1`=草稿（仅作者可见）。存量 `'1'`（原「停用」）语义变为草稿，两义都不可见，天然兼容 |
| 5 | 作者「申请下架」= `off_shelf`，**不改审核记录** → 可自助重新上架、免重审 |
| 6 | 平台「强制下架」= `off_shelf` **且写 `sys_work.reject_reason`**（复用现有列）→ 作者端禁止自助上架，须改完重新提交审核 |
| 7 | 审核流转到 `approved` 时，自动把 `sys_work.status` 置 `on_shelf`（当前缺失的一步） |
| 8 | 审核中锁定编辑、不可重复提交（已实现，保留） |

## 3. 明确不做

- 不新增数据库列 / 表，不做数据迁移脚本；
- 不做完本状态、章节级审核；
- 不把 `revision` 提为独立作品状态（UI 与「待修改」归并）；
- 不改 App 端 Dart 代码、不改 PC 前端 Vue 代码；
- 不动 4 个「我的」列表（书架 / 收藏 / 订阅 / 播放历史）的 `status='on_shelf'` 简化判定，
  它们面向用户自有集合，与书城口径不同（见 §5.5 说明）。

## 4. 关键事实（已核实，作为改动依据）

1. **依赖方向**：`smartscript-content` → 仅 `ruoyi-common` + `ruoyi-framework`；`smartscript-review` → 仅 `ruoyi-common` + web + mybatis + pagehelper；`smartscript-trade` → 不含 content。**content 与 review 之间没有任何 pom 依赖（双向都没有）**，因此不能用直接 Java 调用耦合，审核通过钩子落在 review 模块自己的 Mapper 上（同一张 `sys_review_record` / `sys_work` 库）。
2. `sys_review_record` 被两个模块各自的 Mapper 读写：content 侧 `SysWorkReviewRecordMapper`（读 `selectByWorkId`/`selectByWorkIds`，写 `insertRecord`），review 侧 `ReviewRecordMapper`（PC 管理端读写 + `updateReviewRecord`）。
3. PC 审核入口：`ReviewController` `POST /api/v1/admin/review/operate` → `ReviewService.operateReview(reviewId, status, ...)`，当前**只改 `sys_review_record`，不碰 `sys_work.status`**。
4. `ReviewService` 状态机：`ai_reviewing`→`pending`/`revision`/`rejected`；`pending`→`approved`/`rejected`/`revision`；`approved` 为终态。
5. `sys_work.status` 的全部写入点只有 2 个 Mapper 方法：`SysContentWorkMapper.updateWorkStatus`（被 App `changeShelfStatus` 与 PC `SysBookstoreServiceImpl.updateWorkStatus` 调用）、`SysContentWorkMapper.updateCopyrightAssetStatus`（PC 版权资产）。
6. App 侧「作品可见性」的唯一闸门是 `SysContentWorkMapper.selectAppWorkById`，被书城详情、加入书架、加入收藏、评分共用。
7. 读者可见性 SQL 目前重复 4 处（全量条件）：`selectAppWorkVo`、`selectAppRankingList`、`SysRankingSnapshotMapper.selectWorkMetricStats`、`SysTagMapper.selectAppTagList`；`selectAppWorkById` **缺**最新审核判定（口径不一致）。
8. 章节新增有两个入口：`AppChapterAuthorServiceImpl.createChapter`（`DEFAULT_STATUS="0"`）与 `AppUserWorkServiceImpl.addChapter`（`chapter.setStatus("0")`）。发布走已有 `PUT /api/v1/content/chapters/{chapterId}`（`AppChapterWriteController` 已支持 `status` 入参）。
9. 读者读章节已按 `status='0'` 过滤：`AppBookstoreServiceImpl.getChapterCatalog` / `getChapterContent` 走 `STATUS_NORMAL`；目录与正文同口径，`status='1'` 章节读者不可见。
10. `AppWorkManageServiceImpl.createWork` 目前把 `status` 写成 `'draft'`，`selectDraftWorks` 也按 `status='draft'` 过滤 → 与不变式 1 冲突，且「驳回后的作品仍留在草稿箱」是现有缺陷。
11. 云端库 `sys_work.status` 历史合法域实测含 `draft/pending/approved/on_shelf/off_shelf`（种子脚本 `B_20261009_003` 注释）。因此**不能靠刷库**达成不变式 1，改用「读者查询只匹配 `on_shelf`」+「草稿箱按派生判定」来兼容存量，存量生命周期值对读者不可见，是预期效果。
12. `ReviewRecord`（review 模块 domain）已具备 `targetType` / `targetId` / `status` getter，可直接用于钩子判断。

## 5. 改动清单（逐文件、逐方法）

### 5.1 步骤 1 — 状态派生工具类

**新增** `smartscript-content/src/main/java/com/smartscript/platform/content/util/WorkStatusDeriver.java`

- 作品常量：`ON_SHELF="on_shelf"`、`OFF_SHELF="off_shelf"`、`DRAFT="draft"`、`REVIEWING="reviewing"`、`REVISION="revision"`、`REJECTED="rejected"`、`PUBLISHED="published"`、`APPROVED="approved"`。
- 章节常量：`CHAPTER_PUBLISHED="0"`、`CHAPTER_DRAFT="1"`。
- 私有 `REVIEWING_REVIEW_STATUS = Set.of("pending","ai_reviewing")`。
- `public static String derive(String latestReviewStatus)`：`null/空`→`DRAFT`；命中 reviewing 集合→`REVIEWING`；`approved`→`PUBLISHED`；其余原样（`revision`/`rejected`）。
- `public static boolean isReviewing(String latestReviewStatus)`。
- `public static boolean isReaderVisible(String workStatus, String latestReviewStatus)`：`ON_SHELF.equals(workStatus) && APPROVED.equals(latestReviewStatus)`。
- `public static boolean isChapterReaderVisible(String chapterStatus)`：`!CHAPTER_DRAFT.equals(chapterStatus)`（`'0'` 可见）。
- final 类 + 私有构造（纯静态工具）。

**改** `AppUserWorkServiceImpl.java`
- 删除类内 `REVIEWING_STATUS` 常量与 `deriveStatus(SysWorkReviewRecord)` 的 switch 实现，改为委托：
  `deriveStatus(latest)` → `WorkStatusDeriver.derive(latest == null ? null : latest.getStatus())`。
- `requireEditable(workId, userId)` 改用 `WorkStatusDeriver.isReviewing(records.get(0).getStatus())`。
- 类注释（L22-31）改述为「status 只表示上架开关；生命周期由审核记录派生」并指向 `WorkStatusDeriver`。
- 构造器签名**保持不变**（4 个 Mapper），避免破坏 `AppUserWorkServiceTest` 的 `new` 调用。

**改** `AppWorkManageServiceImpl.java`（病根修复）
- 新增 `@Autowired private SysWorkReviewRecordMapper reviewRecordMapper;`。
- 删除 `deriveReviewResult(String status)` 与 `RESULT_*` 三个常量里「按 sys_work.status 派生」的实现，改为：
  `private static String reviewResultOf(String latestReviewStatus)` → `approved`→`approved`；`rejected`→`rejected`；其余（含 null/revision/reviewing）→`pending`。
- `getReviewStatus(Long workId)`：
  - `SysWork work = requireOwned(...)`（不变，null 直接返回 null）；
  - 取 `List<SysWorkReviewRecord> records = reviewRecordMapper.selectByWorkId(workId);`，`latest = records.isEmpty() ? null : records.get(0).getStatus();`
  - `dto.setStatus(WorkStatusDeriver.derive(latest))`（不再是 `work.getStatus()`）；
  - `dto.setReviewComment(work.getRejectReason())`（不变）；
  - `dto.setReviewResult(reviewResultOf(latest))`。
- `createWork`：`work.setStatus(WorkStatusDeriver.OFF_SHELF)`（原 `STATUS_DRAFT`）；删除 `STATUS_DRAFT` 常量；类注释「反推处理点 1/3」同步改正（status 只写 `off_shelf`，审核状态派生）。

**改** `AppReviewStatusDto.java` 字段注释：`status ← 由 sys_review_record 最新一条派生（draft/reviewing/revision/rejected/published）`；`reviewResult ← 同一来源派生（approved/rejected/pending）`；`reviewComment ← sys_work.reject_reason`。

### 5.2 步骤 2 — 审核通过钩子（自动上架）

**改** `smartscript-review/.../mapper/ReviewRecordMapper.java`
- 新增 `int markWorkOnShelf(@Param("workId") Long workId, @Param("updateBy") String updateBy);`（`org.apache.ibatis.annotations.Param` 已在该文件可用）。

**改** `smartscript-review/src/main/resources/mapper/ReviewRecordMapper.xml`
```xml
<!-- 审核通过后把作品置为已上架（lifecycle=approved 的可见性副产物；is_deleted=1 的行不处理） -->
<update id="markWorkOnShelf">
    UPDATE sys_work
    SET status = 'on_shelf',
        review_time = sysdate(),
        update_by = #{updateBy},
        update_time = sysdate()
    WHERE work_id = #{workId} AND is_deleted = 0
</update>
```

**改** `smartscript-review/.../service/ReviewService.java` → `operateReview`
- 方法加 `@Transactional(rollbackFor = Exception.class)`（`org.springframework.transaction.annotation.Transactional`）。
- 在 `int cnt = reviewRecordMapper.updateReviewRecord(reviewRecord);` 之后、写日志之前插入：
```java
// 审核通过 → 内容侧作品自动上架（两个业务模块无编译期依赖，故在共享库的 Mapper 层落地）
if (cnt > 0 && "approved".equals(status) && "work".equals(before.getTargetType()) && before.getTargetId() != null) {
    reviewRecordMapper.markWorkOnShelf(before.getTargetId(),
            reviewerId == null ? null : String.valueOf(reviewerId));
}
```
- 返回体不变（`code/msg/from/to/cnt`），`markWorkOnShelf` 影响 0 行（作品已删）不视为失败。

### 5.3 步骤 3 — 作者「申请下架 / 重新上架」语义

**改** `AppUserWorkServiceImpl.changeShelfStatus(Long userId, Long workId, String status)`
```java
SysWork work = requireOwnWork(userId, workId);

if (!WorkStatusDeriver.ON_SHELF.equals(status) && !WorkStatusDeriver.OFF_SHELF.equals(status)) {
    throw new IllegalArgumentException("非法的上下架状态");
}

// 重新上架：平台强制下架的作品（reject_reason 有值）禁止作者自助上架；
// 且必须已通过审核，避免绕过审核直接上架
if (WorkStatusDeriver.ON_SHELF.equals(status)) {
    if (work.getRejectReason() != null && !work.getRejectReason().isBlank()) {
        throw new IllegalStateException("作品因违规被平台下架，请修改后重新提交审核");
    }
    List<SysWorkReviewRecord> records = reviewRecordMapper.selectByWorkId(workId);
    String latest = records.isEmpty() ? null : records.get(0).getStatus();
    if (!WorkStatusDeriver.APPROVED.equals(latest)) {
        throw new IllegalStateException("作品尚未通过审核，不能上架");
    }
}

// 下架只降低可见性，不再要求「必须先审核通过」的前置条件
SysWork update = new SysWork();
update.setWorkId(workId);
update.setStatus(status);
update.setUpdateBy(String.valueOf(userId));
update.setUpdateTime(new Date());
workMapper.updateWorkStatus(update);
```

**改** `IAppUserWorkService.java` `changeShelfStatus` 方法注释：由「作者自主上下架，仅 published 可操作」改为「作者申请下架 / 重新上架」；
下架无前置；重新上架需最新审核为 `approved` 且 `reject_reason` 为空。

**改** `AppUserWorkController.java` L310-313 方法注释同步（「仅已通过审核（published）的作品可操作」→ 新语义）。

**不动**：`SysBookstoreServiceImpl.updateWorkStatus`（PC 管理端上下架）逻辑保持；`reject_reason` 的写入由其前端调用方按需提供（本次不新增 PC 写入链路）。

### 5.4 步骤 4 — 章节两态（草稿 / 发布）

**改** `AppChapterAuthorServiceImpl.java`
- `DEFAULT_STATUS`：`"0"` → `WorkStatusDeriver.CHAPTER_DRAFT`（`"1"`）；常量注释改为「章节状态默认值（1=草稿，仅作者可见，发布由 PUT 传 status="0" 触发）」。
- 类注释「反推处理点 4」同步改正。

**改** `AppUserWorkServiceImpl.addChapter`
- `chapter.setStatus("0")` → `chapter.setStatus(WorkStatusDeriver.CHAPTER_DRAFT)`；类注释中「新增默认 status='0'」改为 `'1'`。

**不改** `AppWorkManageServiceImpl.updateChapter`（作者编辑）与 `AppChapterWriteController`：发布路径 `PUT /api/v1/content/chapters/{chapterId}` 传 `status='0'` 已支持。
读者侧过滤不变（`AppBookstoreServiceImpl` 的 `STATUS_NORMAL` 判定已生效）。

### 5.5 步骤 5 — 收敛可见性口径

**改** `SysContentWorkMapper.xml`
- 新增共享片段（唯一口径来源）：
```xml
<!-- 读者可见作品统一判定（书城/榜单/统计同口径，等价于 WorkStatusDeriver.isReaderVisible）：
     is_deleted=0 且 status='on_shelf' 且最新一条审核记录 status='approved'。
     调用方必须通过 property alias 传入 sys_work 表别名，并用 <where> 包裹以吃掉前导 and。 -->
<sql id="readerVisibleWork">
    and ${alias}.is_deleted = 0
    and ${alias}.status = 'on_shelf'
    and exists (
        select 1 from sys_review_record r
        where r.target_type = 'work'
          and r.target_id = ${alias}.work_id
          and r.status = 'approved'
          and r.created_at = (
            select max(r2.created_at) from sys_review_record r2
            where r2.target_type = 'work' and r2.target_id = ${alias}.work_id
          )
    )
</sql>
```
- `selectAppWorkVo`：把原内联的 `where w.is_deleted = 0 and w.status = 'on_shelf' and exists(...)` 替换为
```xml
    <where>
        <include refid="readerVisibleWork"><property name="alias" value="w"/></include>
    </where>
```
（外层 `selectAppWorkList` 的 `<if>AND ...</if>` 保持不变，追加在 where 之后合法）
- `selectAppWorkById`：同样改为 `<where>` + include，再保留 `and w.work_id = #{workId}` → **补齐缺失的最新审核判定，与列表口径一致**。
- `selectAppRankingList`：同样改为 `<where>` + include（`<choose>` 排序不变）。
- `selectDraftWorks`（2.9.5 草稿箱）：过滤条件改为
```sql
where w.is_deleted = 0 and w.author_id = #{userId}
  and not exists (select 1 from sys_review_record r
                  where r.target_type = 'work' and r.target_id = w.work_id)
order by w.create_time desc, w.work_id desc
```
即「本人 + 未删除 + **从未提交审核**」，取代对不可靠的 `status='draft'` 的依赖（顺带修掉「驳回后仍进草稿箱」的缺陷）。

**改** `SysRankingSnapshotMapper.xml` `selectWorkMetricStats`
- 内联块替换为跨命名空间 include：
```xml
from sys_work w
<where>
    <include refid="com.smartscript.platform.content.mapper.SysContentWorkMapper.readerVisibleWork">
        <property name="alias" value="w"/>
    </include>
</where>
```

**改** `SysTagMapper.xml` `selectAppTagList` 的 `categoryId` 子查询
- `and w.is_deleted = 0 and w.status = 'on_shelf'` 替换为 include（alias=w），子查询其余条件（`wt.tag_id = sys_tag.tag_id`、`w.genre_id = #{categoryId}`）保留。

**改** `smartscript-trade/.../service/TradeWorkService.enableTrade`
- `boolean listable = "approved".equals(status) || "on_shelf".equals(status);` → `boolean listable = "on_shelf".equals(status);`
- 提示文案：`off_shelf` → 保持「作品已在内容侧下架，请先上架后再发布到交易」；其余 → 「仅已上架的作品可发布到交易，当前状态: 」。
- 方法上方注释（L87-95）改写：审核通过即自动上架，`status` 只需判 `on_shelf`，不再兼容历史 `approved`。

**明确不改**（记录为有意决策）：`AppPlayHistoryMapper` / `AppBookshelfMapper` / `AppFavoriteMapper` / `AppSubscriptionMapper` 仍只用 `w.status='on_shelf'`。理由：这些是「我的」个人列表，只要求作品未下架，不要求审核状态；且避免给云端慢库（单条 SQL 1.7~2.5s）再加相关子查询。

## 6. 测试计划

### 6.1 新增 `WorkStatusDeriverTest`（`smartscript-content/src/test/java/.../content/util/`）
- `derive`：null→draft；pending/ai_reviewing→reviewing；approved→published；revision→revision；rejected→rejected。
- `isReviewing`：pending/ai_reviewing 为 true，approved/rejected/null 为 false。
- `isReaderVisible` 真值表：`on_shelf+approved`=true；`on_shelf+pending`=false；`off_shelf+approved`=false；null 组合=false。
- `isChapterReaderVisible`：`"0"`=true，`"1"`=false。

### 6.2 改 `AppUserWorkServiceTest`
- 保留全部现有用例（派生口径与 `derive` 实现一致，断言不变）。
- **新增**：
  1. `changeShelfStatus` 下架：作品 `on_shelf`、最新 `approved`、`rejectReason=null` → 成功，`verify(workMapper).updateWorkStatus(argThat(w -> "off_shelf".equals(w.getStatus())))`。
  2. 重新上架成功：最新 `approved`、`rejectReason=null` → 成功。
  3. 重新上架被 `reject_reason` 拦截 → `IllegalStateException`，`never()` 调 `updateWorkStatus`。
  4. 重新上架被「未通过审核」拦截（无记录 / `pending`）→ `IllegalStateException`，`never()` 调 `updateWorkStatus`。
  5. 非法 `status` 参数（如 `"draft"`）→ `IllegalArgumentException`。
  6. `addChapter` 新增章节 `status == "1"`（草稿）。
- 需要新增 mock：`workMapper.selectWorkById` 返回带 `rejectReason` 的 `SysWork`（现有 `workOf()` 加一个带参重载）。

### 6.3 新增 `AppChapterAuthorServiceTest`（同包 `content/service`）
- `AppChapterAuthorServiceImpl` 用字段注入，测试内用 `org.springframework.test.util.ReflectionTestUtils.setField` 注入 mock 的 `chapterMapper`/`workMapper`/`identityProvider`。
- 断言 `createChapter` 写入的 `SysWorkChapter.status == "1"`（`verify(chapterMapper).insertChapter(argThat(c -> "1".equals(c.getStatus())))`）。

### 6.4 编译覆盖
- review / trade 模块无现成单测，以编译通过 + 手工接口验证覆盖。

## 7. 验证步骤（必须全部执行）

1. `mvn -pl smartscript-content -am test` → **0 failures / 0 errors**。
2. `mvn -pl smartscript-review -am -DskipTests package`（或 `test`）编译通过；`mvn -pl smartscript-trade -am -DskipTests package` 编译通过。
3. 整仓重建后端并重启（仅重启可能因旧包缓存导致修复不生效）。
4. 端到端手测（PC 管理端 + App 接口）：
   - App 创建作品 → `GET /api/v1/content/works/{workId}/review-status` 返回 `status="draft"`、`reviewResult="pending"`；DB `sys_work.status='off_shelf'`。
   - 提交审核 → PC `POST /api/v1/admin/review/operate` 传 `status="approved"` → DB `sys_work.status='on_shelf'`；作品出现在书城列表与榜单。
   - App 新增章节 → DB `sys_work_chapter.status='1'`；App 目录接口不返回该章；`PUT /api/v1/content/chapters/{chapterId}` 传 `status="0"` → 目录出现该章。
   - `PUT /api/v1/content/works/{workId}/shelf?status=off_shelf` → 书城列表与 `selectAppWorkById`（书城详情 / 加书架 / 加收藏 / 评分）均 404；再 `status=on_shelf` → 恢复。
   - 管理端书城页把作品置 `off_shelf` 且 `sys_work.reject_reason` 非空 → App 重新上架请求报「作品因违规被平台下架」。
   - `POST /trade/works`（enableTrade）对 `off_shelf` 作品被拒、对 `on_shelf` 作品通过。
   - 2.9.5 草稿箱：新建未提交作品可见；提交后被审核驳回的作品**不再**出现在草稿箱。
5. 若走云端库相关接口验证且出现超时，沿用 `-j 1 --timeout 240s` 口径。

## 8. 风险与回滚

| 风险 | 说明 | 处理 |
|------|------|------|
| `selectAppWorkById` 收紧 | 书架/收藏/评分复用该查询，会一并收紧可见性 | 预期效果；如误伤，回滚该 include 即可 |
| 存量 `status='draft'/'pending'/'approved'` 行 | 读者查询只匹配 `on_shelf`，故对读者不可见 | 符合预期，**不做刷库**；若要彻底归一另立迁移脚本 |
| 2.9.5 草稿箱口径变更 | 由「status='draft'」改为「无审核记录」 | 语义更准（剔除已驳回作品）；App 端 UI 无需改（列表字段不变） |
| 新增相关子查询 | 云端慢库可能变慢 | 本次只加到书城详情；若实测超时再评估索引 |

回滚粒度：5 个步骤相互独立，可单独回退对应文件；`WorkStatusDeriver` 为纯新增，回退只需还原调用点。

## 9. 待用户确认

1. 同意将 2.9.5 草稿箱判定由 `status='draft'` 改为「无审核记录」（连带修掉「驳回后仍在草稿箱」的缺陷），且 `createWork` 写入 `off_shelf`。
2. 同意「作者申请下架」不设前置条件、「重新上架」要求最新审核为 `approved` 且 `reject_reason` 为空。
3. 同意步骤 5 **不**扩散到书架/收藏/订阅/播放历史 4 个「我的」列表。
