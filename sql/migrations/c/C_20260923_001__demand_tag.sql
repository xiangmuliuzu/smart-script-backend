-- C module migration: sys_demand_tag table for demand tag CRUD management.
-- Target: isolated dev database (e.g. ruoyi_dev_c_test). Do NOT run on shared DB without approval.
-- Safe to re-run on an isolated dev DB.

CREATE TABLE IF NOT EXISTS `sys_demand_tag` (
  `tag_id` bigint NOT NULL AUTO_INCREMENT,
  `tag_name` varchar(50) NOT NULL,
  `used_count` int NOT NULL DEFAULT 0,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_tag_name` (`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
