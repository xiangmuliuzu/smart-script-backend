-- =====================================================================
-- B 模块内容 · 作品推广（付费推荐系统）
--
-- 内容：
--   1. 新建 sys_promotion_slot / sys_promotion_application / sandbox_alipay_promotion_payment
--   2. 用旧表 t_work_recommend_slot 的 30 行价格做位次种子（banner 5 / hot 20 / search 5）
--   3. 把旧 sys_work.ext_json 中的推荐数据迁移为 admin_grant 投放记录（不收费）
--   4. 菜单改造：5411「Banner管理」→「作品推广」，清理孤儿按钮 2035~2037，补角色关联
--   5. 修复 sys_banner 中带 host 的绝对图片地址（统一截取 /profile/ 之后的部分）
--   6. 清理旧推荐扩展字段，删除废弃表 t_work_recommend_slot / t_work_recommend_apply
--
-- 幂等：DDL 均为 IF NOT EXISTS；种子用 ON DUPLICATE KEY；申请用 apply_no 唯一键防重；
--   ext_json 清理对已清理行无变化。末行 verify 闸门要求 PASS fail_cnt=0。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261104_001__promotion_system.sql"
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 坑位配置表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_promotion_slot (
  slot_id        BIGINT       NOT NULL AUTO_INCREMENT,
  slot_type      VARCHAR(20)  NOT NULL COMMENT '资源位类型：banner/search/hot',
  slot_no        INT          NOT NULL COMMENT '位次（1..N）',
  price_per_day  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '单价（元/天）',
  enabled        TINYINT      NOT NULL DEFAULT 1 COMMENT '是否接受申请：1 是 0 否',
  remark         VARCHAR(255) DEFAULT NULL COMMENT '位次说明',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (slot_id),
  UNIQUE KEY uk_promotion_slot (slot_type, slot_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品推广坑位配置';

-- ---------------------------------------------------------------------
-- 2. 推广申请 / 投放记录表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_promotion_application (
  apply_id       BIGINT       NOT NULL AUTO_INCREMENT,
  apply_no       VARCHAR(32)  NOT NULL COMMENT '申请单号',
  work_id        BIGINT       NOT NULL,
  author_id      BIGINT       NOT NULL,
  slot_type      VARCHAR(20)  NOT NULL COMMENT 'banner/search/hot',
  slot_no        INT          NOT NULL,
  start_time     DATETIME     NOT NULL,
  end_time       DATETIME     NOT NULL,
  days           INT          NOT NULL,
  amount         DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  source         VARCHAR(20)  NOT NULL DEFAULT 'author_paid' COMMENT 'author_paid 作者付费 / admin_grant 免费直上',
  status         VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected/paid/cancelled',
  pay_status     VARCHAR(20)  NOT NULL DEFAULT 'unpaid' COMMENT 'unpaid/paid/closed',
  pay_time       DATETIME     DEFAULT NULL,
  banner_id      BIGINT       DEFAULT NULL COMMENT '轮播图素材ID（slot_type=banner 时）',
  reject_reason  VARCHAR(500) DEFAULT NULL,
  lock_expire_at DATETIME     DEFAULT NULL COMMENT '审核通过后锁坑截止时间',
  audit_by       VARCHAR(64)  DEFAULT NULL,
  audit_time     DATETIME     DEFAULT NULL,
  remark         VARCHAR(500) DEFAULT NULL,
  create_by      VARCHAR(64)  DEFAULT NULL,
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by      VARCHAR(64)  DEFAULT NULL,
  update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (apply_id),
  UNIQUE KEY uk_promotion_apply_no (apply_no),
  KEY idx_promotion_slot_window (slot_type, slot_no, start_time, end_time),
  KEY idx_promotion_author (author_id, status),
  KEY idx_promotion_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品推广申请与投放记录';

-- ---------------------------------------------------------------------
-- 3. 支付宝沙箱 · 推广单支付记录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sandbox_alipay_promotion_payment (
  apply_id      BIGINT       NOT NULL,
  out_trade_no  VARCHAR(64)  NOT NULL,
  buyer_id      BIGINT       NOT NULL,
  amount        DECIMAL(12,2) NOT NULL,
  trade_no      VARCHAR(64)  DEFAULT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (apply_id),
  UNIQUE KEY uk_sandbox_alipay_promotion_trade (out_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付宝沙箱推广单支付记录';

-- ---------------------------------------------------------------------
-- 4. 位次价格种子（来源：旧表 t_work_recommend_slot 的 30 行）
-- ---------------------------------------------------------------------
INSERT INTO sys_promotion_slot (slot_type, slot_no, price_per_day, enabled, remark) VALUES
  ('banner', 1, 800.00, 1, '首页轮播首位，曝光最高'),
  ('banner', 2, 500.00, 1, '首页轮播次位'),
  ('banner', 3, 500.00, 1, '首页轮播第三位'),
  ('banner', 4, 200.00, 1, '首页轮播尾部'),
  ('banner', 5, 200.00, 1, '首页轮播尾部'),
  ('hot', 1, 100.00, 1, '首页热门精选首位，曝光最高'),
  ('hot', 2, 70.00, 1, '首页热门精选可见位'),
  ('hot', 3, 70.00, 1, '首页热门精选可见位'),
  ('hot', 4, 40.00, 1, '首页热门精选可见末档'),
  ('hot', 5, 40.00, 1, '首页热门精选可见末档'),
  ('hot', 6, 40.00, 1, '首页热门精选可见末档'),
  ('hot', 7, 20.00, 1, '点击「更多」后可见'),
  ('hot', 8, 20.00, 1, '点击「更多」后可见'),
  ('hot', 9, 20.00, 1, '点击「更多」后可见'),
  ('hot', 10, 20.00, 1, '点击「更多」后可见'),
  ('hot', 11, 20.00, 1, '点击「更多」后可见'),
  ('hot', 12, 20.00, 1, '点击「更多」后可见'),
  ('hot', 13, 20.00, 1, '点击「更多」后可见'),
  ('hot', 14, 20.00, 1, '点击「更多」后可见'),
  ('hot', 15, 20.00, 1, '点击「更多」后可见'),
  ('hot', 16, 20.00, 1, '点击「更多」后可见'),
  ('hot', 17, 20.00, 1, '点击「更多」后可见'),
  ('hot', 18, 20.00, 1, '点击「更多」后可见'),
  ('hot', 19, 20.00, 1, '点击「更多」后可见'),
  ('hot', 20, 20.00, 1, '点击「更多」后可见'),
  ('search', 1, 150.00, 1, '搜索结果首屏'),
  ('search', 2, 150.00, 1, '搜索结果首屏'),
  ('search', 3, 150.00, 1, '搜索结果首屏'),
  ('search', 4, 60.00, 1, '搜索页次屏'),
  ('search', 5, 60.00, 1, '搜索页次屏')
ON DUPLICATE KEY UPDATE price_per_day = VALUES(price_per_day), remark = VALUES(remark);

-- ---------------------------------------------------------------------
-- 5. 旧推荐数据迁移为 admin_grant 投放记录
--    首页热门：recommendStatus=home_hot 且 recommendWeight 1..20
--    搜索页：showScope 含 search 且 searchWeight 1..5
--    已过期的记录也保留（end_time < now，前端按时间窗派生为「已结束」）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_promotion_application
  (apply_no, work_id, author_id, slot_type, slot_no, start_time, end_time, days, amount,
   source, status, pay_status, pay_time, audit_by, audit_time, remark, create_by)
SELECT CONCAT('LEGACY-', w.work_id, '-HOT'), w.work_id, w.author_id, 'hot',
       CAST(JSON_EXTRACT(w.ext_json, '$.recommendWeight') AS UNSIGNED),
       DATE_SUB(NOW(), INTERVAL 30 DAY),
       COALESCE(STR_TO_DATE(JSON_UNQUOTE(JSON_EXTRACT(w.ext_json, '$.recommendExpireAt')), '%Y-%m-%d %H:%i:%s'),
                DATE_ADD(NOW(), INTERVAL 365 DAY)),
       30, 0.00, 'admin_grant', 'paid', 'paid', NOW(), 'migration', NOW(),
       '历史首页热门推荐迁移', 'migration'
FROM sys_work w
WHERE w.is_deleted = 0
  AND JSON_UNQUOTE(JSON_EXTRACT(w.ext_json, '$.recommendStatus')) = 'home_hot'
  AND CAST(JSON_EXTRACT(w.ext_json, '$.recommendWeight') AS UNSIGNED) BETWEEN 1 AND 20
  AND JSON_EXTRACT(w.ext_json, '$.recommendWeight') IS NOT NULL;

INSERT IGNORE INTO sys_promotion_application
  (apply_no, work_id, author_id, slot_type, slot_no, start_time, end_time, days, amount,
   source, status, pay_status, pay_time, audit_by, audit_time, remark, create_by)
SELECT CONCAT('LEGACY-', w.work_id, '-SEARCH'), w.work_id, w.author_id, 'search',
       CAST(JSON_EXTRACT(w.ext_json, '$.searchWeight') AS UNSIGNED),
       DATE_SUB(NOW(), INTERVAL 30 DAY),
       COALESCE(STR_TO_DATE(JSON_UNQUOTE(JSON_EXTRACT(w.ext_json, '$.recommendExpireAt')), '%Y-%m-%d %H:%i:%s'),
                DATE_ADD(NOW(), INTERVAL 365 DAY)),
       30, 0.00, 'admin_grant', 'paid', 'paid', NOW(), 'migration', NOW(),
       '历史搜索页推荐迁移', 'migration'
FROM sys_work w
WHERE w.is_deleted = 0
  AND JSON_CONTAINS(w.ext_json, '"search"', '$.showScope')
  AND CAST(JSON_EXTRACT(w.ext_json, '$.searchWeight') AS UNSIGNED) BETWEEN 1 AND 5
  AND JSON_EXTRACT(w.ext_json, '$.searchWeight') IS NOT NULL;

-- ---------------------------------------------------------------------
-- 6. 菜单改造
-- ---------------------------------------------------------------------
UPDATE sys_menu
SET menu_name = '作品推广',
    path = 'promotion',
    component = 'content/promotion/index',
    perms = 'content:promotion:list',
    icon = 'Star',
    order_num = 4,
    visible = '0',
    status = '0'
WHERE menu_id = 5411;

-- 清理父菜单已不存在的孤儿按钮（Banner 查询/新增/编辑）
DELETE FROM sys_role_menu WHERE menu_id IN (2035, 2036, 2037);
DELETE FROM sys_menu WHERE menu_id IN (2035, 2036, 2037) AND menu_type = 'F';

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
VALUES
  (5412, '推广查询', 5411, 1, '#', NULL, 1, 0, 'F', '0', '0', 'content:promotion:query', '#', 'migration', NOW(), '作品推广查询'),
  (5413, '推广新增', 5411, 2, '#', NULL, 1, 0, 'F', '0', '0', 'content:promotion:add', '#', 'migration', NOW(), '新增推广申请/轮播图'),
  (5414, '推广修改', 5411, 3, '#', NULL, 1, 0, 'F', '0', '0', 'content:promotion:edit', '#', 'migration', NOW(), '编辑推广申请/轮播图'),
  (5415, '推广删除', 5411, 4, '#', NULL, 1, 0, 'F', '0', '0', 'content:promotion:remove', '#', 'migration', NOW(), '删除推广申请/轮播图'),
  (5416, '推广审核', 5411, 5, '#', NULL, 1, 0, 'F', '0', '0', 'content:promotion:audit', '#', 'migration', NOW(), '审核推广申请'),
  (5417, '价格配置', 5411, 6, '#', NULL, 1, 0, 'F', '0', '0', 'content:promotion:price', '#', 'migration', NOW(), '位次单价与启停配置'),
  (5418, '免费上推荐', 5411, 7, '#', NULL, 1, 0, 'F', '0', '0', 'content:promotion:grant', '#', 'migration', NOW(), '管理员免费直上推荐')
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name), perms = VALUES(perms), parent_id = VALUES(parent_id),
  order_num = VALUES(order_num), remark = VALUES(remark);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.menu_id BETWEEN 5411 AND 5418
WHERE r.role_id IN (1, 101);

-- ---------------------------------------------------------------------
-- 7. Banner 图片地址归一：去掉 host，只保留 /profile/ 之后的部分
-- ---------------------------------------------------------------------
UPDATE sys_banner
SET image_url = CONCAT('/profile/', SUBSTRING_INDEX(image_url, '/profile/', -1))
WHERE image_url LIKE 'http%://%/profile/%';

-- ---------------------------------------------------------------------
-- 8. 清理旧推荐扩展字段（新链路不再读取）
-- ---------------------------------------------------------------------
UPDATE sys_work
SET ext_json = JSON_REMOVE(ext_json,
      '$.recommendStatus', '$.recommendWeight', '$.searchWeight',
      '$.recommendExpireAt', '$.showScope')
WHERE ext_json IS NOT NULL
  AND (JSON_EXTRACT(ext_json, '$.recommendStatus') IS NOT NULL
       OR JSON_EXTRACT(ext_json, '$.recommendWeight') IS NOT NULL
       OR JSON_EXTRACT(ext_json, '$.searchWeight') IS NOT NULL
       OR JSON_EXTRACT(ext_json, '$.recommendExpireAt') IS NOT NULL
       OR JSON_EXTRACT(ext_json, '$.showScope') IS NOT NULL);

-- ---------------------------------------------------------------------
-- 9. 删除废弃的旧推荐表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_work_recommend_slot;
DROP TABLE IF EXISTS t_work_recommend_apply;

-- ---------------------------------------------------------------------
-- 10. verify 闸门：任一不符则 fail_cnt > 0
-- ---------------------------------------------------------------------
SET @fail = 0;

SET @fail = @fail + IF((SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_promotion_slot') = 1, 0, 1);
SET @fail = @fail + IF((SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_promotion_application') = 1, 0, 1);
SET @fail = @fail + IF((SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sandbox_alipay_promotion_payment') = 1, 0, 1);

SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_promotion_slot) = 30, 0, 1);
SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_promotion_slot WHERE slot_type = 'banner') = 5, 0, 1);
SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_promotion_slot WHERE slot_type = 'hot') = 20, 0, 1);
SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_promotion_slot WHERE slot_type = 'search') = 5, 0, 1);

SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_menu WHERE menu_id = 5411
    AND menu_name = '作品推广' AND component = 'content/promotion/index'
    AND perms = 'content:promotion:list') = 1, 0, 1);
SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_menu WHERE menu_id BETWEEN 5412 AND 5418) = 7, 0, 1);
SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_menu WHERE menu_id IN (2035, 2036, 2037)) = 0, 0, 1);
SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 1 AND menu_id BETWEEN 5411 AND 5418) = 8, 0, 1);
SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_role_menu WHERE role_id = 101 AND menu_id BETWEEN 5411 AND 5418) = 8, 0, 1);

SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_banner WHERE image_url LIKE 'http%://%/profile/%') = 0, 0, 1);
SET @fail = @fail + IF((SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name IN ('t_work_recommend_slot', 't_work_recommend_apply')) = 0, 0, 1);
SET @fail = @fail + IF((SELECT COUNT(*) FROM sys_work WHERE ext_json IS NOT NULL
    AND (JSON_EXTRACT(ext_json, '$.recommendStatus') IS NOT NULL
         OR JSON_EXTRACT(ext_json, '$.recommendWeight') IS NOT NULL
         OR JSON_EXTRACT(ext_json, '$.searchWeight') IS NOT NULL
         OR JSON_EXTRACT(ext_json, '$.recommendExpireAt') IS NOT NULL)) = 0, 0, 1);

SELECT IF(@fail = 0, 'PASS', 'FAIL') AS result, @fail AS fail_cnt;
