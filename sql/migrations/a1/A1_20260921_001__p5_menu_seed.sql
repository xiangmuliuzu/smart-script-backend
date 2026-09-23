-- =====================================================================
-- A1_20260921_001__p5_menu_seed.sql
-- 用途: A1 产品菜单与受限运营角色种子（内容与 shared 区 G1 验收一致的 v4.1）
-- 适用: MySQL 8.0.36
-- 前置:
--   1) 若依基础结构已导入（sql/ry_20260320.sql）
--   2) 已执行 A2 迁移（sys_user 账号域归一）
--   3) 已有库升级时先执行 A1_20260921_001__p5_pregrant_snapshot.sql 并保存输出
-- 事务: 幂等；守卫基于 remark LIKE 'A1SEED%' 与 component 已存在判定
-- 可重复: 是
-- 回滚: U20260921_001__p5_menu_rollback.sql
--
-- 适配本仓 sys_menu：无 create_dept 列。
-- 基线 ry：system menu_id=1、monitor menu_id=2 已存在，禁止平行根目录。
-- 本脚本必须早于 A4_20260922_002__a4_permissions.sql：
--   A4 前置检查要求顶级 path='user' 目录唯一，该目录由本脚本 menu_id=5142 提供。
--
-- 有意保留的重复 component（前端按 perms 分卡片渲染，非缺陷）：
--   common/ModuleScaffold ×9、copyright/ReviewWorkbench ×2。
-- 单条 INSERT..SELECT 内 NOT EXISTS 依据语句开始前的表状态求值，
-- 因此同批同 component 的行会全部插入；这是设计如此，不是竞态缺陷。
-- =====================================================================
SET NAMES utf8mb4;

SET @p_system  := (SELECT menu_id FROM sys_menu WHERE path='system'  AND parent_id=0 AND menu_type='M' ORDER BY menu_id LIMIT 1);
SET @p_monitor := (SELECT menu_id FROM sys_menu WHERE path='monitor' AND parent_id=0 AND menu_type='M' ORDER BY menu_id LIMIT 1);

-- R1: 仅当整库无 system/monitor 根时才插入 A1 占位
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT 5010,'系统管理',0,90,'system',NULL,'',1,0,'M','0','0',NULL,'system','A1','A1SEED'
FROM DUAL
WHERE @p_system IS NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='system' AND parent_id=0 AND menu_type='M')
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=5010 AND (remark IS NULL OR remark NOT LIKE 'A1SEED%'))
ON DUPLICATE KEY UPDATE menu_name=IF(remark LIKE 'A1SEED%',VALUES(menu_name),menu_name), path=IF(remark LIKE 'A1SEED%',VALUES(path),path);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT 5008,'系统监控',0,91,'monitor',NULL,'',1,0,'M','0','0',NULL,'monitor','A1','A1SEED'
FROM DUAL
WHERE @p_monitor IS NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='monitor' AND parent_id=0 AND menu_type='M')
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=5008 AND (remark IS NULL OR remark NOT LIKE 'A1SEED%'))
ON DUPLICATE KEY UPDATE menu_name=IF(remark LIKE 'A1SEED%',VALUES(menu_name),menu_name), path=IF(remark LIKE 'A1SEED%',VALUES(path),path);

-- 产品分组目录
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT t.menu_id, t.menu_name, t.parent_id, t.order_num, t.path, t.component, t.q, t.is_frame, t.is_cache, t.menu_type, t.visible, t.status, t.perms, t.icon, t.create_by, t.remark
FROM (
  SELECT 5000 AS menu_id,'工作台' AS menu_name,0 AS parent_id,1 AS order_num,'workspace' AS path,NULL AS component,'' AS q,1 AS is_frame,0 AS is_cache,'M' AS menu_type,'0' AS visible,'0' AS status,NULL AS perms,'dashboard' AS icon,'A1' AS create_by,'A1SEED' AS remark
  UNION ALL SELECT 5001,'内容与作品',0,2,'content',NULL,'',1,0,'M','0','0',NULL,'Document','A1','A1SEED'
  UNION ALL SELECT 5002,'数据统计',0,3,'statistics',NULL,'',1,0,'M','0','0',NULL,'Chart','A1','A1SEED'
  UNION ALL SELECT 5003,'版权审核管理',0,4,'copyright',NULL,'',1,0,'M','0','0',NULL,'Stamp','A1','A1SEED'
  UNION ALL SELECT 5004,'交易商务管理',0,5,'trade',NULL,'',1,0,'M','0','0',NULL,'Sell','A1','A1SEED'
  UNION ALL SELECT 5005,'平台运维管理',0,6,'operation',NULL,'',1,0,'M','0','0',NULL,'Operation','A1','A1SEED'
  UNION ALL SELECT 5006,'AI创作与福利',0,7,'ai-group',NULL,'',1,0,'M','0','0',NULL,'Magic','A1','A1SEED'
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=t.menu_id AND (remark IS NULL OR remark NOT LIKE 'A1SEED%'))
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE path=t.path AND parent_id=0 AND menu_type='M' AND menu_id<>t.menu_id)
ON DUPLICATE KEY UPDATE
  menu_name=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(menu_name),sys_menu.menu_name),
  path=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(path),sys_menu.path),
  visible=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(visible),sys_menu.visible),
  status=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(status),sys_menu.status);

SET @p_system  := (SELECT menu_id FROM sys_menu WHERE path='system'  AND parent_id=0 AND menu_type='M' ORDER BY menu_id LIMIT 1);
SET @p_monitor := (SELECT menu_id FROM sys_menu WHERE path='monitor' AND parent_id=0 AND menu_type='M' ORDER BY menu_id LIMIT 1);
SET @p_workspace := (SELECT menu_id FROM sys_menu WHERE path='workspace' AND parent_id=0 AND menu_type='M' AND remark LIKE 'A1SEED%' ORDER BY menu_id LIMIT 1);
SET @p_content   := (SELECT menu_id FROM sys_menu WHERE path='content' AND parent_id=0 AND menu_type='M' AND remark LIKE 'A1SEED%' ORDER BY menu_id LIMIT 1);
SET @p_stats     := (SELECT menu_id FROM sys_menu WHERE path='statistics' AND parent_id=0 AND menu_type='M' AND remark LIKE 'A1SEED%' ORDER BY menu_id LIMIT 1);
SET @p_copy      := (SELECT menu_id FROM sys_menu WHERE path='copyright' AND parent_id=0 AND menu_type='M' AND remark LIKE 'A1SEED%' ORDER BY menu_id LIMIT 1);
SET @p_trade     := (SELECT menu_id FROM sys_menu WHERE path='trade' AND parent_id=0 AND menu_type='M' AND remark LIKE 'A1SEED%' ORDER BY menu_id LIMIT 1);
SET @p_ops       := (SELECT menu_id FROM sys_menu WHERE path='operation' AND parent_id=0 AND menu_type='M' AND remark LIKE 'A1SEED%' ORDER BY menu_id LIMIT 1);
SET @p_ai        := (SELECT menu_id FROM sys_menu WHERE path='ai-group' AND parent_id=0 AND menu_type='M' AND remark LIKE 'A1SEED%' ORDER BY menu_id LIMIT 1);

-- 产品叶子
-- 产品叶子：P0 单级路径使用 parent_id=0 + path=dashboard 等，确保 getRouters 生成 /dashboard
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT t.menu_id, t.menu_name, t.parent_id, t.order_num, t.path, t.component, '', 1, 0, 'C', '0', '0', t.perms, t.icon, 'A1', t.remark
FROM (
  -- 根级：/dashboard
  SELECT 5100 AS menu_id,'数据总览' AS menu_name,0 AS parent_id,1 AS order_num,'dashboard' AS path,'dashboard/Dashboard' AS component,'smartscript:dashboard:view' AS perms,'DataLine' AS icon,'A1SEED' AS remark
  UNION ALL SELECT 5101,'作品内容管理',@p_content,1,'works','common/ModuleScaffold','smartscript:content:works','Document','A1SEED'
  UNION ALL SELECT 5102,'分类与标签',@p_content,2,'categories','common/ModuleScaffold','smartscript:content:category','Collection','A1SEED'
  UNION ALL SELECT 5103,'排行榜管理',@p_content,3,'ranking','common/ModuleScaffold','smartscript:content:ranking','Trophy','A1SEED'
  UNION ALL SELECT 5104,'外部漫剧发行',@p_content,4,'external-video','operation/ShortDrama','smartscript:content:video','VideoPlay','A1SEED'
  UNION ALL SELECT 5110,'运营数据总览',@p_stats,1,'overview','statistics/OperationOverview','smartscript:stats:overview','DataLine','A1SEED'
  UNION ALL SELECT 5111,'明细数据查询',@p_stats,2,'detail','statistics/DetailQuery','smartscript:stats:detail','Search','A1SEED'
  UNION ALL SELECT 5120,'作品审核工作台',@p_copy,1,'review','copyright/ReviewWorkbench','smartscript:copyright:review','Stamp','A1SEED'
  UNION ALL SELECT 5121,'AI审核规则配置',@p_copy,2,'review-rules','copyright/AiReviewRules','smartscript:copyright:reviewRules','Stamp','A1SEED'
  UNION ALL SELECT 5125,'AI初审+人工复核',@p_copy,3,'ai-review','copyright/ReviewWorkbench','smartscript:copyright:aiReview','Stamp','A1SEED'
  UNION ALL SELECT 5122,'版权中心对接',@p_copy,4,'center','copyright/CopyrightCenter','smartscript:copyright:center','Link','A1SEED'
  UNION ALL SELECT 5123,'版权资产管理',@p_copy,5,'assets','copyright/CopyrightAssets','smartscript:copyright:assets','Folder','A1SEED'
  UNION ALL SELECT 5124,'印章审核',@p_copy,6,'seals','common/ModuleScaffold','smartscript:copyright:seals','Stamp','A1SEED'
  UNION ALL SELECT 5130,'交易作品管理',@p_trade,1,'works','trade/TradeWorks','smartscript:trade:works','Sell','A1SEED'
  UNION ALL SELECT 5131,'授权订单管理',@p_trade,2,'orders','trade/AuthOrders','smartscript:trade:orders','Sell','A1SEED'
  UNION ALL SELECT 5132,'合作方管理',@p_trade,3,'partners','trade/Partners','smartscript:trade:partners','User','A1SEED'
  UNION ALL SELECT 5133,'询盘与报价',@p_trade,4,'inquiries','common/ModuleScaffold','smartscript:trade:inquiries','Chat','A1SEED'
  UNION ALL SELECT 5134,'合同与结算',@p_trade,5,'contracts','common/ModuleScaffold','smartscript:trade:contracts','Document','A1SEED'
  UNION ALL SELECT 5140,'广告运营配置',@p_ops,1,'ad-config','operation/AdConfig','smartscript:ops:adConfig','Picture','A1SEED'
  UNION ALL SELECT 5141,'用户画像与推荐配置',@p_ops,2,'user-profile-rec','operation/UserProfileRec','smartscript:ops:userProfile','Operation','A1SEED'
  UNION ALL SELECT 5142,'用户与创作者管理',0,3,'user','user/UserManage','smartscript:ops:creatorUser','User','A1SEED-product-not-system-user'
  UNION ALL SELECT 5143,'全局风控管理',0,4,'risk','risk/RiskManage','smartscript:ops:risk','Warning','A1SEED'
  UNION ALL SELECT 5150,'AI创作与次数',0,5,'ai/operations','common/ModuleScaffold','smartscript:ai:operations','Magic','A1SEED'
  UNION ALL SELECT 5151,'福利与积分配置',0,6,'support/welfare','common/ModuleScaffold','smartscript:support:welfare','Present','A1SEED'
  UNION ALL SELECT 5152,'消息与公告',0,7,'support/messages','common/ModuleScaffold','smartscript:support:messages','Bell','A1SEED'
) t
WHERE t.parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id=t.menu_id AND (remark IS NULL OR remark NOT LIKE 'A1SEED%'))
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.component=t.component AND m.component IS NOT NULL AND m.component<>'' AND m.menu_type='C' AND m.menu_id<>t.menu_id)
ON DUPLICATE KEY UPDATE
  menu_name=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(menu_name),sys_menu.menu_name),
  parent_id=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(parent_id),sys_menu.parent_id),
  path=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(path),sys_menu.path),
  component=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(component),sys_menu.component),
  perms=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(perms),sys_menu.perms),
  order_num=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(order_num),sys_menu.order_num),
  visible=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(visible),sys_menu.visible),
  status=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(status),sys_menu.status);

-- G1：标准库已有 component 时跳过
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, remark)
SELECT t.menu_id, t.menu_name, t.parent_id, t.order_num, t.path, t.component, '', 1, 0, 'C', '0', '0', t.perms, t.icon, 'A1', 'A1SEED'
FROM (
  SELECT 5201 AS menu_id,'用户管理' AS menu_name,@p_system AS parent_id,1 AS order_num,'user' AS path,'system/user/index' AS component,'system:user:list' AS perms,'user' AS icon
  UNION ALL SELECT 5202,'角色管理',@p_system,2,'role','system/role/index','system:role:list','peoples'
  UNION ALL SELECT 5203,'菜单管理',@p_system,3,'menu','system/menu/index','system:menu:list','tree-table'
  UNION ALL SELECT 5204,'岗位管理',@p_system,4,'post','system/post/index','system:post:list','post'
  UNION ALL SELECT 5205,'字典管理',@p_system,5,'dict','system/dict/index','system:dict:list','dict'
  UNION ALL SELECT 5206,'参数设置',@p_system,6,'config','system/config/index','system:config:list','edit'
  UNION ALL SELECT 5210,'操作日志',@p_monitor,1,'operlog','monitor/operlog/index','monitor:operlog:list','form'
  UNION ALL SELECT 5211,'登录日志',@p_monitor,2,'logininfor','monitor/logininfor/index','monitor:logininfor:list','logininfor'
) t
WHERE t.parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.component = t.component)
  AND NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_id=t.menu_id AND (m.remark IS NULL OR m.remark NOT LIKE 'A1SEED%'))
ON DUPLICATE KEY UPDATE
  parent_id=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(parent_id),sys_menu.parent_id),
  path=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(path),sys_menu.path),
  component=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(component),sys_menu.component),
  perms=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(perms),sys_menu.perms),
  visible=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(visible),sys_menu.visible),
  status=IF(sys_menu.remark LIKE 'A1SEED%',VALUES(status),sys_menu.status);

-- 角色：只新建，不改已有 remark
INSERT INTO sys_role (role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly, status, del_flag, create_by, remark)
SELECT 'A1受限运营', 'a1_operator', 50, '2', 1, 1, '0', '0', 'A1', 'A1SEED-CREATED'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key='a1_operator' AND del_flag='0');

SET @a1_role_id := (
  SELECT role_id FROM sys_role
  WHERE role_key='a1_operator' AND del_flag='0'
  ORDER BY (remark = 'A1SEED-CREATED') DESC, role_id
  LIMIT 1
);

-- 自动授权：仅 A1SEED 菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT @a1_role_id, m.menu_id
FROM sys_menu m
WHERE @a1_role_id IS NOT NULL
  AND m.remark LIKE 'A1SEED%'
  AND m.menu_type IN ('M', 'C')
  AND (
        m.component IN ('dashboard/Dashboard', 'statistics/OperationOverview')
     OR (m.path IN ('workspace', 'statistics') AND m.parent_id=0 AND m.menu_type='M')
  )
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=@a1_role_id AND rm.menu_id=m.menu_id);
