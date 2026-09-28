-- U20260928_001__user_profile_bio_rollback.sql
-- A5 个人简介（bio）回滚：删除 sys_user.bio 列
--
-- 危险操作：DROP COLUMN 将永久丢失用户填写的简介数据。
-- 默认拒绝执行；确需回滚时先 mysqldump 备份 sys_user(bio)，再显式：
--   SET @a5_force_bio_rollback = 1;
-- 后重跑本脚本。
-- 可重复：列不存在时输出 note 并跳过，可安全重复执行；
--         列存在与否的探测先于任何对 bio 列本身的查询，避免二次执行报错。

SET @force := IFNULL(@a5_force_bio_rollback, 0);

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'bio'
);

-- 仅在列确实存在时才统计非空数据；列不存在时置 0，不触碰 bio
SET @cnt_sql := IF(@col_exists = 0,
  'SELECT 0 INTO @nonempty',
  'SELECT COUNT(*) INTO @nonempty FROM sys_user WHERE bio IS NOT NULL AND bio <> ''''');
PREPARE cnt_stmt FROM @cnt_sql;
EXECUTE cnt_stmt;
DEALLOCATE PREPARE cnt_stmt;

SET @ddl := IF(@col_exists = 0,
  'SELECT ''column sys_user.bio not exists - nothing to roll back'' AS note',
  IF(@nonempty > 0 AND @force = 0,
    'SELECT ''ROLLBACK_ABORTED: sys_user.bio contains user data; backup and SET @a5_force_bio_rollback = 1 to proceed'' AS note',
    'ALTER TABLE sys_user DROP COLUMN bio'));

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
