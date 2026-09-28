-- =====================================================================
-- B_20260928_005__external_drama_menu_verify.sql
-- 用途: B 模块批3菜单迁移的只读校验（外部视频管理）
-- 闸门: 末尾必须输出 "PASS  fail_cnt=0,"
-- 可重复执行；不写任何数据。
-- =====================================================================
SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS tmp_b_batch3_menu_verify;
CREATE TEMPORARY TABLE tmp_b_batch3_menu_verify (
  check_id VARCHAR(64) NOT NULL PRIMARY KEY,
  result   CHAR(4)     NOT NULL,
  detail   VARCHAR(255) NOT NULL
) ENGINE=MEMORY;

-- 1) M 目录挂在 2000 下，字段正确
INSERT INTO tmp_b_batch3_menu_verify
SELECT 'dir_ready', IF(COUNT(*) = 1, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 2012 AND parent_id = 2000 AND menu_type = 'M' AND visible = '0' AND status = '0'
  AND menu_name = '外部视频管理' AND path = 'external-drama'
  AND component IS NULL AND perms IS NULL;

-- 2) 五个 C 页挂在 2012 下，字段正确
INSERT INTO tmp_b_batch3_menu_verify
SELECT 'channel_page_ready', IF(COUNT(*) = 1, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 2018 AND parent_id = 2012 AND menu_type = 'C' AND visible = '0' AND status = '0'
  AND menu_name = '渠道管理' AND path = 'channel'
  AND component = 'content/external-drama/channel/index' AND perms = 'content:channel:list';

INSERT INTO tmp_b_batch3_menu_verify
SELECT 'drama_page_ready', IF(COUNT(*) = 1, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 2019 AND parent_id = 2012 AND menu_type = 'C' AND visible = '0' AND status = '0'
  AND menu_name = '视频内容管理' AND path = 'drama'
  AND component = 'content/external-drama/drama/index' AND perms = 'content:drama:list';

INSERT INTO tmp_b_batch3_menu_verify
SELECT 'bind_page_ready', IF(COUNT(*) = 1, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 2027 AND parent_id = 2012 AND menu_type = 'C' AND visible = '0' AND status = '0'
  AND menu_name = '关联剧本' AND path = 'bind'
  AND component = 'content/external-drama/bind/index' AND perms = 'content:dramabind:list';

INSERT INTO tmp_b_batch3_menu_verify
SELECT 'status_page_ready', IF(COUNT(*) = 1, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 2028 AND parent_id = 2012 AND menu_type = 'C' AND visible = '0' AND status = '0'
  AND menu_name = '上下架管理' AND path = 'status'
  AND component = 'content/external-drama/status/index' AND perms = 'content:dramastatus:list';

INSERT INTO tmp_b_batch3_menu_verify
SELECT 'stats_page_ready', IF(COUNT(*) = 1, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 2029 AND parent_id = 2012 AND menu_type = 'C' AND visible = '0' AND status = '0'
  AND menu_name = '播放数据' AND path = 'stats'
  AND component = 'content/external-drama/stats/index' AND perms = 'content:dramastats:list';

-- 3) 十一个 F 按钮父子挂接与权限码全部正确
INSERT INTO tmp_b_batch3_menu_verify
SELECT 'buttons_ready', IF(COUNT(*) = 11, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*), '/11')
FROM sys_menu m
JOIN (
  SELECT 2040 AS menu_id, 2018 AS parent_id, 'content:channel:query' AS perms
  UNION ALL SELECT 2041, 2018, 'content:channel:add'
  UNION ALL SELECT 2042, 2018, 'content:channel:edit'
  UNION ALL SELECT 2043, 2019, 'content:drama:query'
  UNION ALL SELECT 2044, 2019, 'content:drama:add'
  UNION ALL SELECT 2045, 2019, 'content:drama:edit'
  UNION ALL SELECT 2046, 2027, 'content:dramabind:query'
  UNION ALL SELECT 2047, 2027, 'content:dramabind:edit'
  UNION ALL SELECT 2048, 2028, 'content:dramastatus:query'
  UNION ALL SELECT 2049, 2028, 'content:dramastatus:edit'
  UNION ALL SELECT 2050, 2029, 'content:dramastats:query'
) e ON m.menu_id = e.menu_id AND m.parent_id = e.parent_id AND m.perms = e.perms
WHERE m.menu_type = 'F' AND m.visible = '0' AND m.status = '0';

-- 4) 十七个权限码（含 5 个 list）不被其他菜单占用
INSERT INTO tmp_b_batch3_menu_verify
SELECT 'perms_not_leaked', IF(COUNT(*) = 0, 'PASS', 'FAIL'), CONCAT('leaked_rows=', COUNT(*))
FROM sys_menu
WHERE perms IN ('content:channel:list', 'content:channel:query', 'content:channel:add', 'content:channel:edit',
                'content:drama:list', 'content:drama:query', 'content:drama:add', 'content:drama:edit',
                'content:dramabind:list', 'content:dramabind:query', 'content:dramabind:edit',
                'content:dramastatus:list', 'content:dramastatus:query', 'content:dramastatus:edit',
                'content:dramastats:list', 'content:dramastats:query')
  AND menu_id NOT IN (2012, 2018, 2019, 2027, 2028, 2029,
                      2040, 2041, 2042, 2043, 2044, 2045, 2046, 2047, 2048, 2049, 2050);

-- 5) 外部视频管理目录下 C 页排序无重复
INSERT INTO tmp_b_batch3_menu_verify
SELECT 'sibling_order_unique', IF(COUNT(*) = 0, 'PASS', 'FAIL'), CONCAT('dup_order_groups=', COUNT(*))
FROM (
  SELECT order_num FROM sys_menu
  WHERE parent_id = 2012 AND menu_type = 'C' AND status = '0'
  GROUP BY order_num HAVING COUNT(*) > 1
) d;

-- 6) 五个前端组件标识各只登记一次
INSERT INTO tmp_b_batch3_menu_verify
SELECT 'component_unique', IF(COUNT(*) = 5, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*), '/5')
FROM sys_menu
WHERE menu_type = 'C' AND component IN (
  'content/external-drama/channel/index',
  'content/external-drama/drama/index',
  'content/external-drama/bind/index',
  'content/external-drama/status/index',
  'content/external-drama/stats/index'
);

-- 7) 超管授权 17 条齐全
INSERT INTO tmp_b_batch3_menu_verify
SELECT 'admin_granted', IF(COUNT(*) = 17, 'PASS', 'FAIL'), CONCAT('granted=', COUNT(*), '/17')
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
WHERE r.del_flag = '0' AND (r.role_key = 'admin' OR r.role_id = 1)
  AND rm.menu_id IN (2012, 2018, 2019, 2027, 2028, 2029,
                     2040, 2041, 2042, 2043, 2044, 2045, 2046, 2047, 2048, 2049, 2050);

SELECT check_id, result, detail FROM tmp_b_batch3_menu_verify ORDER BY check_id;

SELECT menu_id, menu_name, parent_id, order_num, path, component, menu_type, perms
FROM sys_menu
WHERE menu_id IN (2012, 2018, 2019, 2027, 2028, 2029,
                  2040, 2041, 2042, 2043, 2044, 2045, 2046, 2047, 2048, 2049, 2050)
ORDER BY menu_id;

SELECT CASE WHEN SUM(result = 'FAIL') = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result = 'FAIL'),
              ',pass_cnt=', SUM(result = 'PASS'),
              ',total_checks=', COUNT(*),
              ',failed_ids=', IFNULL(GROUP_CONCAT(IF(result = 'FAIL', check_id, NULL) ORDER BY check_id), '')) AS summary
FROM tmp_b_batch3_menu_verify;

DROP TEMPORARY TABLE IF EXISTS tmp_b_batch3_menu_verify;
