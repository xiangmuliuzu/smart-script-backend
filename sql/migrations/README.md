# 数据库迁移与升级说明

本目录是**已有数据库**的唯一升级入口。全新空库请改用 `scripts/db/init-database.*`
（见仓库根 `README.md` 的「数据库初始化」）。

## 1. 两条路径，不要混用

| 场景 | 做法 |
| --- | --- |
| 库不存在，或库存在但**一张表都没有** | 运行初始化脚本，自动按 `scripts/db/init-steps.txt` 顺序执行全部阶段 |
| 库中**已有业务表/数据** | 本文档的升级流程：先备份 → 逐阶段执行 → 每阶段校验通过再进下一阶段 |

初始化脚本不会碰已有数据的库：检测到任何一张表即拒绝退出，且**永不执行
`DROP DATABASE`**。反过来，**禁止**对已有数据的库导入 `sql/ry_20260320.sql`：
该文件是 `drop table if exists` + `create table` 的全量基线，会清空同名表。

## 2. 升级前必做

```bash
# 1) 备份（结构 + 数据 + 例程 + 触发器）
mysqldump --single-transaction --routines --triggers -h <host> -P <port> \
  -u <user> -p <db> > backup-$(date +%Y%m%d%H%M).sql

# 2) 记录基线，便于回滚核对
mysql -h <host> -P <port> -u <user> -p <db> \
  -e "SELECT COUNT(*) FROM sys_user; SELECT COUNT(*) FROM sys_menu;"

# 3) 确认应用已停止写入（迁移期间不应有业务流量）
```

口令不要写进命令行历史：用 `mysql_config_editor`、`MYSQL_PWD` 或临时
`--defaults-extra-file`（权限 0600），与初始化脚本的做法一致。

## 3. 执行顺序（依赖关系，不可调换）

| 顺序 | 阶段 | 目录 | 断言 |
| ---: | --- | --- | --- |
| 1 | A2 结构加固 + App 表骨架 | `a2/` | precheck → migrate → verify，`SUMMARY` 必须 `PASS` |
| 2 | A1 产品菜单与受限运营角色 | `a1/` | 种子必须先于 A4；`SUMMARY` 必须 `PASS` |
| 3 | A4 PC 管理能力 | `a4/` | 001 → 002 → 003，各自 verify `SUMMARY` 必须 `PASS` |
| 4 | PC 侧边栏信息架构 | `pc/` | 先执行迁移，再跑 `..._sidebar_ia_verify.sql` |

依赖原因（改动顺序前先读）：

- **A1 必须早于 A4**：A4 前置检查要求顶级 `path='user'` 目录唯一，该目录由 A1 种子
  （`menu_id=5142`）提供。
- **PC 必须晚于 A1 与 A4**：它按 `menu_id` 重挂 A1 的产品菜单、改为绝对路径，并重排/改名
  A4 的 `3000-3015` 菜单；同时补写被 A1 种子「组件唯一性」守卫跳过的 `5142`。
- **A2 必须最先**：A1/A4 都依赖 A2 归一后的 `sys_user` 账号域（`00/01/02/03`）。

每个阶段按顺序执行 `<阶段>_<日期>_<序号>__*.sql`：**先 precheck（只读）→ 再 migrate
（写）→ 最后 verify（只读）**。任一 `SUMMARY` 出现 `FAIL` 或脚本输出 `*_ABORTED`
都必须停止排查，不要跳过继续。

示例（A4 阶段）：

```bash
for f in a4/A4_20260922_001__a4_precheck.sql \
         a4/A4_20260922_001__a4_migrate.sql \
         a4/A4_20260922_001__a4_verify.sql; do
  mysql --batch -h <host> -P <port> -u <user> -p <db> < "$f" || exit 1
done
```

判定要点：verify 脚本末尾汇总行的 `result` 必须是 `PASS`，且 `fail_cnt=0`。

## 4. 已有库升级时的 A1 前置快照

A1 种子会新建菜单与角色。已有库上先执行
`a1/A1_20260921_001__p5_pregrant_snapshot.sql`，把输出存为证据文件
（**不要提交到仓库**），回滚时用来核对是否恢复到授权前状态。
全新空库不需要：直接丢弃空库重建即可。

## 5. 幂等性与重跑

A2 / A4 / PC 各阶段脚本都设计为可重复执行（`IF NOT EXISTS`、条件 `ALTER`、按
`menu_id`/`remark` 前缀定位、历史表 UPSERT）。重跑安全，但仍应先备份：
A4 的 `migrate` 在「约束不存在但存在非法状态行」时会输出 `MIGRATE_ABORTED`
并停止，此时不要手工改数据绕过，按脚本提示排查。

A1 种子同样幂等，但它**有意**保留重复 `component`（`common/ModuleScaffold` ×9、
`copyright/ReviewWorkbench` ×2），这是前端按 `perms` 分卡片渲染的设计，不是脏数据。
`a1/..._p5_menu_verify.sql` 的 `DUP_COMPONENT` 输出是信息项，不参与判定。

## 6. 回滚顺序（与升级相反）

1. `a4/U20260922_003__app_grantable_role_rollback.sql`
2. `a4/U20260922_002__a4_permissions_rollback.sql`
3. `a4/U20260922_001__a4_rollback.sql`
4. `pc/U20260923_001__sidebar_ia_rollback.sql`
5. `a1/U20260921_001__p5_menu_rollback.sql`
6. `a2/U20260921_001__a2_rollback.sql`

A4 与 PC 的回滚默认拒绝破坏性操作（输出 `ROLLBACK_ABORTED` / `REFUSE_IN_USE`）：
若相关能力已投入使用，需显式设置脚本头部声明的开关才继续。回滚脚本都不删除
A2 建的业务表，也不修改 `sys_user` 业务数据。

## 7. 校验脚本的阶段语义

校验脚本断言的是**该阶段结束时的状态**：

- `a1/A1_20260921_001__p5_menu_verify.sql` 断言 `5100` 的 `path` 仍为相对值
  `dashboard`、`5142/5143/5150/5151/5152` 仍挂在根下。PC 迁移会改写这些形态，
  因此 PC 之后请改用 `pc/PC_20260923_001__sidebar_ia_verify.sql`，那时 A1 校验
  报 `FAIL` 是预期行为。
- `a4/A4_20260922_001__a4_verify.sql` 的 `app_user_domain_count` 是**信息项**：
  它只报告 App 域（`user_type` 为 `01/02/03`）用户数。App 用户由 App 注册流程产生，
  迁移不创建用户，所以刚初始化的库该计数为 0 属于合法状态，不参与判定。

## 8. 目录索引

| 目录 | 内容 |
| --- | --- |
| `a2/` | A2 迁移、校验、回滚，含 README |
| `a1/` | A1 产品菜单种子、前置快照、校验、回滚，含 README |
| `a4/` | A4 结构增量（001）、菜单权限（002）、角色可授权标记（003）及各自校验与回滚，含 README |
| `pc/` | PC 侧边栏信息架构迁移、最终校验、回滚，含 README |

初始化流程实际执行的顺序清单在 `scripts/db/init-steps.txt`；该文件是顺序的唯一来源，
本节的表格与之保持一致，改动任一处都要同步另一处。
