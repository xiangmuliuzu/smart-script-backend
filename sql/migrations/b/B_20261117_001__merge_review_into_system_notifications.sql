-- =====================================================================
-- B_20261117_001：消息类型「审核」并入「系统」，为「关注」腾出分类位
--
-- 需求：APP 消息中心原「审核」分类并入「系统」，原位置改为「关注」
--       （关注的创作者发布新作品）。
-- 口径：
--   * 存量 type='REVIEW' 的通知统一改为 SYSTEM：
--       实名认证/创作者申请审核结果本身就是系统消息，改后「全部」「系统」
--       两个筛选项都能看到，不会因分类移除而丢消息。
--   * 旧「审核通知」偏好行（type='REVIEW'）删除：该分类不再存在，
--       后续审核结果由「系统」开关统一控制（稀疏存储，无行即默认开启）。
--   * FOLLOW 为新类型，不需要初始化任何行；未配置偏好即默认开启。
--
-- 幂等：UPDATE/DELETE 只匹配 type='REVIEW'，重复执行影响 0 行；
--       不新增表、不新增列。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261117_001__merge_review_into_system_notifications.sql"
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- 1. 存量审核消息并入系统
UPDATE user_notification
SET type = 'SYSTEM'
WHERE type = 'REVIEW';

-- 2. 删除已废弃的「审核」偏好项
DELETE FROM user_notification_preference
WHERE type = 'REVIEW';

-- 3. verify 闸门：REVIEW 通知与偏好都应清零
SET @notif_left = (SELECT COUNT(*) FROM user_notification WHERE type = 'REVIEW');
SET @pref_left  = (SELECT COUNT(*) FROM user_notification_preference WHERE type = 'REVIEW');

SELECT 'CHECK' AS check_result,
       CONCAT('review_notifications=', @notif_left, ', review_preferences=', @pref_left) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('仍有 REVIEW 存量：notifications=', @notif_left, ', preferences=', @pref_left) AS detail
WHERE @notif_left > 0 OR @pref_left > 0;

SELECT 'PASS' AS check_result,
       'fail_cnt=0, 审核消息已并入系统、旧审核偏好已清理' AS detail
WHERE @notif_left = 0 AND @pref_left = 0;
