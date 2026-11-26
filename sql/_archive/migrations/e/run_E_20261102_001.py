# -*- coding: utf-8 -*-
# 执行 E_20261102_001__create_ai_channel.sql 到云端库（多语句）
# 并从后端 .env.local 读取 AI_API_KEY 注入 DeepSeek 渠道（密钥不进迁移文件）
import os
import pymysql

HOST = "124.221.131.38"; PORT = 3306; USER = "dev_he_minghao"; PWD = "W2yW6NuQbd5VWy5WIDWe"; DB = "script_platform_dev"
SQL_PATH = r"D:\实训\第六小组\github-整体后端\sql\migrations\e\E_20261102_001__create_ai_channel.sql"
ENV_LOCAL = r"D:\实训\第六小组\github-整体后端\.env.local"

with open(SQL_PATH, "r", encoding="utf-8") as f:
    script = f.read()

# 去掉纯注释行与 SET NAMES（逐条交给 pymysql 执行，pymysql 默认 utf8mb4）
statements = []
buf = []
for line in script.splitlines():
    stripped = line.strip()
    if stripped.startswith("--") or stripped == "":
        continue
    if stripped.upper().startswith("SET NAMES"):
        continue
    buf.append(line)
    if stripped.endswith(";"):
        statements.append("\n".join(buf))
        buf = []
if buf:
    statements.append("\n".join(buf))

# 从 .env.local 读取 AI_API_KEY
api_key = ""
if os.path.exists(ENV_LOCAL):
    for line in open(ENV_LOCAL, "r", encoding="utf-8"):
        line = line.strip()
        if line.startswith("AI_API_KEY="):
            api_key = line.split("=", 1)[1].strip()
            break

conn = pymysql.connect(host=HOST, port=PORT, user=USER, password=PWD, database=DB,
                       charset="utf8mb4", connect_timeout=15)
cur = conn.cursor()
try:
    for stmt in statements:
        cur.execute(stmt)
    if api_key:
        cur.execute("UPDATE sys_ai_channel SET api_key = %s, remark = %s, update_time = NOW() WHERE channel_name = 'DeepSeek' AND (api_key = '' OR api_key IS NULL)",
                    (api_key, 'E-Default AI 渠道（密钥已由迁移脚本注入）'))
    conn.commit()
    cur.execute("""SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_channel'""")
    tbl = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM sys_ai_channel WHERE channel_name = 'DeepSeek'")
    seed = cur.fetchone()[0]
    cur.execute("SELECT api_key FROM sys_ai_channel WHERE channel_name = 'DeepSeek'")
    row = cur.fetchone()
    key_ok = 1 if (row and row[0]) else 0
    print(f"CHECK tbl={tbl}, seed={seed}, deepseek_key_filled={key_ok}")
    ok = (tbl == 1 and seed >= 1 and key_ok == 1)
    print("PASS" if ok else "FAIL")
finally:
    cur.close(); conn.close()
