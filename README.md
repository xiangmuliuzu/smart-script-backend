# smart-script-backend（整体后端）

智能剧本创作平台 **整体后端** 仓库。权威开发规格与评审标准位于共享区，本 README 只链接，不复制语义不同的版本。

## 权威文档

| 文档 | 路径 | 版本/状态 |
| --- | --- | --- |
| A 用户与认证开发规格 | `D:\build\shared\A用户与认证开发规格.md` | v1.1 |
| A 用户与认证评审验收标准 | `D:\build\shared\A用户与认证评审验收标准.md` | 配套标准 |
| A 基线锁定记录 | `D:\build\shared\A基线锁定记录.md` | A0，待评审 |
| 三仓迁移映射 | `D:\build\shared\A三仓迁移映射.md` | A0 规定名 |
| A0 执行与测试报告 | `D:\build\shared\A0执行与测试报告.md` | A0 规定名 |

## 仓库边界

- 包含：完整若依多模块工程、A–E 全部后端业务模块、数据库迁移、后端测试、部署配置。
- 不包含：Flutter App 源码、Vue 管理端源码。
- 不按负责人拆成多个后端仓库。
- 业务包规划：`com.smartscript.platform`（`smartscript-user` 等），若依公共模块保持官方边界。

## A0 基线

| 字段 | 值 |
| --- | --- |
| 分支 | `a0/baseline-lock` |
| 基线 | RuoYi-Vue `v3.9.2` |
| Commit SHA | `0e2d75c23c0d7a1fa85f660f06a59a4dd1ba14c0` |
| Spring Boot | `4.0.3` |
| JDK | 17 |
| 官方源 | `https://gitee.com/y_project/RuoYi-Vue` |

本分支内容为 **官方空工程基线**，尚未合入 App 认证业务迁移。旧 monorepo 中的业务代码仅作迁移来源（见 `D:\app` 与 shared 备份），不在本基线中。

**仓库边界（强制）**：本仓 **不包含** 若依上游附带的 `ruoyi-ui` 或任何 PC/App 构建工程。权威 PC 管理端仅位于 `smart-script-web`（RuoYi-Vue3）。

`legacy-placeholders/` 保留导入前仓库内的空包占位，便于对照，不属于若依官方结构。

## 本地启动（空基线）

```powershell
# 依赖：JDK 17、Maven 3.8+、MySQL 8、Redis
# 数据库：请使用干净库，勿对 script_platform_dev 直接执行官方全量 SQL
# 配置变量：DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD 等，口令不入库

cd D:\build\smart-script-backend
mvn.cmd -DskipTests clean package
# 启动：java -jar ruoyi-admin/target/ruoyi-admin.jar
# 默认端口 8080，验证码与若依默认账号以官方 sql 初始化为准
```

详细构建/启动证据：`D:\build\shared\A0空工程启动与构建记录.md`。

## 仓库地址

`https://github.com/xiangmuliuzu/smart-script-backend`
