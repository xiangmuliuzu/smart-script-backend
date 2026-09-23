#!/usr/bin/env sh
# =====================================================================
# smart-script 平台数据库初始化（POSIX sh；Linux / macOS / WSL / Git Bash）
#
#   ./scripts/db/init-database.sh --database smartscript_dev
#
# 规则：
#   1. 只允许针对「不存在」或「已存在但一张表都没有」的库。
#      库中已有任何表即立刻拒绝退出，永不 DROP DATABASE，永不覆盖数据。
#   2. 已有数据的库请走升级路径：见 sql/migrations/README.md。
#   3. 口令只从环境变量或参数读入，写入 0600 临时配置文件后传给 mysql，
#      不进入命令行参数、不进入任何日志，退出时删除。
#
# 参数（均可用同名大写环境变量提供，见 --help）：
#   --host --port --database --user --password --mysql --steps
# =====================================================================
set -eu

PROG=$(basename "$0")

usage() {
    cat <<'USAGE'
用法: init-database.sh --database <库名> [选项]

必填（参数或环境变量二选一）:
  -d, --database <name> 目标库名        环境变量 DB_NAME
  -u, --user <name>     数据库账号      环境变量 DB_USERNAME
  -p, --password <s>    数据库口令      环境变量 DB_PASSWORD（推荐用环境变量，
                                        避免口令留在 shell 历史里）

可选:
  -h, --host <host>    默认 127.0.0.1   环境变量 DB_HOST
  -P, --port <port>    默认 3306        环境变量 DB_PORT
      --mysql <path>   mysql 客户端     环境变量 MYSQL_BIN，默认取 PATH 中的 mysql
      --steps <file>   步骤清单，默认 scripts/db/init-steps.txt
      --dry-run        只做连接检查与空库判定，不建库、不执行步骤
      --help           显示本帮助

示例:
  export DB_PASSWORD='<本机私有口令>'
  ./scripts/db/init-database.sh -h 127.0.0.1 -P 3306 -d smartscript_dev -u root

初始化完成后，按 README 设置运行期环境变量（DB_URL / REDIS_* /
TOKEN_SECRET / APP_* 等）再启动后端。
USAGE
}

# ---------------------------------------------------------------- 参数解析
db_host=${DB_HOST:-127.0.0.1}
db_port=${DB_PORT:-3306}
db_name=${DB_NAME:-}
db_user=${DB_USERNAME:-}
db_password=${DB_PASSWORD:-}
mysql_bin=${MYSQL_BIN:-mysql}
steps_file=
dry_run=0

while [ $# -gt 0 ]; do
    case "$1" in
        --host|-h)   db_host=${2:?--host 需要取值}; shift 2 ;;
        --port|-P)   db_port=${2:?--port 需要取值}; shift 2 ;;
        --database|-d) db_name=${2:?--database 需要取值}; shift 2 ;;
        --user|-u)   db_user=${2:?--user 需要取值}; shift 2 ;;
        --password|-p) db_password=${2:?--password 需要取值}; shift 2 ;;
        --mysql)     mysql_bin=${2:?--mysql 需要取值}; shift 2 ;;
        --steps)     steps_file=${2:?--steps 需要取值}; shift 2 ;;
        --dry-run)   dry_run=1; shift ;;
        --help)      usage; exit 0 ;;
        *) echo "$PROG: 未知参数 $1" >&2; usage >&2; exit 64 ;;
    esac
done

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
[ -n "$steps_file" ] || steps_file="$SCRIPT_DIR/init-steps.txt"

die() { echo "$PROG: $*" >&2; exit 1; }

[ -n "$db_name" ] || { usage >&2; die "缺少 --database / DB_NAME"; }
[ -n "$db_user" ] || { usage >&2; die "缺少 --user / DB_USERNAME"; }
[ -n "$db_password" ] || {
    usage >&2
    die "缺少 --password / DB_PASSWORD（口令不落盘、不落历史，建议导出 DB_PASSWORD 环境变量）"
}

# 库名只允许标识符字符：既避免转义问题，也避免把 SQL 片段带进语句
case "$db_name" in
    *[!A-Za-z0-9_\$]*|'') die "库名只允许字母、数字、下划线和 \$, 实际为: $db_name" ;;
esac
case "$db_port" in
    *[!0-9]*|'') die "端口必须是数字，实际为: $db_port" ;;
esac
case "$db_host" in
    *[!A-Za-z0-9.:_-]*) die "主机名含可疑字符: $db_host" ;;
esac
case "$db_user" in
    *[!A-Za-z0-9_.@-]*) die "用户名含可疑字符: $db_user" ;;
esac

command -v "$mysql_bin" >/dev/null 2>&1 || [ -x "$mysql_bin" ] \
    || die "找不到 mysql 客户端: $mysql_bin（用 --mysql 指定绝对路径）"
[ -f "$steps_file" ] || die "找不到步骤清单: $steps_file"

# ---------------------------------------------------------------- 临时区
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/smartscript-db-init.XXXXXX") \
    || die "无法创建临时目录"
# 失败时保留临时目录，否则我们刚告诉用户的日志路径会立刻被自己删掉
keep_work=0
cleanup() {
    if [ "$keep_work" = "1" ]; then
        echo "$PROG: 排障日志保留在 $work_dir（查看后请自行删除）" >&2
    else
        rm -rf "$work_dir"
    fi
}
trap cleanup EXIT INT TERM

# Windows 版 mysql.exe 不认 Git Bash 的 /tmp 路径
to_native_path() {
    if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

# 口令经 0600 配置文件传入：不出现在 argv（ps 可见）、不出现在日志
cnf="$work_dir/client.cnf"
escape_cnf() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }
{
    echo '[client]'
    echo "host=\"$(escape_cnf "$db_host")\""
    echo "port=\"$(escape_cnf "$db_port")\""
    echo "user=\"$(escape_cnf "$db_user")\""
    echo "password=\"$(escape_cnf "$db_password")\""
    echo 'default-character-set=utf8mb4'
} > "$cnf"
chmod 600 "$cnf" 2>/dev/null || true

CNF_ARG=$(to_native_path "$cnf")
MYSQL="$mysql_bin --defaults-extra-file=$CNF_ARG --batch"

scalar() {
    # 单值查询：失败直接由 set -e 终止
    "$mysql_bin" "--defaults-extra-file=$CNF_ARG" --batch --skip-column-names "$@" 2>"$work_dir/err.txt" \
        || { cat "$work_dir/err.txt" >&2; return 1; }
}

mask_host="$db_host:$db_port/$db_name"

echo "== smart-script 数据库初始化 =="
echo "目标      : $db_user@$mask_host"
echo "客户端    : $mysql_bin"
echo "步骤清单  : $steps_file"

# ---------------------------------------------------------------- 连接与版本
ver=$(scalar -e "SELECT VERSION();") || die "无法连接数据库（主机/端口/账号/口令有误？）"
case "$ver" in
    8.*) ;;
    *) die "本流程要求 MySQL 8.x，检测到 $ver" ;;
esac
echo "MySQL     : $ver"

# ---------------------------------------------------------------- 空库守卫
db_exists=$(scalar -e "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '$db_name';")
table_cnt=0
if [ "$db_exists" = "0" ]; then
    echo "库状态    : 不存在（将新建）"
else
    table_cnt=$(scalar -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '$db_name';")
    if [ "$table_cnt" != "0" ]; then
        cat >&2 <<EOF

$PROG: 拒绝初始化。
  库 \`$db_name\` 已存在且包含 $table_cnt 张表。
  初始化只允许写入不存在或完全为空的库；本工具永不执行 DROP DATABASE，
  也不会覆盖任何既有数据。

已有库请走升级路径：
  sql/migrations/README.md
  先备份：mysqldump --single-transaction --routines --triggers \`$db_name\` > backup.sql
EOF
        exit 2
    fi
    echo "库状态    : 已存在且为 0 张表（视为空库）"
fi

if [ "$dry_run" = "1" ]; then
    echo "== --dry-run：仅检查完成，未做任何写入 =="
    exit 0
fi

if [ "$db_exists" = "0" ]; then
    scalar -e "CREATE DATABASE \`$db_name\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" \
        >/dev/null || die "建库失败"
    echo "建库      : 已创建 $db_name (utf8mb4 / utf8mb4_general_ci)"
fi

# ---------------------------------------------------------------- 步骤执行
grep -v '^[[:space:]]*#' "$steps_file" | grep -v '^[[:space:]]*$' | tr -d '\r' > "$work_dir/steps.txt"
total=$(wc -l < "$work_dir/steps.txt" | tr -d ' ')
[ "$total" -gt 0 ] || die "步骤清单为空: $steps_file"

log_dir="$work_dir/logs"
mkdir -p "$log_dir"

gate_summary() {
    # $1=输出文件；SUMMARY=PASS 判定
    if grep -qE '^FAIL[[:space:]]' "$1"; then
        grep -E '^FAIL[[:space:]]' "$1" | head -1 | cut -f2- >&2
        return 1
    fi
    if grep -q '_ABORTED' "$1"; then
        grep -oE '[A-Z_]*_ABORTED' "$1" | head -1 >&2
        return 1
    fi
    if ! grep -qE '^PASS[[:space:]]+fail_cnt=0,' "$1"; then
        echo "未找到 SUMMARY=PASS（fail_cnt=0）" >&2
        return 1
    fi
    grep -E '^PASS[[:space:]]+fail_cnt=0,' "$1" | head -1 | cut -f2-
    return 0
}

fail_step() {
    # $1=输出文件 $2=说明
    echo "--- $2 输出尾部 ---" >&2
    tail -30 "$1" >&2
    echo "--- 完整日志: $1 ---" >&2
    keep_work=1
    exit 1
}

i=0
while IFS='|' read -r step_id gate rel label; do
    [ -n "$step_id" ] || continue
    i=$((i + 1))
    sql_file="$REPO_ROOT/$rel"
    out="$log_dir/$(printf '%02d' "$i")-$step_id.out"

    printf '[%2d/%2d] %-24s ' "$i" "$total" "$step_id"
    [ -f "$sql_file" ] || { echo "缺少文件"; die "步骤 $step_id 的 SQL 不存在: $sql_file"; }

    if "$mysql_bin" "--defaults-extra-file=$CNF_ARG" --batch "$db_name" < "$sql_file" > "$out" 2>&1; then
        rc=0
    else
        rc=$?
    fi

    case "$gate" in
        exit)
            if [ "$rc" = "0" ]; then
                echo "OK (exit 0)"
            else
                echo "失败 (exit $rc)"
                fail_step "$out" "$label"
            fi
            ;;
        summary)
            if [ "$rc" != "0" ]; then
                echo "失败 (exit $rc)"
                fail_step "$out" "$label"
            fi
            if summary_line=$(gate_summary "$out"); then
                echo "OK (SUMMARY=PASS; $summary_line)"
            else
                echo "失败 (SUMMARY 非 PASS)"
                fail_step "$out" "$label"
            fi
            ;;
        *)
            die "步骤 $step_id 的闸门类型未知: $gate"
            ;;
    esac
done < "$work_dir/steps.txt"

echo
echo "== 初始化完成：$mask_host =="
scalar "$db_name" -e "SELECT CONCAT('表=', (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()), ', 用户=', (SELECT COUNT(*) FROM sys_user), ', 角色=', (SELECT COUNT(*) FROM sys_role), ', 菜单=', (SELECT COUNT(*) FROM sys_menu), ', 角色菜单=', (SELECT COUNT(*) FROM sys_role_menu));"
echo "本次日志: $log_dir（随临时目录在退出时删除；需要留档请自行重定向）"
