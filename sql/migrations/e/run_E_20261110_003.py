# -*- coding: utf-8 -*-
# 执行 E_20261110_003__ai_skill_bundle.sql 到云端库（多语句）
import pymysql

HOST = "124.221.131.38"; PORT = 3306; USER = "dev_he_minghao"; PWD = "W2yW6NuQbd5VWy5WIDWe"; DB = "script_platform_dev"
SQL_PATH = r"D:\实训\第六小组\github-整体后端\sql\migrations\e\E_20261110_003__ai_skill_bundle.sql"

with open(SQL_PATH, "r", encoding="utf-8") as f:
    script = f.read()

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
        # 打印 verify 段的返回，便于肉眼核对
        first = stmt.strip().splitlines()[0].strip().upper()
        if first.startswith("SELECT"):
            for row in cur.fetchall():
                print(" ".join(str(c) for c in row))
    conn.commit()

    cur.execute("""SELECT DATA_TYPE FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_skill'
                     AND COLUMN_NAME = 'file_content'""")
    ct = cur.fetchone()
    cur.execute("""SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_ai_skill'
                     AND COLUMN_NAME IN ('file_tree','file_count')""")
    cols = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM sys_ai_skill")
    total = cur.fetchone()[0]
    ok = (ct and ct[0] == 'mediumtext' and cols == 2)
    print(f"CHECK file_content={ct[0] if ct else 'missing'}, new_cols={cols}, rows={total}")
    print("PASS" if ok else "FAIL")
finally:
    cur.close(); conn.close()
