# A0-R1 安全说明

| 项 | 要求 |
| --- | --- |
| TOKEN_SECRET | 必须环境变量注入；缺失/过短/弱默认值 → **拒绝启动** |
| DB_PASSWORD / REDIS_PASSWORD / DRUID_* | 环境变量；仓库仅占位 |
| 验收脚本 | `scripts/a0-r1-verify-api.ps1` 仅使用 `VERIFY_*` 环境变量；证据 Token/密码脱敏 |
| 旧证据 | 不得继续引用含默认密钥启动或含原始 Token 的历史证据 |
| 密钥轮换 | 本机生成的新 `TOKEN_SECRET` **不得**写入 Git 或共享 evidence |

启动与验收示例（口令仅环境变量）：

```powershell
$env:TOKEN_SECRET='<rotated-strong-secret-at-least-32-chars>'
$env:DB_USERNAME='<db-user>'
$env:DB_PASSWORD='<db-password>'
$env:VERIFY_ADMIN_PASSWORD='<admin-password>'
$env:VERIFY_LIMITED_PASSWORD='<limited-password>'
java -jar ruoyi-admin/target/ruoyi-admin.jar
powershell -File scripts/a0-r1-verify-api.ps1
```
