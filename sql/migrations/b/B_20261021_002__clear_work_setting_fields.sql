-- =====================================================================
-- B 模块内容 · 清空作品「核心设定 / 角色设定」存量内容
--
-- 背景：实际创作流程不产出「核心设定」「角色设定」，这两个字段仅由演示种子
--   （B_20261009_003）写入过，导致 App 作品详情页与 PC 审核内容面板展示出
--   与真实创作无关的占位文案。代码侧已全部停止读写这两列。
--
-- 处理口径：
--   1. 存量值全部置 NULL（不是空串），保证任何旧版本只读代码都不会展示内容。
--   2. 不 DROP 列：当前列仍被历史分支/旧包的 SELECT 引用，直接删列会让旧包查询报错；
--      待全部服务升级完成后再人工执行文件末尾注释里的 DROP 语句。
--
-- 幂等：重复执行结果一致（第二次起为「无变化」更新）。
--
-- 执行：$env:MYSQL_PWD='<口令>'; mysql -h<主机> -u<账号> script_platform_dev -e "source sql/migrations/b/B_20261021_002__clear_work_setting_fields.sql"
--       verify 闸门为 summary：要求末行 PASS fail_cnt=0。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

-- ---------------------------------------------------------------------
-- 1. 清空存量值
-- ---------------------------------------------------------------------
UPDATE sys_work
SET core_setting = NULL,
    character_setting = NULL
WHERE core_setting IS NOT NULL
   OR character_setting IS NOT NULL;

-- ---------------------------------------------------------------------
-- 2. verify 闸门：不允许任何一行仍残留内容
-- ---------------------------------------------------------------------
SET @dirty = (
    SELECT COUNT(*)
    FROM sys_work
    WHERE core_setting IS NOT NULL
       OR character_setting IS NOT NULL
);

SET @total = (SELECT COUNT(*) FROM sys_work);
SET @fail_cnt = @dirty;

SELECT 'CHECK' AS check_result,
       CONCAT('total=', @total, ', dirty=', @dirty) AS detail;

SELECT 'FAIL' AS check_result,
       CONCAT('仍有 ', @dirty, ' 部作品残留核心设定/角色设定') AS detail
WHERE @fail_cnt > 0;

SELECT 'PASS' AS check_result,
       CONCAT('fail_cnt=0, ', @total, ' 部作品的两个设定字段已全部置 NULL') AS detail
WHERE @fail_cnt = 0;

-- ---------------------------------------------------------------------
-- 3. 可选（确认所有服务均已升级到不含这两个字段的版本后再手动执行）：
--    ALTER TABLE sys_work DROP COLUMN core_setting, DROP COLUMN character_setting;
-- ---------------------------------------------------------------------
