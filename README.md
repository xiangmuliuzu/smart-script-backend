# smart-script-backend（整体后端）

智能剧本创作平台 **整体后端** 仓库。权威开发规格与评审标准位于共享区，本 README 只链接，不复制语义不同的版本。

## 权威文档

| 文档 | 路径 | 版本/状态 |
| --- | --- | --- |
| A 用户与认证开发规格 | `D:\build\shared\A用户与认证开发规格.md` | v1.5，A3 当前规格 |
| A 用户与认证评审验收标准 | `D:\build\shared\A用户与认证评审验收标准.md` | v1.4 |
| A3 实施方案 | `D:\build\shared\A3-App认证迁移实施方案.md` | 允许开发，待 G3 |
| A3 API 契约 | `D:\build\shared\A3-认证接口契约.md` | `A3-AUTH-CONTRACT-v1` |
| A3 测试矩阵 | `D:\build\shared\A3-G3-测试矩阵.md` | AUTH/DB/APP 矩阵 |
| A3 验收记录 | `D:\build\shared\A3-G3-评审验收记录.md` | 不通过，待开发与评审 |

## 仓库边界

- 包含：完整若依多模块工程、A–E 全部后端业务模块、数据库迁移、后端测试、部署配置。
- 不包含：Flutter App 源码、Vue 管理端源码。
- 不按负责人拆成多个后端仓库。
- 业务包规划：`com.smartscript.platform`（`smartscript-user` 等），若依公共模块保持官方边界。

## 当前基线

| 字段 | 值 |
| --- | --- |
| 主分支 | `main@95c26ca757ecd47c9fdffd9aabedbff4f97c7a2c` |
| A3 分支 | `a3/app-auth-migration` |
| 基线 | RuoYi-Vue `v3.9.2` |
| Commit SHA | `0e2d75c23c0d7a1fa85f660f06a59a4dd1ba14c0` |
| Spring Boot | `4.1.0` |
| JDK | 17 |
| 官方源 | `https://gitee.com/y_project/RuoYi-Vue` |

当前 `main` 已完成 A0-R1、A1/G1 与 A2/G2。A3 只在功能分支实现 App 认证，完成 G3 前不得合入 `release`。

**仓库边界（强制）**：本仓 **不包含** 若依上游附带的 `ruoyi-ui` 或任何 PC/App 构建工程。权威 PC 管理端仅位于 `smart-script-web`（RuoYi-Vue3）。

`legacy-placeholders/` 保留导入前仓库内的空包占位，便于对照，不属于若依官方结构。

## 本地构建

```powershell
# 依赖：JDK 17、Maven 3.8+、MySQL 8、Redis
# 数据库：请使用干净库，勿对 script_platform_dev 直接执行官方全量 SQL
# 配置变量：DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD 等，口令不入库

cd D:\build\smart-script-backend
mvn.cmd clean verify
# 启动：java -jar ruoyi-admin/target/ruoyi-admin.jar
# 默认端口 8080；数据库迁移只使用 sql/migrations 中已评审脚本
```

## 仓库地址

`https://github.com/xiangmuliuzu/smart-script-backend`
