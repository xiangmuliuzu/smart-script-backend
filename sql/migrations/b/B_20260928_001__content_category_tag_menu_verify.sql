-- =====================================================================
-- B_20260928_001__content_category_tag_menu_verify.sql
-- 用途: B 模块分类/标签菜单迁移的最终只读校验
-- 闸门: 末尾必须输出 “PASS  fail_cnt=0,”（init-database 的 summary 闸门）
-- 可重复执行；不写任何数据。
-- =====================================================================
SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS tmp_b_content_menu_verify;
CREATE TEMPORARY TABLE tmp_b_content_menu_verify (
  check_id VARCHAR(64) NOT NULL PRIMARY KEY,
  result   CHAR(4)     NOT NULL,
  detail   VARCHAR(255) NOT NULL
) ENGINE=MEMORY;

-- 1) 5102 已由占位骨架改写为「分类管理」C 页
INSERT INTO tmp_b_content_menu_verify
SELECT 'category_page_ready',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 5102 AND parent_id = 5001 AND menu_type = 'C'
  AND visible = '0' AND status = '0'
  AND menu_name = '分类管理'
  AND path = 'category'
  AND component = 'content/category/index'
  AND perms = 'content:category:list';

-- 2) 5105「标签管理」C 页已新增
INSERT INTO tmp_b_content_menu_verify
SELECT 'tag_page_ready',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 5105 AND parent_id = 5001 AND menu_type = 'C'
  AND visible = '0' AND status = '0'
  AND menu_name = '标签管理'
  AND path = 'tag'
  AND component = 'content/tag/index'
  AND perms = 'content:tag:list';

-- 3) 七个 F 按钮父子挂接与权限码全部正确
INSERT INTO tmp_b_content_menu_verify
SELECT 'buttons_ready',
       IF(COUNT(*) = 7, 'PASS', 'FAIL'),
       CONCAT('matched=', COUNT(*), '/7')
FROM sys_menu m
JOIN (
  SELECT 5160 AS menu_id, 5102 AS parent_id, 'content:category:query' AS perms
  UNION ALL SELECT 5161, 5102, 'content:category:add'
  UNION ALL SELECT 5162, 5102, 'content:category:edit'
  UNION ALL SELECT 5163, 5105, 'content:tag:query'
  UNION ALL SELECT 5164, 5105, 'content:tag:add'
  UNION ALL SELECT 5165, 5105, 'content:tag:edit'
  UNION ALL SELECT 5166, 5105, 'content:tag:remove'
) e
  ON m.menu_id = e.menu_id
 AND m.parent_id = e.parent_id
 AND m.perms = e.perms
WHERE m.menu_type = 'F' AND m.visible = '0' AND m.status = '0';

-- 4) 九个权限码不得被本迁移以外的菜单占用
INSERT INTO tmp_b_content_menu_verify
SELECT 'perms_not_leaked',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('leaked_rows=', COUNT(*))
FROM sys_menu
WHERE perms IN ('content:category:list', 'content:category:query',
                'content:category:add', 'content:category:edit',
                'content:tag:list', 'content:tag:query',
                'content:tag:add', 'content:tag:edit', 'content:tag:remove')
  AND menu_id NOT IN (5102, 5105, 5160, 5161, 5162, 5163, 5164, 5165, 5166);

-- 5) 5102 占位骨架痕迹清零
INSERT INTO tmp_b_content_menu_verify
SELECT 'placeholder_gone',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('residue_rows=', COUNT(*))
FROM sys_menu
WHERE menu_id = 5102
  AND (component = 'common/ModuleScaffold'
       OR perms = 'smartscript:content:category'
       OR path = 'categories'
       OR menu_name = '分类与标签');

-- 6) 内容与作品目录下 C 页排序无重复
INSERT INTO tmp_b_content_menu_verify
SELECT 'sibling_order_unique',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('dup_order_groups=', COUNT(*))
FROM (
  SELECT order_num
  FROM sys_menu
  WHERE parent_id = 5001 AND menu_type = 'C' AND status = '0'
  GROUP BY order_num
  HAVING COUNT(*) > 1
) d;

-- 7) 两个前端组件标识各只登记一次
INSERT INTO tmp_b_content_menu_verify
SELECT 'component_unique',
       IF(COUNT(*) = 2, 'PASS', 'FAIL'),
       CONCAT('matched=', COUNT(*), '/2')
FROM sys_menu
WHERE menu_type = 'C'
  AND component IN ('content/category/index', 'content/tag/index');

-- 8) 超级管理员角色已授予九个菜单（页面 + 按钮）
INSERT INTO tmp_b_content_menu_verify
SELECT 'admin_granted',
       IF(COUNT(*) = 9, 'PASS', 'FAIL'),
       CONCAT('granted=', COUNT(*), '/9')
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
WHERE r.del_flag = '0'
  AND (r.role_key = 'admin' OR r.role_id = 1)
  AND rm.menu_id IN (5102, 5105, 5160, 5161, 5162, 5163, 5164, 5165, 5166);

-- 9) 内容与作品目录子菜单顺序符合：作品 → 分类 → 标签 → 排行榜 → 外部漫剧
INSERT INTO tmp_b_content_menu_verify
SELECT 'content_sibling_order',
       IF(COUNT(*) = 5, 'PASS', 'FAIL'),
       CONCAT('matched=', COUNT(*), '/5')
FROM sys_menu
WHERE parent_id = 5001 AND menu_type = 'C'
  AND ((menu_id = 5101 AND order_num = 1)
    OR (menu_id = 5102 AND order_num = 2)
    OR (menu_id = 5105 AND order_num = 3)
    OR (menu_id = 5103 AND order_num = 4)
    OR (menu_id = 5104 AND order_num = 5));

SELECT check_id, result, detail FROM tmp_b_content_menu_verify ORDER BY check_id;

-- ---------------------------------------------------------------------
-- 证据输出：迁移后的 9 个菜单与 5001 子菜单树
-- ---------------------------------------------------------------------
SELECT menu_id, menu_name, parent_id, order_num, path, component, menu_type, perms
FROM sys_menu
WHERE menu_id IN (5102, 5105, 5160, 5161, 5162, 5163, 5164, 5165, 5166)
ORDER BY menu_id;

SELECT 'B_CONTENT_GROUP_CHILDREN' AS section,
       (SELECT GROUP_CONCAT(CONCAT(order_num, ':', menu_id, ':', menu_name) ORDER BY order_num)
          FROM sys_menu WHERE parent_id = 5001 AND menu_type = 'C') AS children;

SELECT CASE WHEN SUM(result = 'FAIL') = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result = 'FAIL'),
              ',pass_cnt=', SUM(result = 'PASS'),
              ',total_checks=', COUNT(*),
              ',failed_ids=', IFNULL(GROUP_CONCAT(IF(result = 'FAIL', check_id, NULL) ORDER BY check_id), '')) AS summary
FROM tmp_b_content_menu_verify;

DROP TEMPORARY TABLE IF EXISTS tmp_b_content_menu_verify;
