# A2 数据库兼容迁移脚本

| 项目 | 内容 |
| --- | --- |
| 联调编号 | `A-A2-DB-MIGRATION-001` |
| 迁移版本 | `A2_20260921_001` |
| 适用数据库 | MySQL 8.0.36 |
| 权威规格 | `D:\build\shared\A用户与认证开发规格.md` v1.5 |
| 验收记录 | `D:\build\shared\A2-G2-评审验收记录.md` |

## 执行顺序

1. `A2_20260921_001__a2_precheck.sql` — 只读检查，`fail_cnt` 必须为 0
2. `A2_20260921_001__a2_migrate.sql` — 结构加固与兼容映射
3. `A2_20260921_001__a2_verify.sql` — 迁移后校验
4. 并发唯一约束与若依读写验证（见 shared 证据）
5. `U20260921_001__a2_rollback.sql` — 完整回滚演练

## 重要约束

- **禁止**对现有库执行 `sql/ry_20260320.sql` 全量覆盖。
- **禁止**重编号 `sys_user.user_id`。
- 首次完整执行只允许在隔离测试库（如 `ruoyi_dev_a2_test`）。
- 应用启动不得自动改表。
- 本目录是 A2 迁移唯一来源；不得并存 `final.sql` / `new.sql` / 手工修库脚本。

## 密码与凭据

运行时通过环境变量提供数据库口令，例如：

```powershell
$env:DB_PASSWORD = '<本机私有>'
& D:\java\bin\mysql.exe -h 127.0.0.1 -u root "-p$env:DB_PASSWORD" -D ruoyi_dev_a2_test
```

不得把真实口令写入仓库、日志或验收报告。
