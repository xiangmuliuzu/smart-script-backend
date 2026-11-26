-- 演示印章「张三丰印」「李思思印」切换到 P06 服务器上的新文件
-- 背景：两行原 URL（2026/11/17 版）只存在于 eyhereal 远端与本机磁盘，P06（192.168.100.3:1100，
--       现网统一入口）上没有这两个文件，P06 端 App/部署页两枚印加载失败。
--       新文件由 .local/sync_seals_p06_20261123.py 以属主账号上传到 P06，
--       匿名探测 /profile 与 /prod-api/profile 均 200；同一相对路径已复制回本机 uploadPath。
-- 范围：只改 seal_image_url 与更新人/更新时间，不动审核状态、印章状态、属主。

UPDATE sys_copyright_seal
   SET seal_image_url = '/profile/upload/2026/11/23/seal_zhangsanfeng_20261117_20261123153752A004.png',
       update_by = 'c-migration',
       update_time = NOW()
 WHERE seal_id = 15;

UPDATE sys_copyright_seal
   SET seal_image_url = '/profile/upload/2026/11/23/seal_lisisi_20261117_20261123153753A005.png',
       update_by = 'c-migration',
       update_time = NOW()
 WHERE seal_id = 16;

-- 自检：两行都指向 2026/11/23 的 P06 新图即为 PASS
SELECT IF(COUNT(*) = 2, 'PASS', 'FAIL') AS check_demo_seal_images_p06
  FROM sys_copyright_seal
 WHERE seal_id IN (15, 16)
   AND seal_image_url LIKE '/profile/upload/2026/11/23/seal_%';
