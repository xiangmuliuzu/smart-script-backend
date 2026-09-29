# A5 迁移：个人简介（bio）

| 项 | 值 |
| --- | --- |
| 迁移版本 | A5_20260928_001 |
| 目标库 | 平台主库（sys_user 所在库） |
| 依据 | 《A用户与认证开发规格》§8.3（简介）、《A5-App用户中心实施方案与接口契约》§1.2（2026-09-28 修订版） |
| 性质 | 结构新增（纯加列），不改既有列、不改数据语义 |

## 文件清单与执行顺序

| 顺序 | 文件 | 性质 | 可重复 |
| --- | --- | --- | --- |
| 1 | `A5_20260928_001__user_profile_bio_precheck.sql` | 只读前置检查 | 是 |
| 2 | `A5_20260928_001__user_profile_bio_migrate.sql` | 写（加列） | 是（列已存在则跳过） |
| 3 | `A5_20260928_001__user_profile_bio_verify.sql` | 只读校验 | 是 |
| R | `U20260928_001__user_profile_bio_rollback.sql` | 回滚（危险，默认拒绝） | 是 |

## 前置依赖

- a2 → a1 → a4 → pc → h12 既有迁移均已执行（`sys_user` 表存在）。
- 执行前完成 mysqldump 备份（至少 `sys_user` 表）。

## 变更内容

- `sys_user` 新增列 `bio VARCHAR(200) NOT NULL DEFAULT ''`，位于 `remark` 之后；
  空串表示未填写。**不借用 `remark` 等既有字段**。
- 服务端校验上限 200 字符（`AppUserProfileService.BIO_MAX`），Flutter 本地预校验一致。

## 回滚安全策略

- 回滚脚本默认拒绝执行：当 `bio` 存在非空用户数据且未显式
  `SET @a5_force_bio_rollback = 1` 时输出 `ROLLBACK_ABORTED`。
- 正确回滚姿势：先 `mysqldump` 留底 `sys_user` 的 `bio` 列，再 force 执行，并保留 dump。

## 已验证结论

- **2026-09-28 已在开发库 `ruoyi_dev`（MySQL 8.0.36）连库执行**：
  - 备份：`shared/backups/ruoyi_dev_pre_a5_bio_20260928.sql`（mysqldump 全库，194 账号）。
  - precheck PASS（fail_cnt=0）、migrate 成功（列已存在时跳过）、verify PASS（fail_cnt=0）。
    输出留档：`shared/a5-evidence/A5-20260928-bio-{precheck,migrate,verify}.log`。
  - 生产/其他环境部署仍需按根 `sql/migrations/README.md` 流程执行并保留输出。
- **回滚演练（隔离库 `ruoyi_dev_a5_drill`，演练后已删除）**：
  - 有用户数据且未 force → `ROLLBACK_ABORTED`；force 后列删除；重复执行 →
    `column not exists - nothing to roll back`（可重复执行）。
    留档：`shared/a5-evidence/A5-20260928-bio-rollback-drill.log`。
- 迁移为纯加列，既有查询不受影响（SELECT 列清单显式，未用 `SELECT *`）。
