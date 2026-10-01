# A0-R1 验收脚本（安全版）
# - 口令仅从环境变量读取，禁止仓库默认密码
# - 证据文件不落盘原始 Token / 密码
# - /health 匿名探针；logout 失效仅在明确 HTTP/API 401 时通过
# 用法:
#   $env:VERIFY_ADMIN_PASSWORD='<secret>'
#   $env:VERIFY_LIMITED_PASSWORD='<secret>'   # 可选 g1viewer
#   powershell -File scripts\a0-r1-verify-api.ps1
param(
    [string]$BackendBase = $(if ($env:VERIFY_BACKEND_BASE) { $env:VERIFY_BACKEND_BASE } else { "http://127.0.0.1:8080" }),
    [string]$OutDir = $(if ($env:VERIFY_OUT_DIR) { $env:VERIFY_OUT_DIR } else { "D:\build\shared\backups\A0-R1-secure" }),
    [string]$AdminUsername = $(if ($env:VERIFY_ADMIN_USER) { $env:VERIFY_ADMIN_USER } else { "admin" }),
    [string]$AdminPassword = $env:VERIFY_ADMIN_PASSWORD,
    [string]$LimitedUsername = $(if ($env:VERIFY_LIMITED_USER) { $env:VERIFY_LIMITED_USER } else { "g1viewer" }),
    [string]$LimitedPassword = $env:VERIFY_LIMITED_PASSWORD
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    Write-Error "VERIFY_ADMIN_PASSWORD env var is required. Default passwords are forbidden."
    exit 2
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$redis = $(if ($env:VERIFY_REDIS_CLI) { $env:VERIFY_REDIS_CLI } else { "D:\java\Redis-7.2.12-Windows-x64-cygwin-with-Service\redis-cli.exe" })
$results = New-Object System.Collections.Generic.List[object]

function Protect-Text([string]$s) {
    if ([string]::IsNullOrEmpty($s)) { return $s }
    $s = $s -replace '"token"\s*:\s*"[^"]+"', '"token":"[REDACTED]"'
    $s = $s -replace 'eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}', '[REDACTED_JWT]'
    if (-not [string]::IsNullOrWhiteSpace($script:AdminPassword)) {
        $s = $s -replace [regex]::Escape($script:AdminPassword), '[REDACTED_PASSWORD]'
    }
    if (-not [string]::IsNullOrWhiteSpace($script:LimitedPassword)) {
        $s = $s -replace [regex]::Escape($script:LimitedPassword), '[REDACTED_PASSWORD]'
    }
    return $s
}

function Write-Result($name, $ok, $detail) {
    $safe = Protect-Text $detail
    $script:results.Add([pscustomobject]@{ check = $name; ok = [bool]$ok; detail = $safe })
    $flag = if ($ok) { "PASS" } else { "FAIL" }
    Write-Output "[$flag] $name :: $($safe.Substring(0, [Math]::Min(180, $safe.Length)))"
}

function Save-Evidence($name, $raw) {
    $path = Join-Path $OutDir $name
    Protect-Text $raw | Out-File -Encoding utf8 $path
}

function Get-JsonUtf8($url, $headers) {
    $r = Invoke-WebRequest -Uri $url -Headers $headers -UseBasicParsing -TimeoutSec 20
    return [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
}

function Post-JsonUtf8($url, $body, $headers) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    $r = Invoke-WebRequest -Uri $url -Method Post -ContentType "application/json" -Body $bytes -Headers $headers -UseBasicParsing -TimeoutSec 20
    return [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
}

function Get-CaptchaAnswer($uuid) {
    $raw = & $redis --raw get "captcha_codes:$uuid"
    return $raw.Trim().Trim('"')
}

function New-LoginBody([string]$username, [string]$password, [string]$code, [string]$uuid) {
    $obj = [ordered]@{
        username = $username
        password = $password
        code     = $code
        uuid     = $uuid
    }
    return ($obj | ConvertTo-Json -Compress)
}

function Get-ApiCode($jsonText) {
    try {
        $o = $jsonText | ConvertFrom-Json
        if ($null -ne $o.code) { return [int]$o.code }
    } catch { }
    return $null
}

function Get-HttpStatusCodeFromError($err) {
    if ($null -eq $err) { return $null }
    if ($err.Exception -and $err.Exception.Response -and $err.Exception.Response.StatusCode) {
        try { return [int]$err.Exception.Response.StatusCode } catch { }
    }
    if ($err.Exception -and $err.Exception.Message -match '\b(401|Unauthorized)\b') { return 401 }
    return $null
}

function Invoke-Login($username, $password) {
    $capRaw = Get-JsonUtf8 "$BackendBase/captchaImage" $null
    Save-Evidence "captcha.json" $capRaw
    $cap = $capRaw | ConvertFrom-Json
    $uuid = [string]$cap.uuid
    $answer = Get-CaptchaAnswer $uuid
    $body = New-LoginBody $username $password $answer $uuid
    $loginRaw = Post-JsonUtf8 "$BackendBase/login" $body $null
    Save-Evidence ("03-login-" + $username + ".json") $loginRaw
    $parsed = $loginRaw | ConvertFrom-Json
    $token = $parsed.token
    return @{ raw = $loginRaw; json = $parsed; token = $token }
}

try {
    $healthRaw = Get-JsonUtf8 "$BackendBase/health" $null
    Save-Evidence "01-health.json" $healthRaw
    $health = $healthRaw | ConvertFrom-Json
    $healthOk = ($health.code -eq 200) -and ($health.data.status -eq "UP" -or $health.data -eq "UP" -or ("$($health.data)" -match "UP"))
    if (-not $healthOk -and $health.code -eq 200 -and $healthRaw -match 'UP') { $healthOk = $true }
    Write-Result "health" $healthOk $healthRaw
} catch {
    Write-Result "health" $false $_.Exception.Message
}

$token = $null
try {
    $login = Invoke-Login $AdminUsername $AdminPassword
    $token = $login.token
    $code = $login.json.code
    Write-Result "login-admin" ($code -eq 200 -and -not [string]::IsNullOrEmpty($token)) "code=$code token_present=$([bool]$token)"
} catch {
    Write-Result "login-admin" $false $_.Exception.Message
    ($results | ConvertTo-Json -Depth 4) | Out-File -Encoding utf8 (Join-Path $OutDir "00-summary.json")
    exit 1
}

$h = @{ Authorization = "Bearer $token" }
$checks = @(
    @{ name = "getInfo"; path = "/getInfo" },
    @{ name = "getRouters"; path = "/getRouters" },
    @{ name = "menu-list"; path = "/system/menu/list" },
    @{ name = "role-list"; path = "/system/role/list?pageNum=1&pageSize=5" },
    @{ name = "post-list"; path = "/system/post/list?pageNum=1&pageSize=5" },
    @{ name = "dict-type-list"; path = "/system/dict/type/list?pageNum=1&pageSize=5" },
    @{ name = "config-list"; path = "/system/config/list?pageNum=1&pageSize=5" },
    @{ name = "operlog-list"; path = "/monitor/operlog/list?pageNum=1&pageSize=5" },
    @{ name = "logininfor-list"; path = "/monitor/logininfor/list?pageNum=1&pageSize=5" }
)
foreach ($c in $checks) {
    try {
        $raw = Get-JsonUtf8 ($BackendBase + $c.path) $h
        Save-Evidence ("04-" + $c.name + ".json") $raw
        $j = Protect-Text $raw | ConvertFrom-Json
        Write-Result $c.name ($j.code -eq 200) ("code=" + $j.code)
    } catch {
        Write-Result $c.name $false $_.Exception.Message
    }
}

if (-not [string]::IsNullOrWhiteSpace($LimitedPassword)) {
    try {
        $lLogin = Invoke-Login $LimitedUsername $LimitedPassword
        $lCode = $lLogin.json.code
        if ($lCode -ne 200 -or [string]::IsNullOrEmpty($lLogin.token)) {
            Write-Result "limited-login" $false "limited user login code=$lCode (must be 200)"
        } else {
            Write-Result "limited-login" $true "code=200"
            $h2 = @{ Authorization = "Bearer $($lLogin.token)" }
            try {
                $forbidden = Get-JsonUtf8 "$BackendBase/system/user/list?pageNum=1&pageSize=5" $h2
                Save-Evidence "05-forbidden-user-list.json" $forbidden
                $fj = Protect-Text $forbidden | ConvertFrom-Json
                $ok403 = ([int]$fj.code -eq 403)
                Write-Result "limited-forbidden-403" $ok403 ("code=" + $fj.code + " msg=" + $fj.msg)
            } catch {
                $msg = $_.Exception.Message
                Save-Evidence "05-forbidden-user-list.txt" $msg
                $http = Get-HttpStatusCodeFromError $_
                # 仅明确 HTTP 403 通过；其他异常（超时/连接失败）不得记 PASS
                Write-Result "limited-forbidden-403" ($http -eq 403) ("http=$http msg=$msg")
            }
        }
    } catch {
        Write-Result "limited-login" $false $_.Exception.Message
    }
} else {
    Write-Result "limited-login" $false "VERIFY_LIMITED_PASSWORD not set; 403 negative case not executed"
}

try {
    $logoutRaw = Post-JsonUtf8 "$BackendBase/logout" "{}" $h
    Save-Evidence "06-logout.json" $logoutRaw
    $logoutCode = Get-ApiCode $logoutRaw
    Write-Result "logout" ($logoutCode -eq 200) ("code=$logoutCode")
    $passLogout = $false
    $logoutDetail = ""
    try {
        $after = Get-JsonUtf8 "$BackendBase/getInfo" $h
        Save-Evidence "06-getInfo-after-logout.json" $after
        $aj = Protect-Text $after | ConvertFrom-Json
        $afterCode = $null
        if ($null -ne $aj.code) { $afterCode = [int]$aj.code }
        $logoutDetail = "api_code=$afterCode"
        # 仅 API 业务码 401 通过
        $passLogout = ($afterCode -eq 401)
    } catch {
        $http = Get-HttpStatusCodeFromError $_
        $logoutDetail = "http=$http exception=$($_.Exception.Message)"
        # 仅明确 HTTP 401 通过；连接失败/超时/崩溃不得记 PASS
        $passLogout = ($http -eq 401)
    }
    Write-Result "logout-invalidates-token" $passLogout $logoutDetail
} catch {
    Write-Result "logout" $false $_.Exception.Message
}

($results | ConvertTo-Json -Depth 4) | Out-File -Encoding utf8 (Join-Path $OutDir "00-summary.json")
$fail = @($results | Where-Object { -not $_.ok })
Write-Output "---- SUMMARY total=$($results.Count) fail=$($fail.Count) outdir=$OutDir ----"
if ($fail.Count -gt 0) { exit 1 } else { exit 0 }
