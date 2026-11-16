param(
    [string]$Endpoint = $env:MINIO_ENDPOINT,
    [string]$AccessKey = $env:MINIO_ACCESS_KEY,
    [string]$SecretKey = $env:MINIO_SECRET_KEY,
    [string]$Bucket = $(if ($env:MINIO_BUCKET) { $env:MINIO_BUCKET } else { "smartscript-profile" }),
    [string]$PrivateBucket = $(if ($env:MINIO_PRIVATE_BUCKET) { $env:MINIO_PRIVATE_BUCKET } else { "smartscript-private" }),
    [string]$ProfileDir = $(if ($env:RUOYI_PROFILE) { $env:RUOYI_PROFILE } else { "uploadPath" }),
    [string]$MaterialDir = $(if ($env:APP_ADMIN_MATERIAL_STORAGE_PATH) { $env:APP_ADMIN_MATERIAL_STORAGE_PATH } else { "private-materials" }),
    [string]$Alias = "smartscript"
)

$ErrorActionPreference = "Stop"

# MinIO credentials are read from environment variables or parameters.
# Never hardcode a Password or secret key in this file.

if (-not (Get-Command mc -ErrorAction SilentlyContinue)) {
    throw "mc (MinIO Client) not found. Install it first: https://min.io/docs/minio/linux/reference/minio-mc.html"
}
foreach ($pair in @(
    @("MINIO_ENDPOINT", $Endpoint),
    @("MINIO_ACCESS_KEY", $AccessKey),
    @("MINIO_SECRET_KEY", $SecretKey)
)) {
    if ([string]::IsNullOrWhiteSpace($pair[1])) {
        throw "Missing $($pair[0]). Pass it via environment variable or parameter."
    }
}

$profilePath = (Resolve-Path $ProfileDir).Path
$materialPath = $null
if (Test-Path $MaterialDir) {
    $materialPath = (Resolve-Path $MaterialDir).Path
}

Write-Host "Configure MinIO alias: $Alias -> $Endpoint"
mc alias set $Alias $Endpoint $AccessKey $SecretKey | Out-Host

Write-Host "Ensure buckets exist: $Bucket / $PrivateBucket"
mc mb --ignore-existing "$Alias/$Bucket" | Out-Host
mc mb --ignore-existing "$Alias/$PrivateBucket" | Out-Host

Write-Host "Mirror public profile files: $profilePath -> $Alias/$Bucket"
mc mirror --overwrite "$profilePath" "$Alias/$Bucket" | Out-Host

if ($materialPath) {
    Write-Host "Mirror private materials: $materialPath -> $Alias/$PrivateBucket"
    mc mirror --overwrite "$materialPath" "$Alias/$PrivateBucket" | Out-Host
} else {
    Write-Host "Skip private materials: directory not found: $MaterialDir"
}

Write-Host "Done. Set MINIO_ENABLED=true, restart the backend, then verify /profile/** images."
