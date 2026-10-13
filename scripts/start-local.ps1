﻿<#
=====================================================================
 smart-script 后端本地启动（Windows / PowerShell 5.1+）

   powershell -File scripts\start-local.ps1
   powershell -File scripts\start-local.ps1 -Rebuild   # 先重新打包再启动

 作用：
   1. 读取仓库根目录的 .env.local（本机私有、已被 .gitignore 忽略），
      逐项注入当前进程环境变量，免去每次手动 export；
   2. 按需重新打包，再用 java -jar 启动 ruoyi-admin。

 为什么需要它：mock 短信验证码默认是随机的，只有显式提供 APP_SMS_MOCK_CODE
 才会固定为指定值（见 .env.example 的联调提示）。漏带该变量时，App 端输入
 123456 会报「验证码错误」。本脚本保证本地启动始终带上 .env.local 里的配置。

 安全：本脚本只把 .env.local 的值注入进程环境，不回显任何值（口令不落日志）。
=====================================================================
#>
[CmdletBinding()]
param(
    # 启动前重新打包（代码有改动时必须加，否则跑的是旧 jar）
    [switch]$Rebuild,

    # 自定义环境变量文件路径，默认取仓库根目录的 .env.local
    [string]$EnvFile
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$envPath = if ($EnvFile) { $EnvFile } else { Join-Path $repoRoot '.env.local' }
if (-not (Test-Path -LiteralPath $envPath)) {
    throw "未找到环境变量文件：$envPath（请复制 .env.example 为 .env.local 并填写）"
}

# 解析 KEY=VALUE（忽略空行与 # 注释）；不打印任何值，避免口令进入日志。
$loaded = 0
foreach ($line in Get-Content -LiteralPath $envPath) {
    $trimmed = $line.Trim()
    if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
    $idx = $trimmed.IndexOf('=')
    if ($idx -lt 1) { continue }
    $key = $trimmed.Substring(0, $idx).Trim()
    $value = $trimmed.Substring($idx + 1)
    Set-Item -Path ('Env:' + $key) -Value $value
    $loaded++
}
Write-Host "已加载环境变量：$loaded 项（来源 $envPath）"

if ([string]::IsNullOrWhiteSpace($env:APP_SMS_MOCK_CODE)) {
    Write-Warning 'APP_SMS_MOCK_CODE 为空：mock 短信将使用随机验证码，App 端 123456 会报「验证码错误」。'
} else {
    Write-Host 'mock 短信固定验证码已启用（APP_SMS_MOCK_CODE 已设置）。'
}

$mvn = if ($env:MAVEN_BIN) { $env:MAVEN_BIN } else { 'mvn.cmd' }
$jar = Join-Path $repoRoot 'ruoyi-admin\target\ruoyi-admin.jar'

if ($Rebuild -or -not (Test-Path -LiteralPath $jar)) {
    Write-Host '正在打包后端（mvn -DskipTests package）...'
    & $mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "打包失败（exit=$LASTEXITCODE）" }
}

if (-not (Test-Path -LiteralPath $jar)) {
    throw "未找到可执行 jar：$jar（请先执行 mvn package，或加 -Rebuild）"
}

Write-Host "启动后端：$jar"
& java -jar $jar