# -*- coding: utf-8 -*-
# 执行 E_20261030_001__create_ai_skill.sql 到云端库（多语句）
import pymysql, re, sys

HOST="124.221.131.38"; PORT=3306; USER="dev_he_minghao"; PWD="W2yW6NuQbd5VWy5WIDWe"; DB="script_platform_dev"
SQL_PATH=r"D:\实训\第六小组\github-整体后端\sql\migrations\e\E_20261030_001__create_ai_skill.sql"

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

conn = pymysql.connect(host=HOST, port=PORT, user=USER, password=PWD, database=DB,
                       charset="utf8mb4", connect_timeout=15)
cur = conn.cursor()
try:
    for stmt in statements:
        cur.execute(stmt)
    conn.commit()
    # verify 查询（取 CHECK / PASS / FAIL 行）
    cur.execute("""SELECT COUNT(*) FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_skill'""")
    tbl = cur.fetchone()[0]
    cur.execute("""SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_skill'
                     AND COLUMN_NAME IN ('id','skill_name','file_name','file_content','status',
                                         'create_by','create_time','update_by','update_time','remark')""")
    col = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM sys_ai_skill WHERE skill_name IN ('悬疑烧脑导演','甜宠爆款导演')")
    seed = cur.fetchone()[0]
    print(f"CHECK table={tbl}, columns={col}, seeds={seed}")
    print("PASS" if (tbl==1 and col==10 and seed==2) else "FAIL")
finally:
    cur.close(); conn.close()
