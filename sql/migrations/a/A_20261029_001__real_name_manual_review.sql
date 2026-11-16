-- 两张私有材料及审核期间的姓名；审核决定后清除待审核姓名。
SET @stmt = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_real_name_auth' AND COLUMN_NAME='material_back_ref') = 0,
  'ALTER TABLE user_real_name_auth ADD COLUMN material_back_ref VARCHAR(255) NULL', 'SELECT 1');
PREPARE migration_stmt FROM @stmt; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;
SET @stmt = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_real_name_auth' AND COLUMN_NAME='pending_real_name') = 0,
  'ALTER TABLE user_real_name_auth ADD COLUMN pending_real_name VARCHAR(64) NULL', 'SELECT 1');
PREPARE migration_stmt FROM @stmt; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;
UPDATE sys_menu SET menu_name = '实名审核' WHERE perms = 'user:realname:list';
INSERT INTO sys_menu (menu_name,parent_id,order_num,path,menu_type,visible,status,perms,icon,create_by)
SELECT '实名审核决定',m.menu_id,2,'#','F','0','0','user:realname:audit','#','migration'
FROM sys_menu m WHERE m.perms = 'user:realname:list'
AND NOT EXISTS (SELECT 1 FROM (SELECT perms FROM sys_menu) existing WHERE existing.perms = 'user:realname:audit');
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE perms = 'user:realname:audit';
