<#
=====================================================================
 smart-script 平台数据库初始化（Windows / PowerShell 5.1+）

   pwsh -File scripts\db\init-database.ps1 -Database smartscript_dev

 规则：
   1. 只允许针对「不存在」或「已存在但一张表都没有」的库。
      库中已有任何表即立刻拒绝退出，永不 DROP DATABASE，永不覆盖数据。
   2. 已有数据的库请走升级路径：见 sql\migrations\README.md。
   3. 口令只从环境变量或参数读入，写入仅当前用户可读的临时配置文件后传给
      mysql，不进入命令行参数、不进入任何日志，退出时删除。

 与 init-database.sh 共用同一份步骤清单（scripts\db\init-steps.txt），
 执行顺序只有那一处定义。
=====================================================================
#>
[CmdletBinding()]
param(
    [Alias('d')]
    [string]$Database = $env:DB_NAME,

    [Alias('u')]
    [string]$User = $env:DB_USERNAME,

    [string]$Password = $env:DB_PASSWORD,

    [Alias('h')]
    [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' }),

    [Alias('P')]
    [string]$Port = $(if ($env:DB_PORT) { $env:DB_PORT } else { '3306' }),

    [string]$MysqlBin = $(if ($env:MYSQL_BIN) { $env:MYSQL_BIN } else { 'mysql' }),

    [string]$Steps,

    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

function Write-Usage {
    @'
用法: init-database.ps1 -Database <库名> [选项]

必填（参数或环境变量二选一）:
  -d, -Database <name>  目标库名      环境变量 DB_NAME
  -u, -User <name>      数据库账号    环境变量 DB_USERNAME
      -Password <s>     数据库口令    环境变量 DB_PASSWORD（推荐用环境变量）

可选:
  -h, -DbHost <host>   默认 127.0.0.1 环境变量 DB_HOST
  -P, -Port <port>      默认 3306      环境变量 DB_PORT
      -MysqlBin <path>  mysql 客户端   环境变量 MYSQL_BIN，默认取 PATH 中的 mysql
      -Steps <file>     步骤清单，默认 scripts\db\init-steps.txt
      -DryRun           只做连接检查与空库判定，不建库、不执行步骤

示例:
  $env:DB_PASSWORD = '<本机私有口令>'
  .\scripts\db\init-database.ps1 -d smartscript_dev -u root

初始化完成后，按 README 设置运行期环境变量（DB_URL / REDIS_* /
TOKEN_SECRET / APP_* 等）再启动后端。
'@
}

function Fail([string]$Message, [int]$Code = 1) {
    Write-Host "init-database.ps1: $Message" -ForegroundColor Red
    exit $Code
}

if (-not $Database) { Write-Usage; Fail '缺少 -Database / DB_NAME' 64 }
if (-not $User) { Write-Usage; Fail '缺少 -User / DB_USERNAME' 64 }
if (-not $Password) {
    Write-Usage
    Fail '缺少 -Password / DB_PASSWORD（口令不落盘、不落历史，建议设置 DB_PASSWORD 环境变量）' 64
}
if ($Database -notmatch '^[A-Za-z0-9_$]+$') {
    Fail "库名只允许字母、数字、下划线和 `$, 实际为: $Database"
}
if ($Port -notmatch '^\d+$') { Fail "端口必须是数字，实际为: $Port" }
if ($DbHost -notmatch '^[A-Za-z0-9.:_-]+$') { Fail "主机名含可疑字符: $DbHost" }
if ($User -notmatch '^[A-Za-z0-9_.@-]+$') { Fail "用户名含可疑字符: $User" }

$scriptDir = $PSScriptRoot
$repoRoot = (Resolve-Path (Join-Path $scriptDir '..\..')).Path
if (-not $Steps) { $Steps = Join-Path $scriptDir 'init-steps.txt' }
if (-not (Test-Path -LiteralPath $Steps)) { Fail "找不到步骤清单: $Steps" }

$resolvedMysql = $MysqlBin
if (-not (Test-Path -LiteralPath $resolvedMysql)) {
    $cmd = Get-Command $MysqlBin -ErrorAction SilentlyContinue
    if ($cmd) { $resolvedMysql = $cmd.Source }
    else { Fail "找不到 mysql 客户端: $MysqlBin（用 -MysqlBin 指定绝对路径）" }
}

# ------------------------------------------------------------------ 临时区
$workDir = Join-Path ([System.IO.Path]::GetTempPath()) ("smartscript-db-init." + [System.Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $workDir -Force | Out-Null
$logDir = Join-Path $workDir 'logs'
New-Item -ItemType Directory -Path $logDir -Force | Out-Null
$keepWork = $false

# 口令经临时配置文件传入：不进入 argv（进程列表可见）、不进入任何日志
$cnfPath = Join-Path $workDir 'client.cnf'
$esc = {
    param($v)
    ($v -replace '\\', '\\\\') -replace '"', '\"'
}
$cnfText = @"
[client]
host="$(& $esc $DbHost)"
port="$(& $esc $Port)"
user="$(& $esc $User)"
password="$(& $esc $Password)"
default-character-set=utf8mb4
"@
[System.IO.File]::WriteAllText($cnfPath, $cnfText, (New-Object System.Text.UTF8Encoding($false)))
try {
    $acl = Get-Acl -LiteralPath $cnfPath
    $acl.SetAccessRuleProtection($true, $false)
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule(
        [System.Security.Principal.WindowsIdentity]::GetCurrent().Name, 'FullControl', 'Allow')
    $acl.SetAccessRule($rule)
    Set-Acl -LiteralPath $cnfPath -AclObject $acl
} catch {
    # 文件系统不支持 ACL 时不影响功能（临时目录本身已是用户私有）
}

function Invoke-Mysql {
    <#
      运行 mysql 客户端。返回 @{ ExitCode; StdOut; StdErr }
      StdInFile 指定时把该文件作为标准输入（SQL 脚本）。
    #>
    param(
        [string[]]$MysqlArgs,
        [string]$StdInFile
    )
    $stamp = [System.Guid]::NewGuid().ToString('N').Substring(0, 8)
    $outFile = Join-Path $workDir "out-$stamp.txt"
    $errFile = Join-Path $workDir "err-$stamp.txt"

    $all = @("--defaults-extra-file=$cnfPath") + $MysqlArgs
    # 参数含空格时需要显式加引号：Start-Process 直接拼接到命令行
    $quoted = @()
    foreach ($a in $all) {
        if ($a -match '\s') { $quoted += ('"' + $a + '"') } else { $quoted += $a }
    }

    $sp = @{
        FilePath               = $resolvedMysql
        ArgumentList           = $quoted
        NoNewWindow            = $true
        Wait                   = $true
        PassThru               = $true
        RedirectStandardOutput = $outFile
        RedirectStandardError  = $errFile
    }
    if ($StdInFile) { $sp.RedirectStandardInput = $StdInFile }

    $p = Start-Process @sp
    $stdout = if (Test-Path -LiteralPath $outFile) {
        [System.IO.File]::ReadAllText($outFile, [System.Text.Encoding]::UTF8)
    } else { '' }
    $stderr = if (Test-Path -LiteralPath $errFile) {
        [System.IO.File]::ReadAllText($errFile, [System.Text.Encoding]::UTF8)
    } else { '' }
    return [pscustomobject]@{ ExitCode = $p.ExitCode; StdOut = $stdout; StdErr = $stderr }
}

function Invoke-Scalar {
    param([string]$Query, [string]$Db)
    # 查询可能含换行；命令行参数里折叠成单行空格
    $flat = ($Query -replace '\s+', ' ').Trim()
    $a = @('--batch', '--skip-column-names')
    if ($Db) { $a += $Db }
    $a += @('-e', $flat)
    $r = Invoke-Mysql -MysqlArgs $a
    if ($r.ExitCode -ne 0) {
        Fail "查询失败: $($r.StdErr.Trim())"
    }
    return $r.StdOut.Trim()
}

$target = "$User@$DbHost`:$Port/$Database"
Write-Host '== smart-script 数据库初始化 =='
Write-Host "目标      : $target"
Write-Host "客户端    : $resolvedMysql"
Write-Host "步骤清单  : $Steps"

try {
    # -------------------------------------------------------- 连接与版本
    $ver = Invoke-Scalar -Query 'SELECT VERSION();'
    if ($ver -notmatch '^8\.') { Fail "本流程要求 MySQL 8.x，检测到 $ver" }
    Write-Host "MySQL     : $ver"

    # -------------------------------------------------------- 空库守卫
    $dbExists = Invoke-Scalar -Query "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '$Database';"
    $tableCnt = '0'
    if ($dbExists -eq '0') {
        Write-Host '库状态    : 不存在（将新建）'
    } else {
        $tableCnt = Invoke-Scalar -Query "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '$Database';"
        if ($tableCnt -ne '0') {
            Write-Host ''
            Write-Host "init-database.ps1: 拒绝初始化。" -ForegroundColor Red
            Write-Host "  库 ``$Database`` 已存在且包含 $tableCnt 张表。"
            Write-Host '  初始化只允许写入不存在或完全为空的库；本工具永不执行 DROP DATABASE，'
            Write-Host '  也不会覆盖任何既有数据。'
            Write-Host ''
            Write-Host '已有库请走升级路径：'
            Write-Host '  sql\migrations\README.md'
            Write-Host "  先备份：mysqldump.exe --single-transaction --routines --triggers $Database > backup.sql"
            exit 2
        }
        Write-Host '库状态    : 已存在且为 0 张表（视为空库）'
    }

    if ($DryRun) {
        Write-Host '== -DryRun：仅检查完成，未做任何写入 =='
        exit 0
    }

    if ($dbExists -eq '0') {
        Invoke-Scalar -Query "CREATE DATABASE ``$Database`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" | Out-Null
        Write-Host "建库      : 已创建 $Database (utf8mb4 / utf8mb4_general_ci)"
    }

    # -------------------------------------------------------- 步骤执行
    $stepLines = Get-Content -LiteralPath $Steps -Encoding UTF8 |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and -not $_.StartsWith('#') }
    if (-not $stepLines) { Fail "步骤清单为空: $Steps" }
    $total = $stepLines.Count

    $index = 0
    foreach ($line in $stepLines) {
        $index++
        $parts = $line -split '\|'
        $stepId = $parts[0].Trim()
        $gate = $parts[1].Trim()
        $rel = $parts[2].Trim()
        $label = if ($parts.Count -gt 3) { $parts[3].Trim() } else { $stepId }

        $sqlFile = Join-Path $repoRoot $rel
        $logFile = Join-Path $logDir ('{0:d2}-{1}.out' -f $index, $stepId)
        Write-Host ('[{0,2}/{1,2}] {2,-24} ' -f $index, $total, $stepId) -NoNewline

        if (-not (Test-Path -LiteralPath $sqlFile)) {
            Write-Host '缺少文件'
            Fail "步骤 $stepId 的 SQL 不存在: $sqlFile"
        }

        $r = Invoke-Mysql -MysqlArgs @('--batch', $Database) -StdInFile $sqlFile
        $combined = $r.StdOut + $r.StdErr
        [System.IO.File]::WriteAllText($logFile, $combined, (New-Object System.Text.UTF8Encoding($false)))

        if ($r.ExitCode -ne 0) {
            Write-Host "失败 (exit $($r.ExitCode))"
            Write-Host "--- $label 输出尾部 ---" -ForegroundColor Red
            Write-Host $combined
            Write-Host "--- 完整日志: $logFile ---"
            $keepWork = $true
            exit 1
        }

        switch ($gate) {
            'exit' {
                Write-Host 'OK (exit 0)'
            }
            'summary' {
                $failRow = [regex]::Match($combined, '(?m)^FAIL\t.*$')
                $abort = [regex]::Match($combined, '[A-Z_]*_ABORTED')
                $passRow = [regex]::Match($combined, '(?m)^PASS\tfail_cnt=0,.*$')
                if ($failRow.Success) {
                    Write-Host '失败 (SUMMARY 含 FAIL 行)'
                    Write-Host "--- $label 输出尾部 ---" -ForegroundColor Red
                    Write-Host $combined
                    Write-Host "--- 完整日志: $logFile ---"
                    $keepWork = $true
                    exit 1
                }
                if ($abort.Success) {
                    Write-Host "失败 (检测到 $($abort.Value))"
                    Write-Host "--- 完整日志: $logFile ---" -ForegroundColor Red
                    $keepWork = $true
                    exit 1
                }
                if (-not $passRow.Success) {
                    Write-Host '失败 (未找到 SUMMARY=PASS)'
                    Write-Host "--- $label 输出尾部 ---" -ForegroundColor Red
                    Write-Host $combined
                    Write-Host "--- 完整日志: $logFile ---"
                    $keepWork = $true
                    exit 1
                }
                Write-Host "OK (SUMMARY=PASS; $($passRow.Value.Substring(5)))"
            }
            default {
                Fail "步骤 $stepId 的闸门类型未知: $gate"
            }
        }
    }

    Write-Host ''
    Write-Host "== 初始化完成：$target =="
    $summary = Invoke-Scalar -Db $Database -Query @'
SELECT CONCAT('表=', (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()),
              ', 用户=', (SELECT COUNT(*) FROM sys_user),
              ', 角色=', (SELECT COUNT(*) FROM sys_role),
              ', 菜单=', (SELECT COUNT(*) FROM sys_menu),
              ', 角色菜单=', (SELECT COUNT(*) FROM sys_role_menu));
'@
    Write-Host $summary
    Write-Host "本次日志: $logDir（初始化成功后随临时目录删除）"
}
finally {
    if ($keepWork) {
        Write-Host "init-database.ps1: 排障日志保留在 $workDir（查看后请自行删除）" -ForegroundColor Yellow
    } else {
        Remove-Item -LiteralPath $workDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
