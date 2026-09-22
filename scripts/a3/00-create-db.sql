# A3 isolated integration database
# Clone RuoYi baseline schema then apply A2 migration (do NOT touch ruoyi_dev).

DROP DATABASE IF EXISTS `ruoyi_dev_a3_test`;
CREATE DATABASE `ruoyi_dev_a3_test` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `ruoyi_dev_a3_test`;

-- Import is performed by scripts outside this file using ry_20260320.sql + A2 migrate.
SELECT 'a3_db_created' AS step, DATABASE() AS db;
