# smart-script-backend（整体后端）

智能剧本创作平台 **整体后端** 仓库。旧 A 模块共享文档已清理；共享 SQL、验证脚本与回归记录见 [`../shared/README.md`](../shared/README.md)。

## 仓库边界

- 包含：完整若依多模块工程、A–E 全部后端业务模块、数据库迁移、后端测试、部署配置。
- 不包含：Flutter App 源码、Vue 管理端源码。
- 不按负责人拆成多个后端仓库。
- 业务包规划：`com.smartscript.platform`（`smartscript-user` 等），若依公共模块保持官方边界。

## 当前基线

| 字段 | 值 |
| --- | --- |
| 主分支（2026-09-23） | `main@ae3ebc0` |
| 阶段状态 | A3/G3、A4/G4 已关闭；A5 `7c765ae`、A6 `d15d541`、A7 验证基线均已合入 `main`；`main` 另含 C 模块提交（A7 未验证，见 A7 记录 A7-Q1/A7-Q5） |
| 基线 | RuoYi-Vue `v3.9.2` |
| Commit SHA | `0e2d75c23c0d7a1fa85f660f06a59a4dd1ba14c0` |
| Spring Boot | `4.1.0` |
| JDK | 17 |
| 官方源 | `https://gitee.com/y_project/RuoYi-Vue` |

当前 `main` 已完成 A0-R1、A1/G1、A2/G2、A3/G3、A4/G4，并包含 A5/A6 快速阶段交付；A7 已在上述基线上完成基础构建、空库初始化、启动与主流程冒烟。以上为历史阶段状态，快速阶段通过不代表加固或发布门禁已完成。

**仓库边界（强制）**：本仓 **不包含** 若依上游附带的 `ruoyi-ui` 或任何 PC/App 构建工程。权威 PC 管理端仅位于 `smart-script-web`（RuoYi-Vue3）。


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
| `APP_SMS_MOCK_CODE` | mock 短信固定验证码。本地联调时可设为 `123456`，App 端即用该码通过短信验证；也可临时修改 `application.yml` 中 `app.sms.mock-fixed-code` 的默认值（勿提交） |

可选：`SERVER_PORT`（默认 8080）、`TOKEN_EXPIRE_MINUTES`（默认 30）、
`DRUID_LOGIN_USERNAME` / `DRUID_LOGIN_PASSWORD`、`APP_ACCESS_TOKEN_TTL` /
`APP_REFRESH_TOKEN_TTL`、`APP_AGREEMENT_*` 等，详见 `.env.example` 与 `application.yml`。

---

## 数据库初始化

### 规则（强制）

- 初始化脚本**自幂等**：空库做完整初始化；已有库自动进入**增量升级模式**，
  只补齐缺失的表/列/索引/菜单/字典，**不删除、不覆盖任何既有数据**，可重复执行。
- 即便如此，对有业务数据的库执行前仍建议先备份：
  `mysqldump --single-transaction --routines --triggers <库名> > backup.sql`。

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

按 [`scripts/db/init-steps.txt`](scripts/db/init-steps.txt) 执行唯一一步，**顺序只有这一处定义**，
两个脚本共用：[`sql/smartscript_full_init.sql`](sql/smartscript_full_init.sql)。

该文件主结构为第 0–10 部分，全部幂等（空库完整导入；已有库跳过已有结构、只补缺失部分）：

| 部分 | 内容 |
| ---: | --- |
| 0 | 若依基线（仅当 `sys_dept` 不存在即空库时导入，已有库整体跳过） |
| 1–2 | `sys_user` 加固与数据归一、App/用户域 11 张表（原 A2/A4 迁移） |
| 3 | `sys_user.bio`（A5）、`(user_type, del_flag)` 覆盖索引（H12）、`sys_role.app_grantable`（A4） |
| 4–5 | 产品/用户中心/内容/交易菜单与角色授权（A1+PC+B1+C 最终形态，upsert） |
| 6–8 | C 交易域 15 张业务表、交易字典 14 类型/52 项、询盘过期定时任务 |
| 9 | 公告接收范围 `USER/ADMIN/ALL`、可见性索引、消息与公告按钮权限；历史公告默认为 `ADMIN`，不自动授予受限角色管理权限 |
| 10 | 用户消息迁入平台运维管理的“消息与公告”页，迁移原角色的列表/发送/详情授权并补齐父目录，删除用户中心旧入口；保留消息和阅读记录 |

消息与公告菜单及旧入口迁移由第 4、9、10 部分维护；所有自检在第 10 部分完成后输出。
服务器更新 PC 前端后，运行上述同一初始化脚本即可完成菜单升级；管理员重新登录以刷新菜单与权限。

**任一步失败立即停止**，并打印该步输出尾部与完整日志路径（失败时保留临时日志目录，
查看后自行删除）。也可以绕过脚本直接执行：
`mysql -h<host> -u<user> -p <库名> < sql/smartscript_full_init.sql`。

历史演进：原 `sql/migrations/` 下 15 步迁移流程（含 precheck/verify/回滚脚本）已全部
蒸馏合并进该文件，历史版本可通过 git 记录回看。后续新增字段/菜单直接修改该文件，
不要再往仓库里堆一次性迁移脚本。

### 初始化后的期望状态

全新空库跑完后：**表 54、用户 2、角色 3、菜单 194、角色菜单 125**（2026-10-10 MySQL 8.0.36 实测；用户消息旧菜单 3010–3012 已迁入消息与公告，admin 授权改用新入口和按钮）。
其中包含若依原生 `admin`（超级管理员）与 `ry` 两个账号、`a1_operator` 受限运营角色，
`menu_id` 段 `3000-3015` 的 A4 用户中心菜单、`5000-5196` 的产品/内容/交易菜单，
以及 C 模块交易域 15 张业务表与交易字典。

> **默认测试账号**：`admin` / `admin123`（`ry` 同为 `admin123`）。
> 哈希来自 `sql/ry_20260320.sql` 内置种子数据，任何人按上述流程初始化即可直接登录，
> 无需手工插入账号。仅限本地/测试环境使用，生产环境部署后请立即修改。

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

数据库结构变更统一维护在 `sql/smartscript_full_init.sql`（幂等）；应用启动不自动改表。

## 目录导航

| 路径 | 内容 |
| --- | --- |
| `sql/smartscript_full_init.sql` | 平台数据库唯一初始化/增量升级入口（基线 + 全部增量，自幂等） |
| `sql/ry_20260320.sql` | 若依基线原文（已内嵌进全量脚本，仅供上游参照） |
| `scripts/db/` | 初始化脚本（`.sh` / `.ps1`）与全量步骤清单 `init-steps.txt` |

**建库只有这一条路径**：所有建库动作都走 `scripts/db/init-database.*`，仓库内不存在
第二个初始化命令，也没有任何会 `DROP DATABASE` 的建库脚本。

## 仓库地址

`https://github.com/xiangmuliuzu/smart-script-backend`

## 数据库密码配置

数据库密码必须通过 `DB_PASSWORD` 环境变量注入，不再使用仓库内的默认值。本机开发仍可使用 `.env.local`；云端部署时也必须设置 `DB_PASSWORD`。
