# Prepare isolated A3 test database: clone RuoYi SQL + A2 migrate.
param([string]$DbName = 'ruoyi_dev_a3_test')

$ErrorActionPreference = 'Stop'
$dbPass = $env:DB_PASSWORD
if ([string]::IsNullOrEmpty($dbPass)) { throw 'DB_PASSWORD required' }
$mysql = 'D:\java\bin\mysql.exe'
$root = 'D:\build\smart-script-backend'
$ry = Join-Path $root 'sql\ry_20260320.sql'
$a2 = Join-Path $root 'sql\migrations\a2\A2_20260921_001__a2_migrate.sql'
$a2v = Join-Path $root 'sql\migrations\a2\A2_20260921_001__a2_verify.sql'

Write-Host "Creating $DbName ..."
& $mysql -h 127.0.0.1 -u root "-p$dbPass" -e "DROP DATABASE IF EXISTS ``$DbName``; CREATE DATABASE ``$DbName`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

Write-Host 'Importing RuoYi baseline schema...'
& $mysql -h 127.0.0.1 -u root "-p$dbPass" $DbName -e "source $ry"

Write-Host 'Applying A2 migration...'
& $mysql -h 127.0.0.1 -u root "-p$dbPass" $DbName -e "source $a2"

Write-Host 'Verifying A2...'
& $mysql -h 127.0.0.1 -u root "-p$dbPass" $DbName -e "source $a2v"

Write-Host 'A3 DB ready:' $DbName
