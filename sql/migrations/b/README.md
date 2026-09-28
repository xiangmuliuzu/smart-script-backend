# B 模块（内容与作品）分类/标签 PC 菜单 — 数据库迁移说明

## 1. 阶段信息

| 项目 | 内容 |
| --- | --- |
| 迁移版本 | `B_20260928_001` |
| 适用数据库 | MySQL 8.0.36 |
| 迁移对象 | 仅 `sys_menu` / `sys_role_menu`（菜单与超级管理员授权），不涉及任何业务表 |

## 2. 文件清单与执行顺序

| 顺序 | 文件 | 作用 | 可重复 |
| ---: | --- | --- | --- |
| 1 | `B_20260928_001__content_category_tag_menu.sql` | 分类/标签菜单与权限落库（写） | 是 |
| 2 | `B_20260928_001__content_category_tag_menu_verify.sql` | 最终校验；末尾必须 `PASS fail_cnt=0,` | 是 |
| R | `U20260928_001__content_category_tag_menu_rollback.sql` | 回滚到 A1 占位形态 | 是 |

## 3. 前置依赖

- `sql/migrations/a1/A1_20260921_001__p5_menu_seed.sql` 已应用：提供 `5001`
  「内容与作品」目录与 `5102` 占位菜单「分类与标签」（`common/ModuleScaffold` 骨架）。
- 建议在 `pc/` 侧边栏信息架构迁移之后执行（本迁移不依赖 PC 迁移结果，
  但初始化流程中 PC 是其紧邻的前序阶段）。

## 4. 做了什么

| 步骤 | 内容 |
| ---: | --- |
| 1 | `5102` 占位骨架改写为真实页面「分类管理」：`path=category`、`component=content/category/index`、`perms=content:category:list` |
| 2 | 新增 `5105`「标签管理」C 页：`path=tag`、`component=content/tag/index`、`perms=content:tag:list` |
| 3 | 新增 7 个 F 按钮（`5160-5166`）：分类 query/add/edit，标签 query/add/edit/remove；权限码与后端 `@PreAuthorize` 一一对应（启用/停用/排序复用 `content:category:edit`） |
| 4 | 「内容与作品」子菜单顺序：作品内容管理(1) → 分类管理(2) → 标签管理(3) → 排行榜管理(4) → 外部漫剧发行(5) |
| 5 | 9 个菜单幂等授予超级管理员（`role_key='admin'` 或 `role_id=1`），不扩散到普通角色 |

前端路由由 `/getRouters` 动态下发，两个组件已在
`smart-script-web/src/router/component-map.js` 白名单显式登记。

## 5. 最终校验

校验脚本共 9 项判定，末尾汇总行必须为 `PASS` 且 `fail_cnt=0`，覆盖：

- 两个 C 页的名称/路径/组件/权限码/挂接目录正确
- 7 个 F 按钮父子挂接与权限码正确
- 9 个权限码未被其他菜单占用；`5102` 占位骨架痕迹清零
- 同级排序无重复；两个组件标识各登记一次；超管授权 9 条齐全
- 内容与作品目录 5 个子菜单顺序正确

## 6. 回滚

`U20260928_001__content_category_tag_menu_rollback.sql` 删除新增的 8 个菜单及其授权，
并把 `5102` 恢复为 A1 占位形态、恢复 `5103/5104` 的原顺序。回滚不触碰
`sys_category` / `sys_tag` 业务表与后端代码。回滚顺序见
`sql/migrations/README.md` 第 6 节（B 最后安装、最先回滚）。
