-- A5_20260928_001__user_profile_bio_migrate.sql
-- A5 个人简介（bio）：sys_user 新增 bio 列
--
-- 依据：A用户与认证开发规格.md §8.3「个人资料——查询和修改头像、昵称、简介」；
--       A5-App用户中心实施方案与接口契约.md §1.2（本次同步修订）。
-- 长度：VARCHAR(200)，与服务端校验 BIO_MAX=200、Flutter 本地预校验一致；
--       空串（DEFAULT ''）表示未填写，允许清空。
-- 适用：MySQL 8.0.36。执行前必须先跑 precheck 且全部 PASS，并完成 mysqldump 备份。
-- 幂等：先探测列是否存在，已存在则跳过（可安全重复执行）。
-- 回滚：U20260928_001__user_profile_bio_rollback.sql（默认拒绝破坏性 DROP，需显式 force）。

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME = 'bio'
);

SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE sys_user ADD COLUMN bio VARCHAR(200) NOT NULL DEFAULT '''' COMMENT ''个人简介（A5 用户中心，空串表示未填写）'' AFTER remark',
  'SELECT ''column sys_user.bio already exists - skip'' AS note');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 执行后请运行 A5_20260928_001__user_profile_bio_verify.sql 并保留输出；
-- 回滚执行 U20260928_001__user_profile_bio_rollback.sql。
