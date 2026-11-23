-- 回滚 C_20261123_001__sync_demo_seals_to_p06.sql
-- 恢复到 2026/11/17 版 URL（eyhereal 远端与本机磁盘仍有这两个文件，P06 上没有）。

UPDATE sys_copyright_seal
   SET seal_image_url = '/profile/upload/2026/11/17/seal_zhangsanfeng_20261117_20261117161326A001.png',
       update_by = 'c-rollback',
       update_time = NOW()
 WHERE seal_id = 15;

UPDATE sys_copyright_seal
   SET seal_image_url = '/profile/upload/2026/11/17/seal_lisisi_20261117_20261117161326A002.png',
       update_by = 'c-rollback',
       update_time = NOW()
 WHERE seal_id = 16;

-- 自检：两行都回到 2026/11/17 旧值即为 PASS
SELECT IF(COUNT(*) = 2, 'PASS', 'FAIL') AS check_demo_seal_images_p06_rollback
  FROM sys_copyright_seal
 WHERE seal_id IN (15, 16)
   AND seal_image_url LIKE '/profile/upload/2026/11/17/seal_%';
