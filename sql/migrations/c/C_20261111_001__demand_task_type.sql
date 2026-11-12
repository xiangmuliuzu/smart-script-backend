-- =====================================================================
-- C_20261111_001：任务广场任务类型三分类（衔接 AI 分镜服务）
-- 需求：发布任务时选择任务类型——script=只征集剧本、
--       script_storyboard=剧本+分镜都要、storyboard=提供剧本按剧本做分镜；
--       storyboard 类任务发布者须提供源剧本（选本人作品或上传剧本文件，至少其一）；
--       广场/管理端列表支持按任务类型筛选，存量任务默认 script。
-- 幂等：列存在则跳过 ADD。
-- =====================================================================

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_demand'
      AND COLUMN_NAME = 'task_type') = 0,
  'ALTER TABLE sys_demand ADD COLUMN task_type VARCHAR(32) NOT NULL DEFAULT ''script'' COMMENT ''任务类型: script=只征集剧本, script_storyboard=剧本+分镜, storyboard=提供剧本做分镜'' AFTER source',
  'SELECT ''task_type already exists'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_demand'
      AND COLUMN_NAME = 'source_work_id') = 0,
  'ALTER TABLE sys_demand ADD COLUMN source_work_id BIGINT NULL COMMENT ''源剧本作品ID（storyboard 类型，发布者选本人作品，与 source_script_url 至少其一）'' AFTER task_type',
  'SELECT ''source_work_id already exists'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_demand'
      AND COLUMN_NAME = 'source_script_url') = 0,
  'ALTER TABLE sys_demand ADD COLUMN source_script_url VARCHAR(500) NULL COMMENT ''源剧本文件相对路径（storyboard 类型，上传的剧本文档）'' AFTER source_work_id',
  'SELECT ''source_script_url already exists'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_demand'
      AND COLUMN_NAME = 'source_script_name') = 0,
  'ALTER TABLE sys_demand ADD COLUMN source_script_name VARCHAR(255) NULL COMMENT ''源剧本文件原始文件名（展示用）'' AFTER source_script_url',
  'SELECT ''source_script_name already exists'' AS msg'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;