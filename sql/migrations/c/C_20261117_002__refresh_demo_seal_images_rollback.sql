-- 回滚 C_20261117_002__refresh_demo_seal_images.sql
-- 恢复为迁移前的历史 URL（这两个文件在两台机器上都已缺失，回滚后管理端会重新变成加载失败，
-- 仅用于把数据还原成迁移前的样子）。原值备份见 smart-script-backend/.local/seal_url_backup_20261117.txt。

UPDATE sys_copyright_seal
   SET seal_image_url = '/profile/upload/2026/10/28/avatar_20261028143657A001.png',
       update_by = 'c-rollback',
       update_time = NOW()
 WHERE seal_id = 15;

UPDATE sys_copyright_seal
   SET seal_image_url = '/profile/upload/2026/10/27/test_images_20261027155558A002.jpg',
       update_by = 'c-rollback',
       update_time = NOW()
 WHERE seal_id = 16;

-- 自检：两行都回到旧值即为 PASS
SELECT IF(COUNT(*) = 2, 'PASS', 'FAIL') AS check_demo_seal_images_rollback
  FROM sys_copyright_seal
 WHERE (seal_id = 15 AND seal_image_url = '/profile/upload/2026/10/28/avatar_20261028143657A001.png')
    OR (seal_id = 16 AND seal_image_url = '/profile/upload/2026/10/27/test_images_20261027155558A002.jpg');
