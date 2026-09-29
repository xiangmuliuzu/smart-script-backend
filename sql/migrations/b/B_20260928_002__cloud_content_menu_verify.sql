-- =====================================================================
-- B_20260928_002__cloud_content_menu_verify.sql
-- 用途: 云库分类/标签菜单迁移的只读校验
-- 闸门: 末尾必须输出 “PASS  fail_cnt=0,”
-- 可重复执行；不写任何数据。
-- =====================================================================
SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS tmp_b_cloud_menu_verify;
CREATE TEMPORARY TABLE tmp_b_cloud_menu_verify (
  check_id VARCHAR(64) NOT NULL PRIMARY KEY,
  result   CHAR(4)     NOT NULL,
  detail   VARCHAR(255) NOT NULL
) ENGINE=MEMORY;

-- 1) 两个 C 页挂在 2000 下，字段正确
INSERT INTO tmp_b_cloud_menu_verify
SELECT 'category_page_ready', IF(COUNT(*) = 1, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 2010 AND parent_id = 2000 AND menu_type = 'C' AND visible = '0' AND status = '0'
  AND menu_name = '分类管理' AND path = 'category'
  AND component = 'content/category/index' AND perms = 'content:category:list';

INSERT INTO tmp_b_cloud_menu_verify
SELECT 'tag_page_ready', IF(COUNT(*) = 1, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 2011 AND parent_id = 2000 AND menu_type = 'C' AND visible = '0' AND status = '0'
  AND menu_name = '标签管理' AND path = 'tag'
  AND component = 'content/tag/index' AND perms = 'content:tag:list';

-- 2) 七个 F 按钮父子挂接与权限码全部正确
INSERT INTO tmp_b_cloud_menu_verify
SELECT 'buttons_ready', IF(COUNT(*) = 7, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*), '/7')
FROM sys_menu m
JOIN (
  SELECT 2020 AS menu_id, 2010 AS parent_id, 'content:category:query' AS perms
  UNION ALL SELECT 2021, 2010, 'content:category:add'
  UNION ALL SELECT 2022, 2010, 'content:category:edit'
  UNION ALL SELECT 2023, 2011, 'content:tag:query'
  UNION ALL SELECT 2024, 2011, 'content:tag:add'
  UNION ALL SELECT 2025, 2011, 'content:tag:edit'
  UNION ALL SELECT 2026, 2011, 'content:tag:remove'
) e ON m.menu_id = e.menu_id AND m.parent_id = e.parent_id AND m.perms = e.perms
WHERE m.menu_type = 'F' AND m.visible = '0' AND m.status = '0';

-- 3) 九个权限码不被其他菜单占用
INSERT INTO tmp_b_cloud_menu_verify
SELECT 'perms_not_leaked', IF(COUNT(*) = 0, 'PASS', 'FAIL'), CONCAT('leaked_rows=', COUNT(*))
FROM sys_menu
WHERE perms IN ('content:category:list', 'content:category:query', 'content:category:add', 'content:category:edit',
                'content:tag:list', 'content:tag:query', 'content:tag:add', 'content:tag:edit', 'content:tag:remove')
  AND menu_id NOT IN (2010, 2011, 2020, 2021, 2022, 2023, 2024, 2025, 2026);

-- 4) 内容管理目录下 C 页排序无重复
INSERT INTO tmp_b_cloud_menu_verify
SELECT 'sibling_order_unique', IF(COUNT(*) = 0, 'PASS', 'FAIL'), CONCAT('dup_order_groups=', COUNT(*))
FROM (
  SELECT order_num FROM sys_menu
  WHERE parent_id = 2000 AND menu_type = 'C' AND status = '0'
  GROUP BY order_num HAVING COUNT(*) > 1
) d;

-- 5) 两个前端组件标识各只登记一次
INSERT INTO tmp_b_cloud_menu_verify
SELECT 'component_unique', IF(COUNT(*) = 2, 'PASS', 'FAIL'), CONCAT('matched=', COUNT(*), '/2')
FROM sys_menu
WHERE menu_type = 'C' AND component IN ('content/category/index', 'content/tag/index');

-- 6) 超管授权 9 条齐全
INSERT INTO tmp_b_cloud_menu_verify
SELECT 'admin_granted', IF(COUNT(*) = 9, 'PASS', 'FAIL'), CONCAT('granted=', COUNT(*), '/9')
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
WHERE r.del_flag = '0' AND (r.role_key = 'admin' OR r.role_id = 1)
  AND rm.menu_id IN (2010, 2011, 2020, 2021, 2022, 2023, 2024, 2025, 2026);

SELECT check_id, result, detail FROM tmp_b_cloud_menu_verify ORDER BY check_id;

SELECT menu_id, menu_name, parent_id, order_num, path, component, menu_type, perms
FROM sys_menu
WHERE menu_id IN (2010, 2011, 2020, 2021, 2022, 2023, 2024, 2025, 2026)
ORDER BY menu_id;

SELECT CASE WHEN SUM(result = 'FAIL') = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result = 'FAIL'),
              ',pass_cnt=', SUM(result = 'PASS'),
              ',total_checks=', COUNT(*),
              ',failed_ids=', IFNULL(GROUP_CONCAT(IF(result = 'FAIL', check_id, NULL) ORDER BY check_id), '')) AS summary
FROM tmp_b_cloud_menu_verify;

DROP TEMPORARY TABLE IF EXISTS tmp_b_cloud_menu_verify;
