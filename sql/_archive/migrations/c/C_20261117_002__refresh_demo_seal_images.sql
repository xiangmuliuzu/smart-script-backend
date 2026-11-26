-- 演示印章「张三丰印」「李思思印」换新版印面图
-- 背景：这两行的原图（2026/10/28 avatar_*、2026/10/27 test_images_*）在本机与服务器磁盘上都已不存在，
--       管理端印章审核点开只有加载失败。新图由 .local/place_seals_20261117.py 以属主账号上传，
--       同一相对路径已同时落在服务器 uploadPath 与本机 uploadPath，四端（管理端/PC/APP/真机）都能看到。
-- 范围：只改 seal_image_url 与更新人/更新时间，不动审核状态、印章状态、属主。

UPDATE sys_copyright_seal
   SET seal_image_url = '/profile/upload/2026/11/17/seal_zhangsanfeng_20261117_20261117161326A001.png',
       update_by = 'c-migration',
       update_time = NOW()
 WHERE seal_id = 15;

UPDATE sys_copyright_seal
   SET seal_image_url = '/profile/upload/2026/11/17/seal_lisisi_20261117_20261117161326A002.png',
       update_by = 'c-migration',
       update_time = NOW()
 WHERE seal_id = 16;

-- 自检：两行都指向 2026/11/17 的新图即为 PASS
SELECT IF(COUNT(*) = 2, 'PASS', 'FAIL') AS check_demo_seal_images
  FROM sys_copyright_seal
 WHERE seal_id IN (15, 16)
   AND seal_image_url LIKE '/profile/upload/2026/11/17/seal_%';
