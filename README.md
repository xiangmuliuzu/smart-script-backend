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
| 主分支 | `main@f50359b` |
| A3 分支 | `a3/app-auth-migration` |
| 基线 | RuoYi-Vue `v3.9.2` |
| Commit SHA | `0e2d75c23c0d7a1fa85f660f06a59a4dd1ba14c0` |
| Spring Boot | `4.1.0` |
| JDK | 17 |
| 官方源 | `https://gitee.com/y_project/RuoYi-Vue` |

当前 `main` 已完成 A0-R1、A1/G1、A2/G2 与 A4/A5/A6 的已合入部分。A3 只在功能分支实现 App 认证，完成 G3 前不得合入 `release`。

**仓库边界（强制）**：本仓 **不包含** 若依上游附带的 `ruoyi-ui` 或任何 PC/App 构建工程。权威 PC 管理端仅位于 `smart-script-web`（RuoYi-Vue3）。

`legacy-placeholders/` 保留导入前仓库内的空包占位，便于对照，不属于若依官方结构。

---

## 环境准备

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17 | 编译与运行 |
| Maven | 3.8+ | 构建 |
| MySQL | **8.x**（验证于 8.0.36） | 初始化脚本会校验大版本，非 8.x 直接拒绝 |
| Redis | 5+ | 登录态、验证码、App 凭证域与限流 |
| mysql 客户端 | 8.x | 初始化流程调用；不在 PATH 时用 `--mysql` / `-MysqlBin` 指定绝对路径 |

## 环境变量

所有敏感值只从环境变量或初始化脚本参数读入，**禁止写入仓库**。模板见 `.env.example`，
复制为本机私有的 `.env.local`（已被 `.gitignore` 忽略）。

### 数据库与 Redis

| 变量 | 必需 | 说明 |
| --- | --- | --- |
| `DB_URL` | 是 | 例：`jdbc:mysql://localhost:3306/smartscript_dev?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8` |
| `DB_USERNAME` | 是 | 数据库账号 |
| `DB_PASSWORD` | 是 | 数据库口令，不入库、不落日志 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_DATABASE` | 否 | 默认 `localhost` / `6379` / `0` |
| `REDIS_PASSWORD` | 否 | 有口令时必须设置 |

初始化脚本另用一组变量，与运行期解耦（脚本也支持同名命令行参数）：

| 变量 | 说明 |
| --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | 初始化目标：主机、端口、**库名**、账号、口令 |
| `MYSQL_BIN` | mysql 客户端路径，缺省取 PATH 中的 `mysql` |

### 启动必需（缺失即拒绝启动）

| 变量 | 约束 |
| --- | --- |
| `TOKEN_SECRET` | PC 管理员 Token 密钥，≥32 字符，不得为已知弱口令 |
| `APP_TOKEN_SECRET` | App 凭证域密钥，≥32 字符，且**必须与 `TOKEN_SECRET` 不同** |
| `APP_TOKEN_ISSUER` / `APP_TOKEN_AUDIENCE` | App JWT 签发者与受众，不得为空 |
| `APP_REDIS_KEY_PREFIX` | App 凭证域 Redis 前缀，例：`smartscript:dev:app-auth:` |
| `APP_ADMIN_MATERIAL_STORAGE_PATH` | A4 材料文件根目录，不得位于 `ruoyi.profile` 之下 |
| `APP_AUTH_ENV` | `local` / `test` 才允许 `SMS_PROVIDER=mock` |

可选：`SERVER_PORT`（默认 8080）、`TOKEN_EXPIRE_MINUTES`（默认 30）、
`DRUID_LOGIN_USERNAME` / `DRUID_LOGIN_PASSWORD`、`APP_ACCESS_TOKEN_TTL` /
`APP_REFRESH_TOKEN_TTL`、`APP_AGREEMENT_*` 等，详见 `.env.example` 与 `application.yml`。

---

## 数据库初始化

### 规则（强制）

- 初始化**只允许**针对「不存在」或「已存在但一张表都没有」的库。
- 检测到库中已有**任何一张表**即立刻拒绝退出（退出码 `2`），
  **永不执行 `DROP DATABASE`，永不覆盖既有数据**。
- 已有业务数据的库**禁止**导入 `sql/ry_20260320.sql`（该文件会 `drop table` 重建）。
  已有库请走升级路径：见 [`sql/migrations/README.md`](sql/migrations/README.md)。

### 执行

```bash
# Linux / macOS / WSL / Git Bash
export DB_PASSWORD='<本机私有口令>'
./scripts/db/init-database.sh -h 127.0.0.1 -P 3306 -d smartscript_dev -u root
```

```powershell
# Windows PowerShell 5.1+
$env:DB_PASSWORD = '<本机私有口令>'
.\scripts\db\init-database.ps1 -d smartscript_dev -u root
# mysql 客户端不在 PATH 时追加： -MysqlBin "D:\java\bin\mysql.exe"
```

口令优先用环境变量；两个脚本都不会把口令写进命令行日志，也不会落盘到仓库内
（仅写入退出时删除的 0600 临时配置文件）。先加 `--dry-run` / `-DryRun` 可只做连接
检查与空库判定。

### 脚本做了什么

按 [`scripts/db/init-steps.txt`](scripts/db/init-steps.txt) 顺序执行 15 步，**顺序只有这一处定义**，
两个脚本共用：

| 顺序 | 阶段 | 校验闸门 |
| ---: | --- | --- |
| 1 | 若依基础结构 + 初始数据（`sql/ry_20260320.sql`） | 退出码 |
| 2–4 | A2：前置检查 → 迁移 → 校验 | precheck / verify 要求 `SUMMARY=PASS` |
| 5–6 | A1：产品菜单与受限运营角色种子 → 校验 | verify 要求 `SUMMARY=PASS` |
| 7–13 | A4：001 结构增量、002 菜单与权限、003 角色可授权标记，各自 前置检查/迁移/校验 | 各 verify 要求 `SUMMARY=PASS` |
| 14–15 | PC：侧边栏信息架构迁移 → 最终校验 | 最终校验要求 `SUMMARY=PASS` |

每一步都检查 mysql 退出码；带 `summary` 闸门的步骤还要求输出出现
`SUMMARY=PASS`（`fail_cnt=0`），且不得出现 `FAIL` 汇总行或 `*_ABORTED` 中止标记。
**任一步失败立即停止**，并打印该步输出尾部与完整日志路径（失败时保留临时日志目录，
查看后自行删除）。

顺序依赖：A1 必须早于 A4（A4 前置检查要求顶级 `path='user'` 目录唯一，由 A1 的
`menu_id=5142` 提供）；PC 必须晚于 A1 与 A4（它重挂 A1 产品菜单并重排/改名 A4 菜单）。
详见 `sql/migrations/README.md` 第 3 节。

### 初始化后的期望状态

全新空库跑完后：**表 36、用户 2、角色 3、菜单 133、角色菜单 105**。
其中包含若依原生 `admin`（超级管理员）与 `ry` 两个账号、`a1_operator` 受限运营角色，
以及 `menu_id` 段 `3000-3015` 的 A4 用户中心菜单。

---

## 本地构建与启动

```powershell
cd D:\build\smart-script-backend

# 构建（跳过测试加 -DskipTests）
mvn.cmd clean verify

# 启动（先按上面设置好 DB_URL/DB_USERNAME/DB_PASSWORD、REDIS_* 与启动必需变量）
java -jar ruoyi-admin/target/ruoyi-admin.jar
# 默认端口 8080，健康检查：curl http://127.0.0.1:8080/captchaImage
```

数据库迁移只使用 `sql/migrations` 中已评审的脚本；应用启动不自动改表。

## 目录导航

| 路径 | 内容 |
| --- | --- |
| `sql/ry_20260320.sql` | 若依基础结构与初始数据（仅空库初始化使用） |
| `sql/migrations/` | 初始化与已有库升级用到的全部 SQL，含各阶段 README 与回滚脚本 |
| `scripts/db/` | 初始化脚本（`.sh` / `.ps1`）与全量步骤清单 `init-steps.txt` |
| `scripts/a3/` | A3 联调脚手架（后端启动器、环境变量模板、测试矩阵、A3 专用步骤子集 `init-steps-a3.txt`） |
| `docs/` | 安全与设计说明 |

**建库只有这一条路径**：所有建库动作都走 `scripts/db/init-database.*`，仓库内不存在
第二个初始化命令，也没有任何会 `DROP DATABASE` 的建库脚本。A3 联调需要只有基线 + A2 的
隔离库时，用同一工具加载 A3 子集清单即可：

```bash
export DB_PASSWORD='<口令>'
./scripts/db/init-database.sh -d ruoyi_dev_a3_test -u root --steps scripts/a3/init-steps-a3.txt
```

（新工具拒绝在非空库上执行，因此反复联调前需先自行 `DROP DATABASE` 该测试库；
破坏性动作不藏在脚本里，避免误删非测试库。详见 `scripts/a3/init-steps-a3.txt` 头部说明。）

## 仓库地址

`https://github.com/xiangmuliuzu/smart-script-backend`
