# sql/_archive — 已归档的历史 SQL（勿用于初始化）

本目录**不参与**任何初始化、升级或测试流程，仅作历史留档与 git 追溯用途。

## 单入口约定

- 平台建库/升级的**唯一入口**：`sql/smartscript_full_init.sql`（自幂等，空库完整初始化、已有库增量补齐）。
- 由 `scripts/db/init-steps.txt` 驱动，`scripts/db/init-database.sh` / `.ps1` 两个脚本都只执行这一个文件。
- 测试数据种子仍在 `sql/` 根目录，由 `scripts/db/seed-steps.txt` 单独执行：
  `seed_content_tag_testdata.sql`、`seed_content_chapter_testdata.sql`、`seed_content_banner_testdata.sql`。
- 后续字段/菜单/权限/字典变更请直接修改 `sql/smartscript_full_init.sql`，
  **不要**再向仓库根或 `sql/` 添加一次性补丁脚本。

## 目录

| 路径 | 内容 | 归档原因 |
| --- | --- | --- |
| `migrations/` | 原 `sql/migrations/`（`a/ b/ c/ e/` 分批次迁移脚本及回滚脚本） | 已全部蒸馏合并进 `smartscript_full_init.sql` 第 1–14 部分 |
| `patches/` | 根目录历史一次性补丁：`add_*.sql`、`create_*.sql`、`sandbox_payment_support.sql`、`copyright_settlement_finance.sql`、`app_2_8_drama_seed.sql`、`cleanup_content_testdata.sql`、`fix_material_urls_column_clean.sql` | 内容已合并进全量脚本，或对应表/功能已退役 |
| `upstream/` | `ry_20260320.sql`（若依上游基线原文）、`quartz.sql`（Quartz 官方建表） | 仅作上游参照；平台已使用适配后的基线结构 |

## 注意

- `patches/sandbox_payment_support.sql` 含 19 张已在阶段 A 删除的退役表 DDL，
  **禁止**再在正式库执行；仅沙箱演示库历史说明。
- `migrations/` 中的脚本可能引用已被本仓库删除的表/列，执行会失败，仅作记录。
