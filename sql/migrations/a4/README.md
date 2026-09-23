# A4 PC 管理能力 — 数据库迁移说明

## 1. 阶段信息

| 项目 | 内容 |
| --- | --- |
| 联调编号 | `A-A4-PC-ADMIN-001` |
| 契约版本 | `A4-PC-ADMIN-CONTRACT-v1` |
| 迁移版本 | `A4_20260922_001`（结构增量）、`A4_20260922_002`（菜单与权限） |
| 目标库 | MySQL 8.0.36 |
| 实施方案 | `A4-PC管理实施方案.md` §6.2 |
| 裁决依据 | `A4-P0-页面资产清单与实施计划.md` §3.4（GAP-1～GAP-6） |

## 2. 关键前提：A4 不重建业务表

A2 迁移 (`sql/migrations/a2/`) 已经创建了 A4 所需的五张业务表：
`user_real_name_auth`、`user_author_capability`、`user_notification`、
`user_notification_receiver`、`user_feedback`。

因此 **A4 迁移的定位是「复用 A2 结构 + 增量对齐契约」**，不是从零建表。
A4 不删除、不重建、不改名这五张表的既有列。

## 3. 文件清单与执行顺序

| 顺序 | 文件 | 作用 | 可重复 |
| ---: | --- | --- | --- |
| 1 | `A4_20260922_001__a4_precheck.sql` | 只读前置检查；任一 FAIL 阻断后续 | 是 |
| 2 | `A4_20260922_001__a4_migrate.sql` | 结构增量（GAP-1/2/3） | 是 |
| 3 | `A4_20260922_001__a4_verify.sql` | 迁移后校验 | 是 |
| 4 | `A4_20260922_002__a4_permissions.sql` | 菜单 + 15 个权限标识 | 是 |
| 5 | `A4_20260922_002__a4_permissions_verify.sql` | 权限校验 | 是 |
| 6 | `A4_20260922_003__app_grantable_role.sql` | `sys_role.app_grantable` 角色可授权标记 | 是 |
| 7 | `A4_20260922_003__app_grantable_role_verify.sql` | 角色可授权标记校验 | 是 |
| R1 | `U20260922_003__app_grantable_role_rollback.sql` | 回滚角色标记（最先回滚） | 是 |
| R2 | `U20260922_002__a4_permissions_rollback.sql` | 回滚权限 | 是 |
| R3 | `U20260922_001__a4_rollback.sql` | 回滚结构增量（最后回滚） | 是 |

执行示例：

```bash
mysql -h 127.0.0.1 -P 3306 -u <user> -p <db> < A4_20260922_001__a4_precheck.sql
mysql -h 127.0.0.1 -P 3306 -u <user> -p <db> < A4_20260922_001__a4_migrate.sql
mysql -h 127.0.0.1 -P 3306 -u <user> -p <db> < A4_20260922_001__a4_verify.sql
mysql -h 127.0.0.1 -P 3306 -u <user> -p <db> < A4_20260922_002__a4_permissions.sql
mysql -h 127.0.0.1 -P 3306 -u <user> -p <db> < A4_20260922_002__a4_permissions_verify.sql
mysql -h 127.0.0.1 -P 3306 -u <user> -p <db> < A4_20260922_003__app_grantable_role.sql
mysql -h 127.0.0.1 -P 3306 -u <user> -p <db> < A4_20260922_003__app_grantable_role_verify.sql
```

回滚顺序完全相反，且**先回滚角色标记，再回滚权限，最后回滚结构**。

## 4. 结构增量内容（001）

| 裁决 | 变更 | 说明 |
| --- | --- | --- |
| GAP-1 | `user_notification.request_id VARCHAR(64) NULL` + 唯一索引 `uk_user_notification_request_id` | 消息创建幂等键；历史行允许 NULL |
| GAP-2 | `user_notification.business_type` / `business_id` 各 `VARCHAR(64) NULL` | 新写入只用双列；`biz_ref` 作为兼容列**保留**，历史行不回填 |
| GAP-3 | `user_feedback.status` 默认值 `OPEN` → `SUBMITTED`，并加 `CHECK ck_user_feedback_status` | 存量 `OPEN` 先快照再归一；未知状态由 precheck 阻断 |

未产生 DDL 的裁决：GAP-4 收件人数由 `COUNT` 聚合派生，不设冗余列；
GAP-5 材料/附件单值映射为零或单元素数组，不解析分隔符；
GAP-6 实名表只做 DTO 字段映射，**不改列名**。

### 4.4 角色可授权标记（003，P1-R1 裁决）

`sys_role` 原本没有 App 可用性标记，且若依没有可复用的既有约定。契约 §5.1 只要求
「只允许授权配置为 App 可用的角色」，未定义该标记的落库方式。评审裁决新增：

| 项目 | 内容 |
| --- | --- |
| 列定义 | `app_grantable TINYINT(1) NOT NULL DEFAULT 0`，位于 `data_scope` 之后 |
| 唯一权威来源 | 该列；**不**使用部署配置白名单、`remark`、角色名前缀或 `role_key` 命名约定 |
| 维护方式 | 若依原生角色管理页的「可授予 App 用户」开关，复用 `system:role:edit` 与原生操作日志 |
| 授权条件 | `app_grantable=1` **且** `status='0'` **且** `del_flag='0'` **且**非超级管理员 |
| 永久拒绝 | `role_id=1` 或 `role_key='admin'` 无论标记为何均不可授予 App 用户 |

默认值为 0（拒绝）：迁移**不**为任何业务角色预先置 1，需管理员显式开启。

**超级管理员的永久拒绝在 SQL 层强制**（`AppUserAdminMapper.countNonGrantableRoles`），
不依赖标记值兜底：迁移会把被误标记的超级管理员纠正为 0，即使纠正被绕过，
SQL 的 `role_id = 1 OR role_key = 'admin'` 仍会计入不可授权并拒绝授权。
`precheck` 与 `verify` 都会对被误标记的超级管理员报 FAIL 阻断。

新增接口 `GET /api/v1/admin/app-users/grantable-roles`（权限 `user:app:grant`）
返回该清单，字段限于 `roleId`/`roleName`/`roleKey`。

**回滚安全**：若已有 App 账号域用户实际持有被标记为可授权的角色，回滚默认
`REFUSE_IN_USE` 并安全停止（该标记已被投入使用，回滚会静默改变授权语义），
需显式 `SET @a4_force_rollback = 1` 才继续。回滚只删标记列，不删角色、不改角色状态。

### 4.1 控制表

| 表 | 作用 |
| --- | --- |
| `a4_migration_history` | A4 版本登记；只标记 `rolled_back`，不删除审计行 |
| `a4_feedback_status_preimage` | 记录被归一行的原状态。**只增不删**，保证 `migrate → rollback → migrate` 重跑时复用原始前像 |

### 4.2 归一前像与精确回滚

`OPEN → SUBMITTED` 归一前，先把受影响行的 `id` 与 `status_before` 写入
`a4_feedback_status_preimage`。回滚时**只把快照中记录、且当前仍为 `SUBMITTED` 的行**还原为 `OPEN`，
因此 A4 之后由真实业务流转产生的 `PROCESSING/REPLIED/CLOSED` 结果不会被破坏。

### 4.3 重跑守卫

`migrate` 开头会检测「状态集合约束不存在但存在 `OPEN` 行」的状态，识别为
`REAPPLY_AFTER_ROLLBACK` 并在无约束前提下完成归一，再重建约束；
若「约束存在但仍有 `OPEN` 行」，说明数据被外部改写，脚本输出 `MIGRATE_ABORTED` 停止，不猜测转换。

## 5. 回滚安全策略

`U20260922_001` 默认拒绝破坏性回滚：若 `user_notification` 的 `business_type`/`business_id`
存在非空业务值，`DROP COLUMN` 会造成不可逆丢失，脚本输出 `ROLLBACK_ABORTED` 并跳过**全部** DDL。
确认可丢弃后，显式声明后重跑：

```sql
SET @a4_force_rollback = 1;
```

`U20260922_002` 同理：若 A4 菜单已被非超级管理员角色额外授权（说明已投入使用），
默认 `REFUSE_IN_USE`，需显式放行。

两个回滚脚本都**不会**删除 A2 五张业务表，也不会修改 `sys_user` 任何数据。

## 6. 权限与菜单（002）

新建顶级目录 `A4用户管理`（`path=appuser`），下挂 5 个页面与 10 个按钮，
共 16 个菜单、15 个权限标识。菜单固定使用 `menu_id` 段 **3000–3015**（远离
`sys_menu.AUTO_INCREMENT`，避免与若依后台新增菜单抢号）。

| 页面 | 组件 | 权限标识 |
| --- | --- | --- |
| A4-App用户与创作者 | `user/UserManage` | `user:app:list` / `query` / `status` / `grant` |
| A4-实名审核 | `user/realname/index` | `user:realname:list` / `query` / `audit` |
| A4-作者能力 | `user/UserManage` | `user:creator:list` / `update` |
| A4-用户消息 | `user/message/index` | `user:message:list` / `add` / `query` |
| A4-用户反馈 | `user/feedback/index` | `user:feedback:list` / `query` / `handle` |

授权仅授予超级管理员角色（与若依 `admin` 判定一致），不扩散到普通角色。
后端同名 `@PreAuthorize` 注解为最终强制边界；前端菜单与按钮只提供可见性体验层。

`user/UserManage` 被两个页面复用：页面内按权限标识分别渲染 App 用户卡片与作者能力卡片，
对应 P0 裁决中「前两张卡片分别接入真实接口」的要求。

不复制若依原生管理员/角色/菜单/岗位/字典/参数/日志体系；`menu_id=100`（系统管理下的用户管理）
保持原样，权限校验脚本显式断言其未被改动。

## 7. 发布顺序与兼容

先执行 001、002 并确认两个 verify 为 PASS，再发布后端接口，最后发布 PC 页面。
迁移完成前，前端不应展示 A4 菜单（由权限数据缺失自然保证）。
最终合入顺序为 后端 → Web → 联合回归；回滚顺序相反。

## 8. 已验证结论（隔离库 `ruoyi_dev_a4_test`）

| 项目 | 结果 |
| --- | --- |
| precheck | PASS（13/13 检查项） |
| migrate | `MIGRATE_DONE`；`OPEN` 归一 2 行，新增列 3 个 |
| 重复执行 | 幂等，无重复数据、无重复快照 |
| verify | PASS（18/18 检查项） |
| rollback（有业务数据） | `ROLLBACK_ABORTED`，全部 DDL 跳过，列与表均未变动 |
| rollback（无业务数据） | 列移除、2 行精确还原为 `OPEN`、A2 五表完好 |
| 重跑 migrate | 守卫识别 `REAPPLY_AFTER_ROLLBACK`，verify 再次 PASS |
| 权限迁移 002 | 16 菜单 / 15 权限标识 / 16 授权；重复执行幂等 |
| 权限 verify | PASS（12/12 检查项） |
| 权限回滚 + 重放 | 菜单归零后重放，verify 再次 PASS |
| 角色标记迁移 003 | 列建立；默认全部 `app_grantable=0`（默认拒绝）；管理员标记被纠正 1 行 |
| 角色标记 verify | PASS（8/8 检查项） |
| 001 precheck（含 003） | PASS（15/15，新增角色形态与管理员拒绝两项） |
| 001 verify（含 003） | PASS（21/21，新增角色形态、标记域、管理员拒绝三项） |
| 绕过测试 | 手工标记管理员为 1 → precheck FAIL；003 纠正后清单排除管理员；授权校验仍判 1（拒绝）；停用角色不进清单 |
| 角色标记回滚（已投入使用） | `REFUSE_IN_USE`，列未删除 |
| 角色标记回滚（无占用） | 列移除，角色数据与 `system:role:edit` 菜单完好 |

证据目录：`D:\build\shared\a4-evidence`（`10`～`14` 号日志）。

## 9. 初始化流程引入后的校验项调整

为支持「全新空库一次跑通、每步 `SUMMARY` 必须 `PASS`」，`A4_20260922_001__a4_verify.sql`
的账号域检查做了一处语义修正：

| 项目 | 原实现 | 现实现 |
| --- | --- | --- |
| check_id | `app_user_domain_nonempty` | `app_user_domain_count`（信息项，不参与判定） |
| 条件 | `COUNT(*) > 0` 才 `PASS` | 恒为 `PASS`，`detail` 报出 `app_user_rows=N` |

原因：本迁移只做 `user_notification` / `user_feedback` / `sys_role` 的增量，
**不创建任何用户**。App 域（`user_type` 为 `01/02/03`）用户由 App 注册流程产生，
因此全新初始化的若依基线该计数必然为 0。原判定实际断言的是「联调库里已灌入 App 用户」
这一测试环境条件，会让空库初始化无法通过，不属于迁移不变式。

保留下来的强制项：`account_domain_defined` 仍要求所有 `sys_user.user_type` 取值合法
（`00/01/02/03`），取值非法即 `FAIL`。A4 的其余 20 项结构校验未做任何放宽。

