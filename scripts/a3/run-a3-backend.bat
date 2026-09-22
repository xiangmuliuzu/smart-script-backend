@echo off
REM A3 backend launcher. Secrets MUST come from the environment - never hardcode.
if "%TOKEN_SECRET%"=="" (echo TOKEN_SECRET is required & exit /b 1)
if "%APP_TOKEN_SECRET%"=="" (echo APP_TOKEN_SECRET is required & exit /b 1)
if "%APP_TOKEN_ISSUER%"=="" (echo APP_TOKEN_ISSUER is required & exit /b 1)
if "%APP_TOKEN_AUDIENCE%"=="" (echo APP_TOKEN_AUDIENCE is required & exit /b 1)
if "%APP_REDIS_KEY_PREFIX%"=="" (echo APP_REDIS_KEY_PREFIX is required & exit /b 1)
if "%DB_USERNAME%"=="" (echo DB_USERNAME is required & exit /b 1)
if "%DB_URL%"=="" (echo DB_URL is required & exit /b 1)
if "%DB_PASSWORD%"=="" (echo DB_PASSWORD is required & exit /b 1)
if "%APP_AUTH_ENV%"=="" set APP_AUTH_ENV=local
if "%SMS_PROVIDER%"=="" set SMS_PROVIDER=mock
if "%REDIS_HOST%"=="" set REDIS_HOST=localhost
if "%REDIS_PORT%"=="" set REDIS_PORT=6379
if "%A3_JAVA%"=="" set A3_JAVA=D:\java\jdk-17\bin\java.exe
if "%A3_JAR%"=="" set A3_JAR=D:\build\smart-script-backend\ruoyi-admin\target\ruoyi-admin.jar
if "%A3_LOG%"=="" set A3_LOG=D:\build\shared\a3-evidence\backend-a3.log
start "A3-backend" /b "%A3_JAVA%" -jar "%A3_JAR%" > "%A3_LOG%" 2>&1
