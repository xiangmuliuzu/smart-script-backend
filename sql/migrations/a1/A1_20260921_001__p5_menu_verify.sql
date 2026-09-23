-- =====================================================================
-- A1_20260921_001__p5_menu_verify.sql
-- 用途: A1 菜单种子落库校验（原 shared 区 v5 校验查询 + 失败即阻断的 SUMMARY）
-- 适用: MySQL 8.0.36
-- 前置: 已在目标库执行 A1_20260921_001__p5_menu_seed.sql
-- 事务: 只读
-- 可重复: 是（但只对 A1 阶段状态成立，见下）
-- 阶段说明:
--   本校验断言 A1 阶段的菜单形态：5100 的 path 仍是相对值 'dashboard'，
--   5142/5143/5150/5151/5152 仍挂在 parent_id=0。
--   PC_20260923_001__sidebar_ia.sql 会把这些菜单重挂到产品分组下并改成
--   绝对路径（/dashboard、/user、/risk、/ai/operations、/support/*）。
--   因此 PC 迁移之后请改用 PC_20260923_001__sidebar_ia_verify.sql，
--   本文件那时会因阶段不符而报 FAIL，属于预期行为。
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 一、证据输出（与 shared 区 v5 校验脚本一致，供人工核对与留档）
-- ---------------------------------------------------------------------

SELECT 'ROOTS' AS q,
       (SELECT COUNT(*) FROM sys_menu WHERE path='system' AND parent_id=0 AND menu_type='M') AS system_roots,
       (SELECT COUNT(*) FROM sys_menu WHERE path='monitor' AND parent_id=0 AND menu_type='M') AS monitor_roots;
-- 期望均为 1（标准库 1/2 或空库 A1，不得 ≥2）

SELECT 'A1SEED_COUNT' AS q, COUNT(*) AS cnt FROM sys_menu WHERE remark LIKE 'A1SEED%';

SELECT 'P0_LEAVES' AS q, menu_id, parent_id, path, component, remark
FROM sys_menu
WHERE remark LIKE 'A1SEED%'
  AND (
    path IN ('dashboard','user','risk','ai/operations','support/welfare','support/messages',
             'works','categories','ranking','external-video','overview','detail',
             'review','review-rules','ai-review','center','assets','seals',
             'ad-config','user-profile-rec')
    OR component IN ('dashboard/Dashboard','user/UserManage','risk/RiskManage',
                     'statistics/OperationOverview','copyright/ReviewWorkbench')
  )
ORDER BY path;

SELECT 'ROLE' AS q, role_id, role_key, remark FROM sys_role WHERE role_key='a1_operator';

SELECT 'ROLE_GRANTS' AS q, m.menu_id, m.path, m.component, m.remark
FROM sys_role r
JOIN sys_role_menu rm ON rm.role_id=r.role_id
JOIN sys_menu m ON m.menu_id=rm.menu_id
WHERE r.role_key='a1_operator'
ORDER BY m.menu_id;

SELECT 'G1_STD' AS q, component, COUNT(*) AS cnt
FROM sys_menu
WHERE component IN (
  'system/user/index','system/role/index','system/menu/index','system/post/index',
  'system/dict/index','system/config/index','monitor/operlog/index','monitor/logininfor/index'
)
GROUP BY component
ORDER BY component;
-- 标准库每 component 期望 cnt=1

SELECT 'DUP_COMPONENT' AS q, component, COUNT(*) AS cnt
FROM sys_menu
WHERE menu_type='C' AND component IS NOT NULL AND component<>''
GROUP BY component
HAVING cnt > 1
ORDER BY cnt DESC;
-- 信息项：common/ModuleScaffold ×9、copyright/ReviewWorkbench ×2 为设计如此（前端按 perms 分卡片）

-- ---------------------------------------------------------------------
-- 二、判定项（任一 FAIL 即阻断后续步骤）
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_a1_verify;
CREATE TEMPORARY TABLE tmp_a1_verify (
  check_id VARCHAR(48) NOT NULL,
  result   VARCHAR(8)  NOT NULL,
  detail   VARCHAR(255) NOT NULL,
  PRIMARY KEY (check_id)
) ENGINE=MEMORY;

-- 1) 根目录唯一：禁止出现平行 system/monitor 根
INSERT INTO tmp_a1_verify
SELECT 'system_root_unique',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('system_roots=', COUNT(*))
FROM sys_menu WHERE path='system' AND parent_id=0 AND menu_type='M';

INSERT INTO tmp_a1_verify
SELECT 'monitor_root_unique',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('monitor_roots=', COUNT(*))
FROM sys_menu WHERE path='monitor' AND parent_id=0 AND menu_type='M';

-- 2) 产品分组目录 7 个
INSERT INTO tmp_a1_verify
SELECT 'product_groups',
       IF(COUNT(*) = 7, 'PASS', 'FAIL'),
       CONCAT('groups=', COUNT(*), '/7')
FROM sys_menu
WHERE menu_id IN (5000,5001,5002,5003,5004,5005,5006)
  AND menu_type='M' AND parent_id=0;

-- 3) 产品叶子 25 个
INSERT INTO tmp_a1_verify
SELECT 'product_leaves',
       IF(COUNT(*) = 25, 'PASS', 'FAIL'),
       CONCAT('leaves=', COUNT(*), '/25')
FROM sys_menu
WHERE menu_id IN (5100,5101,5102,5103,5104,5110,5111,5120,5121,5122,5123,5124,5125,
                  5130,5131,5132,5133,5134,5140,5141,5142,5143,5150,5151,5152)
  AND menu_type='C';

-- 4) 种子总量
INSERT INTO tmp_a1_verify
SELECT 'seed_menu_count',
       IF(COUNT(*) = 32, 'PASS', 'FAIL'),
       CONCAT('a1seed_menus=', COUNT(*), '/32')
FROM sys_menu WHERE remark LIKE 'A1SEED%';

-- 5) 关键路径（A1 阶段形态：相对 path + 5142/5143/5150/5151/5152 为顶级叶子）
INSERT INTO tmp_a1_verify
SELECT 'key_paths',
       IF(COUNT(*) = 6, 'PASS', 'FAIL'),
       CONCAT('matched=', COUNT(*), '/6')
FROM sys_menu
WHERE (menu_id=5100 AND path='dashboard' AND parent_id=0)
   OR (menu_id=5142 AND path='user' AND parent_id=0 AND component='user/UserManage')
   OR (menu_id=5143 AND path='risk' AND parent_id=0)
   OR (menu_id=5150 AND path='ai/operations' AND parent_id=0)
   OR (menu_id=5151 AND path='support/welfare' AND parent_id=0)
   OR (menu_id=5152 AND path='support/messages' AND parent_id=0);

-- 6) 叶子必须都带权限标识（菜单可用性依赖）
INSERT INTO tmp_a1_verify
SELECT 'leaves_with_perms',
       IF(COUNT(*) = 25, 'PASS', 'FAIL'),
       CONCAT('with_perms=', COUNT(*), '/25')
FROM sys_menu
WHERE menu_id IN (5100,5101,5102,5103,5104,5110,5111,5120,5121,5122,5123,5124,5125,
                  5130,5131,5132,5133,5134,5140,5141,5142,5143,5150,5151,5152)
  AND perms IS NOT NULL AND perms <> '';

-- 7) 父子引用完整：不得留下指向不存在父菜单的行
INSERT INTO tmp_a1_verify
SELECT 'no_orphan_parent',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('orphan_rows=', COUNT(*))
FROM sys_menu m
LEFT JOIN sys_menu p ON p.menu_id = m.parent_id
WHERE m.parent_id <> 0 AND p.menu_id IS NULL;

-- 8) 受限运营角色存在且启用
INSERT INTO tmp_a1_verify
SELECT 'operator_role_present',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('a1_operator_rows=', COUNT(*))
FROM sys_role WHERE role_key='a1_operator' AND del_flag='0';

-- 9) 授权范围：只授 4 个 A1SEED 菜单（工作台、数据统计、数据总览、运营数据总览）
INSERT INTO tmp_a1_verify
SELECT 'operator_grants',
       IF(COUNT(*) = 4, 'PASS', 'FAIL'),
       CONCAT('grants=', COUNT(*), '/4')
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
WHERE r.role_key='a1_operator';

-- 10) 授权不得溢出到非 A1SEED 菜单
INSERT INTO tmp_a1_verify
SELECT 'operator_grants_scoped',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('off_seed_grants=', COUNT(*))
FROM sys_role_menu rm
JOIN sys_role r ON r.role_id = rm.role_id
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE r.role_key='a1_operator' AND m.remark NOT LIKE 'A1SEED%';

-- 11) 若依原生体系未被种子改写
INSERT INTO tmp_a1_verify
SELECT 'baseline_menu_100_intact',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('menu_100_matched=', COUNT(*))
FROM sys_menu
WHERE menu_id = 100 AND parent_id = 1 AND path = 'user'
  AND component = 'system/user/index' AND perms = 'system:user:list';

-- 12) 原生根目录未被种子占用
INSERT INTO tmp_a1_verify
SELECT 'baseline_roots_not_reused',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('reused_roots=', COUNT(*))
FROM sys_menu WHERE menu_id IN (1,2) AND remark LIKE 'A1SEED%';

-- 13) 种子未新建与若依原生组件重名的菜单（G1 去重守卫生效）
INSERT INTO tmp_a1_verify
SELECT 'seed_skipped_baseline_components',
       IF(COUNT(*) = 0, 'PASS', 'FAIL'),
       CONCAT('seeded_on_baseline_components=', COUNT(*))
FROM sys_menu
WHERE remark LIKE 'A1SEED%'
  AND component IN ('system/user/index','system/role/index','system/menu/index',
                    'system/post/index','system/dict/index','system/config/index',
                    'monitor/operlog/index','monitor/logininfor/index');

-- 14) 超级管理员角色存在（A4 授权判定依赖 role_id=1 / role_key='admin'）
INSERT INTO tmp_a1_verify
SELECT 'super_admin_role_present',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('admin_role_rows=', COUNT(*))
FROM sys_role WHERE role_id = 1 AND role_key = 'admin';

SELECT check_id, result, detail FROM tmp_a1_verify ORDER BY check_id;

SELECT CASE WHEN SUM(result = 'FAIL') = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       CONCAT('fail_cnt=', SUM(result = 'FAIL'),
              ',pass_cnt=', SUM(result = 'PASS'),
              ',total_checks=', COUNT(*),
              ',failed_ids=', IFNULL(GROUP_CONCAT(IF(result='FAIL', check_id, NULL) ORDER BY check_id), '')) AS summary
FROM tmp_a1_verify;

DROP TEMPORARY TABLE IF EXISTS tmp_a1_verify;
