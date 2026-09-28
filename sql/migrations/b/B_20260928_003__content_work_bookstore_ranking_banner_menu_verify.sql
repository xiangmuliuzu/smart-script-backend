-- =====================================================================
-- B_20260928_003__content_work_bookstore_ranking_banner_menu_verify.sql
-- 用途: B 模块批1菜单迁移的只读校验
-- 闸门: 末尾必须输出 "PASS  fail_cnt=0,"
-- 可重复执行；不写任何数据。
-- =====================================================================
SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS tmp_b_batch1_menu_verify;
CREATE TEMPORARY TABLE tmp_b_batch1_menu_verify (
  check_id VARCHAR(64) NOT NULL PRIMARY KEY,
  result   CHAR(4)     NOT NULL,
  detail   VARCHAR(255) NOT NULL
) ENGINE=MEMORY;

-- 1) 四个 C 页挂在 2000 下，字段正确
INSERT INTO tmp_b_batch1_menu_verify
SELECT 'work_page_ready', IF(COUNT(*) = 1, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 2013 AND parent_id = 2000 AND menu_type = 'C' AND visible = '0' AND status = '0'
  AND menu_name = '作品管理' AND path = 'work'
  AND component = 'content/work/index' AND perms = 'content:work:list';

INSERT INTO tmp_b_batch1_menu_verify
SELECT 'bookstore_page_ready', IF(COUNT(*) = 1, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 2014 AND parent_id = 2000 AND menu_type = 'C' AND visible = '0' AND status = '0'
  AND menu_name = '书城作品管理' AND path = 'bookstore'
  AND component = 'content/bookstore/index' AND perms = 'content:bookstore:list';

INSERT INTO tmp_b_batch1_menu_verify
SELECT 'ranking_page_ready', IF(COUNT(*) = 1, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 2015 AND parent_id = 2000 AND menu_type = 'C' AND visible = '0' AND status = '0'
  AND menu_name = '排行榜管理' AND path = 'ranking'
  AND component = 'content/ranking/index' AND perms = 'content:ranking:list';

INSERT INTO tmp_b_batch1_menu_verify
SELECT 'banner_page_ready', IF(COUNT(*) = 1, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 2016 AND parent_id = 2000 AND menu_type = 'C' AND visible = '0' AND status = '0'
  AND menu_name = 'Banner管理' AND path = 'banner'
  AND component = 'content/banner/index' AND perms = 'content:banner:list';

-- 2) 八个 F 按钮父子挂接与权限码全部正确
INSERT INTO tmp_b_batch1_menu_verify
SELECT 'buttons_ready', IF(COUNT(*) = 8, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*), '/8')
FROM sys_menu m
JOIN (
  SELECT 2030 AS menu_id, 2013 AS parent_id, 'content:work:query' AS perms
  UNION ALL SELECT 2031, 2014, 'content:bookstore:query'
  UNION ALL SELECT 2032, 2014, 'content:bookstore:edit'
  UNION ALL SELECT 2033, 2015, 'content:ranking:query'
  UNION ALL SELECT 2034, 2015, 'content:ranking:edit'
  UNION ALL SELECT 2035, 2016, 'content:banner:query'
  UNION ALL SELECT 2036, 2016, 'content:banner:add'
  UNION ALL SELECT 2037, 2016, 'content:banner:edit'
) e ON m.menu_id = e.menu_id AND m.parent_id = e.parent_id AND m.perms = e.perms
WHERE m.menu_type = 'F' AND m.visible = '0' AND m.status = '0';

-- 3) 十二个权限码不被其他菜单占用
INSERT INTO tmp_b_batch1_menu_verify
SELECT 'perms_not_leaked', IF(COUNT(*) = 0, 'PASS', 'FAIL'), CONCAT('leaked_rows=', COUNT(*))
FROM sys_menu
WHERE perms IN ('content:work:list', 'content:work:query',
                'content:bookstore:list', 'content:bookstore:query', 'content:bookstore:edit',
                'content:ranking:list', 'content:ranking:query', 'content:ranking:edit',
                'content:banner:list', 'content:banner:query', 'content:banner:add', 'content:banner:edit')
  AND menu_id NOT IN (2013, 2014, 2015, 2016, 2030, 2031, 2032, 2033, 2034, 2035, 2036, 2037);

-- 4) 内容管理目录下 C 页排序无重复
INSERT INTO tmp_b_batch1_menu_verify
SELECT 'sibling_order_unique', IF(COUNT(*) = 0, 'PASS', 'FAIL'), CONCAT('dup_order_groups=', COUNT(*))
FROM (
  SELECT order_num FROM sys_menu
  WHERE parent_id = 2000 AND menu_type = 'C' AND status = '0'
  GROUP BY order_num HAVING COUNT(*) > 1
) d;

-- 5) 四个前端组件标识各只登记一次
INSERT INTO tmp_b_batch1_menu_verify
SELECT 'component_unique', IF(COUNT(*) = 4, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*), '/4')
FROM sys_menu
WHERE menu_type = 'C' AND component IN ('content/work/index', 'content/bookstore/index', 'content/ranking/index', 'content/banner/index');

-- 6) 超管授权 12 条齐全
INSERT INTO tmp_b_batch1_menu_verify
SELECT 'admin_granted', IF(COUNT(*) = 12, 'PASS', 'FAIL'), CONCAT('granted=', COUNT(*), '/12')
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
WHERE r.del_flag = '0' AND (r.role_key = 'admin' OR r.role_id = 1)
  AND rm.menu_id IN (2013, 2014, 2015, 2016, 2030, 2031, 2032, 2033, 2034, 2035, 2036, 2037);

SELECT check_id, result, detail FROM tmp_b_batch1_menu_verify ORDER BY check_id;

SELECT menu_id, menu_name, parent_id, order_num, path, component, menu_type, perms
FROM sys_menu
WHERE menu_id IN (2013, 2014, 2015, 2016, 2030, 2031, 2032, 2033, 2034, 2035, 2036, 2037)
ORDER BY menu_id;

SELECT CASE WHEN SUM(result = 'FAIL') = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result = 'FAIL'),
              ',pass_cnt=', SUM(result = 'PASS'),
              ',total_checks=', COUNT(*),
              ',failed_ids=', IFNULL(GROUP_CONCAT(IF(result = 'FAIL', check_id, NULL) ORDER BY check_id), '')) AS summary
FROM tmp_b_batch1_menu_verify;

DROP TEMPORARY TABLE IF EXISTS tmp_b_batch1_menu_verify;
