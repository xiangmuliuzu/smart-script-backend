-- MySQL dump 10.13  Distrib 8.0.36, for Linux (x86_64)
--
-- Sandbox support schema (structure only)
-- ------------------------------------------------------
-- Server version	8.0.36

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `sys_ad_config`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_ad_config` (
  `ad_id` int NOT NULL AUTO_INCREMENT,
  `category` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PLACEMENT',
  `ad_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `ad_position` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `ad_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `ad_source` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `ad_unit_id` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `is_enabled` tinyint NOT NULL,
  `frequency_limit` int NOT NULL,
  `daily_cap` int NOT NULL,
  `target_audience` json DEFAULT NULL,
  `watch_limit` int NOT NULL,
  `reward_points` int NOT NULL,
  `sort` int NOT NULL,
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `image_url` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `link_url` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `duration_seconds` int NOT NULL DEFAULT '5',
  PRIMARY KEY (`ad_id`),
  KEY `idx_ad_position` (`ad_position`) USING BTREE,
  KEY `idx_is_enabled` (`is_enabled`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='广告配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_ad_revenue`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_ad_revenue` (
  `revenue_id` bigint NOT NULL AUTO_INCREMENT,
  `stat_date` date NOT NULL,
  `work_id` bigint NOT NULL,
  `drama_id` bigint DEFAULT NULL,
  `ad_slot` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `impressions` int DEFAULT NULL,
  `clicks` int DEFAULT NULL,
  `revenue_amount` decimal(12,2) DEFAULT NULL,
  `channel` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`revenue_id`),
  UNIQUE KEY `uk_date_work_slot` (`stat_date`,`work_id`,`ad_slot`) USING BTREE,
  KEY `idx_date` (`stat_date`) USING BTREE,
  KEY `idx_work` (`work_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='广告收益流水表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_ad_task`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_ad_task` (
  `task_id` int NOT NULL AUTO_INCREMENT,
  `task_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `ad_config_id` int DEFAULT NULL,
  `ad_id` int NOT NULL,
  `ad_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `target_id` bigint DEFAULT NULL,
  `reward_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reward_amount` int DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`task_id`),
  KEY `idx_user` (`user_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_ai_quota_account`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_ai_quota_account` (
  `account_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `available_quota` int NOT NULL,
  `reserved_quota` int NOT NULL,
  `total_earned` bigint NOT NULL,
  `total_consumed` bigint NOT NULL,
  `total_refunded` bigint NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_ai_quota_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_ai_quota_record` (
  `record_id` bigint NOT NULL AUTO_INCREMENT,
  `record_no` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `change_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `change_amount` int NOT NULL,
  `before_balance` int NOT NULL,
  `after_balance` int NOT NULL,
  `business_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `business_id` bigint DEFAULT NULL,
  `idempotency_key` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`record_id`),
  UNIQUE KEY `uk_record_no` (`record_no`) USING BTREE,
  UNIQUE KEY `uk_idempotency_key` (`idempotency_key`) USING BTREE,
  KEY `idx_user_created` (`user_id`) USING BTREE,
  KEY `idx_business` (`business_type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=112 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_ai_request`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_ai_request` (
  `request_id` bigint NOT NULL AUTO_INCREMENT,
  `request_no` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `work_id` bigint DEFAULT NULL,
  `capability` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `quota_cost` int NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `provider` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `prompt_hash` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `result_file_id` bigint DEFAULT NULL,
  `error_code` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`request_id`),
  UNIQUE KEY `uk_request_no` (`request_no`) USING BTREE,
  KEY `idx_user_created` (`user_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=60 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_ai_review_rule`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_ai_review_rule` (
  `rule_id` int NOT NULL AUTO_INCREMENT,
  `rule_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `rule_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `rule_key` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `rule_value` text COLLATE utf8mb4_general_ci NOT NULL,
  `violation_level` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `action` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `threshold` decimal(5,2) DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `sort` int NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`rule_id`),
  UNIQUE KEY `uk_rule_key` (`rule_key`) USING BTREE,
  KEY `idx_rule_type` (`rule_type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI审核规则配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_ai_skill`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_ai_skill` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'skill ID',
  `skill_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'skill 名称（唯一）',
  `file_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '上传的原始文件名',
  `file_content` text COLLATE utf8mb4_general_ci COMMENT 'skill 文件文本内容（UTF-8）',
  `status` char(1) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '0' COMMENT '状态：0正常 1停用',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'system' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_skill_name` (`skill_name`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 导演风格 skill 配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_ai_write_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_ai_write_record` (
  `record_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_id` bigint DEFAULT NULL,
  `title` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `write_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `pov` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `language_style` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `pace` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `reference_doc_ids` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `input_prompt` text COLLATE utf8mb4_general_ci NOT NULL,
  `output_content` longtext COLLATE utf8mb4_general_ci NOT NULL,
  `word_count` int NOT NULL,
  `ai_model` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `tokens_used` int NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`record_id`),
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=99 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI写作记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_bill_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_bill_record` (
  `bill_id` bigint NOT NULL AUTO_INCREMENT,
  `bill_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `bill_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `balance_after` decimal(12,2) NOT NULL,
  `related_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `related_id` bigint DEFAULT NULL,
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`bill_id`),
  UNIQUE KEY `uk_bill_no` (`bill_no`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_bill_type` (`bill_type`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='账单流水表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_blacklist`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_blacklist` (
  `black_id` bigint NOT NULL AUTO_INCREMENT,
  `target_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `target_value` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `black_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `reason` varchar(500) COLLATE utf8mb4_general_ci NOT NULL,
  `operator_id` bigint NOT NULL,
  `duration` int NOT NULL,
  `expire_at` datetime DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `release_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `release_operator_id` bigint DEFAULT NULL,
  `release_time` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`black_id`),
  KEY `idx_target` (`target_type`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_expire_at` (`expire_at`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='黑名单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_bookshelf_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_bookshelf_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `add_source` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `last_read_chapter_id` bigint DEFAULT NULL,
  `last_read_at` datetime DEFAULT NULL,
  `is_deleted` tinyint NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_work` (`user_id`,`work_id`) USING BTREE,
  KEY `idx_user_updated` (`user_id`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=73 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_browse_history`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_browse_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `browse_count` int NOT NULL,
  `last_browse_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_work` (`user_id`,`work_id`) USING BTREE,
  KEY `idx_last_browse` (`last_browse_at`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=58 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='浏览历史表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_category_group`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_category_group` (
  `group_id` int NOT NULL AUTO_INCREMENT,
  `channel` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `group_name` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `category_ids` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `sort` int NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`group_id`),
  KEY `idx_channel` (`channel`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='剧本分类分组表（题材、标签、篇幅等，不包含外部视频分类）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_category_group_item`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_category_group_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` int NOT NULL,
  `category_id` int NOT NULL,
  `sort` int NOT NULL,
  `created_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_category` (`group_id`,`category_id`) USING BTREE,
  KEY `idx_category_id` (`category_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_chat_trade_template`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_chat_trade_template` (
  `template_id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(32) NOT NULL COMMENT '分类：price_param/price_text/progress/general',
  `content` varchar(200) NOT NULL COMMENT '定型文内容，含{price}占位符表示需填入金额',
  `role` varchar(16) NOT NULL DEFAULT 'both' COMMENT 'buyer/seller/both',
  `has_amount` tinyint NOT NULL DEFAULT '0' COMMENT '1=含{price}占位符，发送时须填入金额并同步更新报价',
  `sort_order` int NOT NULL DEFAULT '0',
  `is_enabled` tinyint NOT NULL DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`template_id`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易会话定型文模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_comment`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_comment` (
  `comment_id` bigint NOT NULL AUTO_INCREMENT,
  `episode_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `parent_id` bigint NOT NULL,
  `content` varchar(500) COLLATE utf8mb4_general_ci NOT NULL,
  `like_count` int NOT NULL,
  `reply_count` int NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`comment_id`),
  KEY `idx_episode_id` (`episode_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_parent_id` (`parent_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外部视频评论缓存表（仅在外部接口支持评论时启用）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_contract`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_contract` (
  `contract_id` bigint NOT NULL AUTO_INCREMENT,
  `contract_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `contract_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `external_contract_no` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `amount` decimal(12,2) DEFAULT NULL,
  `file_id` bigint DEFAULT NULL,
  `order_id` bigint NOT NULL,
  `buyer_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `template_id` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `source_type` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'template' COMMENT '合同来源: template平台模板/uploaded用户上传PDF',
  `contract_content` longtext COLLATE utf8mb4_general_ci NOT NULL,
  `contract_url` varchar(500) COLLATE utf8mb4_general_ci NOT NULL,
  `buyer_sign_status` tinyint NOT NULL,
  `seller_sign_status` tinyint NOT NULL,
  `buyer_sign_time` datetime DEFAULT NULL,
  `seller_sign_time` datetime DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `buyer_signature_image` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????????URL',
  `buyer_seal_image` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??????URL',
  `seller_signature_image` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '????????URL',
  `seller_seal_image` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??????URL',
  `buyer_sign_pos` text COLLATE utf8mb4_general_ci COMMENT '买方签章定位JSON(上传件拖拽坐标)',
  `seller_sign_pos` text COLLATE utf8mb4_general_ci COMMENT '卖方签章定位JSON(上传件拖拽坐标)',
  `signed_pdf_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '??????PDF??URL',
  `source_pdf_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '上传合同PDF文件URL',
  `pdf_page_count` int DEFAULT NULL COMMENT '上传PDF页数',
  `clauses_text` text COLLATE utf8mb4_general_ci COMMENT '可编辑的正文条款纯文本(null=模板默认)',
  PRIMARY KEY (`contract_id`),
  UNIQUE KEY `uk_contract_no` (`contract_no`) USING BTREE,
  KEY `idx_order_id` (`order_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='电子合同表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_contract_event`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_contract_event` (
  `event_id` bigint NOT NULL AUTO_INCREMENT,
  `event_no` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `contract_id` bigint NOT NULL,
  `provider` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `event_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `event_payload` json DEFAULT NULL,
  `process_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `retry_count` int NOT NULL,
  `event_time` datetime NOT NULL,
  `processed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_event_no` (`event_no`) USING BTREE,
  KEY `idx_contract_status` (`contract_id`) USING BTREE,
  KEY `idx_retry` (`process_status`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_copyright_application`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_copyright_application` (
  `apply_id` bigint NOT NULL AUTO_INCREMENT,
  `apply_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `work_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `copyright_center` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `external_apply_no` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cert_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cert_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `reject_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `attachment_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Application material file URL (single or multiple files separated by comma)',
  `originality_proof` text COLLATE utf8mb4_general_ci COMMENT 'Originality proof material URL (JSON array)',
  `author_identity_doc` text COLLATE utf8mb4_general_ci COMMENT 'Author identity document URL (JSON array)',
  `creation_process` text COLLATE utf8mb4_general_ci COMMENT 'Creation process material URL (JSON array)',
  `other_proof` text COLLATE utf8mb4_general_ci COMMENT 'Other supporting material URL (JSON array)',
  `submit_time` datetime NOT NULL,
  `cert_time` datetime DEFAULT NULL,
  `sync_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `last_sync_time` datetime DEFAULT NULL,
  `config_id` bigint DEFAULT NULL COMMENT '版权中心配置ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`apply_id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_config_id` (`config_id`),
  KEY `idx_user_status` (`user_id`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='版权确权申请表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_copyright_authorization`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_copyright_authorization` (
  `auth_id` bigint NOT NULL AUTO_INCREMENT,
  `auth_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `work_id` bigint NOT NULL,
  `licensor_id` bigint NOT NULL,
  `licensee_id` bigint NOT NULL,
  `order_id` bigint DEFAULT NULL,
  `license_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `license_scope` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date DEFAULT NULL,
  `amount` decimal(12,2) NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `contract_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`auth_id`),
  UNIQUE KEY `uk_auth_no` (`auth_no`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE,
  KEY `idx_licensor_id` (`licensor_id`) USING BTREE,
  KEY `idx_licensee_id` (`licensee_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='版权授权记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_copyright_center_config`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_copyright_center_config` (
  `config_id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `center_code` varchar(64) NOT NULL COMMENT '版权中心代码',
  `center_name` varchar(128) NOT NULL COMMENT '版权中心名称',
  `api_endpoint` varchar(256) DEFAULT NULL COMMENT 'API端点',
  `api_key` varchar(256) DEFAULT NULL COMMENT 'API密钥',
  `api_secret` varchar(256) DEFAULT NULL COMMENT 'API密钥',
  `status` varchar(20) NOT NULL DEFAULT 'enabled' COMMENT '状态',
  `priority` int DEFAULT '0' COMMENT '优先级',
  `config_json` text COMMENT '扩展配置JSON',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_center_code` (`center_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='版权中心配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_copyright_cert`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_copyright_cert` (
  `cert_id` bigint NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `cert_no` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `blockchain_hash` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cert_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `apply_time` datetime NOT NULL,
  `cert_time` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`cert_id`),
  UNIQUE KEY `uk_cert_no` (`cert_no`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='版权存证表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_copyright_seal`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_copyright_seal` (
  `seal_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `seal_name` varchar(100) NOT NULL,
  `seal_image_url` varchar(500) DEFAULT NULL,
  `material_urls` text COMMENT 'Material URLs (comma-separated)',
  `review_status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  `seal_status` varchar(20) NOT NULL DEFAULT 'disabled' COMMENT 'disabled/enabled/abnormal',
  `reject_reason` varchar(500) DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`seal_id`),
  KEY `idx_copyright_seal_review` (`review_status`,`create_time`),
  KEY `idx_copyright_seal_user` (`user_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='版权印章申请';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_copyright_seal_log`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_copyright_seal_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `seal_id` bigint NOT NULL,
  `operation_type` varchar(30) NOT NULL COMMENT 'review_approve/review_reject/enable/disable',
  `from_status` varchar(20) DEFAULT NULL,
  `to_status` varchar(20) NOT NULL,
  `reason` varchar(500) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`),
  KEY `idx_copyright_seal_log_seal` (`seal_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=61 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='版权印章审核与状态操作记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_creator_stats`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_creator_stats` (
  `stats_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `total_income` decimal(12,2) NOT NULL,
  `month_income` decimal(12,2) NOT NULL,
  `total_orders` int NOT NULL,
  `published_count` int NOT NULL,
  `reviewing_count` int NOT NULL,
  `sold_count` int NOT NULL,
  `total_views` bigint NOT NULL,
  `total_favorites` int NOT NULL,
  `avg_rating` decimal(3,1) NOT NULL,
  `stat_date` date NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`stats_id`),
  UNIQUE KEY `uk_user_date` (`user_id`,`stat_date`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作者能力统计表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_delivery`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_delivery` (
  `delivery_id` bigint NOT NULL AUTO_INCREMENT COMMENT '交割ID',
  `delivery_no` varchar(32) NOT NULL COMMENT '交割单号',
  `order_id` bigint NOT NULL COMMENT '关联订单ID',
  `work_id` bigint NOT NULL COMMENT '作品ID',
  `buyer_id` bigint NOT NULL COMMENT '买方用户ID',
  `seller_id` bigint NOT NULL COMMENT '卖方用户ID',
  `amount` decimal(12,2) NOT NULL COMMENT '交易金额（元）',
  `license_type` varchar(32) DEFAULT NULL COMMENT '授权类型',
  `status` varchar(20) NOT NULL DEFAULT 'pending_upload' COMMENT '状态（pending_upload/pending_confirm/completed/cancelled）',
  `contract_no` varchar(64) DEFAULT NULL COMMENT '合同编号（冗余展示）',
  `contract_sign_time` datetime DEFAULT NULL COMMENT '合同签署完成时间（冗余展示）',
  `cert_no` varchar(64) DEFAULT NULL COMMENT '版权证书号（冗余展示）',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `upload_time` datetime DEFAULT NULL COMMENT '卖方提交交割时间',
  `confirm_time` datetime DEFAULT NULL COMMENT '买方确认收货时间',
  `completed_time` datetime DEFAULT NULL COMMENT '交割完成时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`delivery_id`),
  UNIQUE KEY `uk_delivery_no` (`delivery_no`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_buyer_id` (`buyer_id`),
  KEY `idx_seller_id` (`seller_id`),
  KEY `idx_status` (`status`),
  KEY `idx_work_id` (`work_id`)
) ENGINE=InnoDB AUTO_INCREMENT=529 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='版权交割单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_delivery_file`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_delivery_file` (
  `file_id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `delivery_id` bigint NOT NULL COMMENT '交割ID',
  `file_name` varchar(255) NOT NULL COMMENT '文件名',
  `file_type` varchar(20) NOT NULL COMMENT '类型（cert/source/doc/other）',
  `file_url` varchar(500) NOT NULL COMMENT '文件URL',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '文件大小（字节）',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`file_id`),
  KEY `idx_delivery_id` (`delivery_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='版权交割文件';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_delivery_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_delivery_record` (
  `delivery_id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `deliverer_id` bigint NOT NULL,
  `receiver_id` bigint NOT NULL,
  `delivery_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `material_file_id` bigint DEFAULT NULL,
  `deliverer_confirm` tinyint DEFAULT NULL,
  `deliverer_confirm_at` datetime DEFAULT NULL,
  `receiver_confirm` tinyint DEFAULT NULL,
  `receiver_confirm_at` datetime DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`delivery_id`),
  KEY `idx_order` (`order_id`) USING BTREE,
  KEY `idx_work` (`work_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='版权交割记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_draft`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_draft` (
  `draft_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_id` bigint DEFAULT NULL,
  `title` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `content` longtext COLLATE utf8mb4_general_ci NOT NULL,
  `auto_save` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`draft_id`),
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_updated_at` (`updated_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='草稿表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_drama_channel`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_drama_channel` (
  `channel_id` int NOT NULL AUTO_INCREMENT,
  `channel_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `channel_code` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `platform` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `account_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `account_id` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `api_config` json DEFAULT NULL,
  `is_auto_distribute` tinyint NOT NULL,
  `status` tinyint NOT NULL,
  `sort` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`channel_id`),
  UNIQUE KEY `uk_channel_code` (`channel_code`) USING BTREE,
  KEY `idx_platform` (`platform`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='短剧发行渠道表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_episode_like`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_episode_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `episode_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_episode` (`user_id`,`episode_id`) USING BTREE,
  KEY `idx_episode_id` (`episode_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=81 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外部视频点赞缓存表（仅在外部接口支持点赞时启用）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_escrow`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_escrow` (
  `escrow_id` bigint NOT NULL AUTO_INCREMENT,
  `escrow_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `order_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `paid_at` datetime DEFAULT NULL,
  `pay_channel` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `release_time` datetime DEFAULT NULL,
  `release_reason` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `refund_amount` decimal(12,2) DEFAULT NULL,
  `refunded_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`escrow_id`),
  UNIQUE KEY `uk_escrow_no` (`escrow_no`) USING BTREE,
  KEY `idx_order_id` (`order_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资金托管表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_external_drama`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_external_drama` (
  `drama_id` bigint NOT NULL AUTO_INCREMENT,
  `channel_id` int NOT NULL,
  `external_content_id` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `title` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `cover_file_id` bigint DEFAULT NULL,
  `external_url` varchar(1000) COLLATE utf8mb4_general_ci NOT NULL,
  `related_work_id` bigint DEFAULT NULL,
  `source_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `authorization_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `sync_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `copyright_note` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`drama_id`),
  UNIQUE KEY `uk_channel_external` (`channel_id`,`external_content_id`) USING BTREE,
  KEY `idx_related_work` (`related_work_id`) USING BTREE,
  KEY `idx_status_auth` (`status`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_favorite`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_work` (`user_id`,`work_id`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4229 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='收藏表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_file_asset`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_file_asset` (
  `file_id` bigint NOT NULL AUTO_INCREMENT,
  `owner_id` bigint NOT NULL,
  `business_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `business_id` bigint DEFAULT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `storage_key` varchar(500) COLLATE utf8mb4_general_ci NOT NULL,
  `file_hash` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `file_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `file_size` bigint NOT NULL,
  `access_scope` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `scan_status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `is_deleted` tinyint NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`file_id`),
  UNIQUE KEY `uk_storage_key` (`storage_key`) USING BTREE,
  KEY `idx_owner_id` (`owner_id`) USING BTREE,
  KEY `idx_business` (`business_type`) USING BTREE,
  KEY `idx_scan_status` (`scan_status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_finance_abnormal`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_finance_abnormal` (
  `abnormal_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `abnormal_type` varchar(20) NOT NULL COMMENT 'Type: duplicate/refund/escrow/settlement',
  `related_no` varchar(32) NOT NULL COMMENT 'Related Number',
  `order_id` bigint DEFAULT NULL COMMENT 'Order ID',
  `user_id` bigint DEFAULT NULL COMMENT 'User ID',
  `abnormal_amount` decimal(12,2) DEFAULT NULL COMMENT 'Amount',
  `abnormal_reason` varchar(500) DEFAULT NULL COMMENT 'Reason',
  `abnormal_desc` varchar(500) DEFAULT NULL COMMENT 'Description',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'Status: pending/handled',
  `handle_solution` varchar(50) DEFAULT NULL COMMENT 'Solution',
  `handle_remark` varchar(1000) DEFAULT NULL COMMENT 'Handle Remark',
  `handle_by` varchar(64) DEFAULT NULL COMMENT 'Handler',
  `handle_time` datetime DEFAULT NULL COMMENT 'Handle Time',
  `duplicate_count` int DEFAULT NULL COMMENT 'Duplicate Count',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`abnormal_id`),
  KEY `idx_type` (`abnormal_type`),
  KEY `idx_related_no` (`related_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Finance Abnormal Table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_income_account`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_income_account` (
  `account_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `available_amount` decimal(18,2) NOT NULL,
  `frozen_amount` decimal(18,2) NOT NULL,
  `settled_amount` decimal(18,2) NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_income_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_income_record` (
  `record_id` bigint NOT NULL AUTO_INCREMENT,
  `record_no` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `change_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `amount_cent` bigint NOT NULL,
  `before_balance_cent` bigint NOT NULL,
  `after_balance_cent` bigint NOT NULL,
  `business_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `business_id` bigint DEFAULT NULL,
  `rule_version` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `idempotency_key` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`record_id`),
  UNIQUE KEY `uk_record_no` (`record_no`) USING BTREE,
  UNIQUE KEY `uk_idempotency_key` (`idempotency_key`) USING BTREE,
  KEY `idx_user_created` (`user_id`) USING BTREE,
  KEY `idx_business` (`business_type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_income_settle`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_income_settle` (
  `settle_id` bigint NOT NULL AUTO_INCREMENT COMMENT '结算单ID',
  `settle_no` varchar(32) NOT NULL COMMENT '结算单号',
  `user_id` bigint NOT NULL COMMENT '卖家/作者用户ID',
  `order_id` bigint NOT NULL COMMENT '关联订单ID',
  `order_no` varchar(64) DEFAULT NULL COMMENT '订单号快照',
  `work_title` varchar(200) DEFAULT NULL COMMENT '作品标题快照',
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '订单总额快照',
  `platform_fee` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '平台费用快照',
  `split_rate` decimal(8,4) NOT NULL DEFAULT '0.0000' COMMENT '分成比例快照',
  `seller_amount` decimal(12,2) NOT NULL COMMENT '作者应得金额快照',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending待确认/confirmed已确认入账/disputed有异议',
  `dispute_reason` varchar(500) DEFAULT NULL COMMENT '异议原因',
  `confirm_time` datetime DEFAULT NULL COMMENT '确认时间',
  `dispute_time` datetime DEFAULT NULL COMMENT '异议时间',
  `bill_id` bigint DEFAULT NULL COMMENT '入账后关联账单ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（订单完成时）',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`settle_id`),
  UNIQUE KEY `uk_settle_no` (`settle_no`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_user_status` (`user_id`,`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作者收益待结算单（到账前确认）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_invoice`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_invoice` (
  `invoice_id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `order_id` bigint NOT NULL,
  `applicant_id` bigint NOT NULL,
  `invoice_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `email` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `title` varchar(200) COLLATE utf8mb4_general_ci NOT NULL,
  `tax_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `amount` decimal(12,2) NOT NULL,
  `invoice_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `reject_reason` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `invoice_time` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`invoice_id`),
  UNIQUE KEY `uk_invoice_no` (`invoice_no`) USING BTREE,
  KEY `idx_order_id` (`order_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='发票表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_notification`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_notification` (
  `notify_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `notify_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `title` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `content` varchar(500) COLLATE utf8mb4_general_ci NOT NULL,
  `related_id` bigint DEFAULT NULL,
  `related_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_read` tinyint NOT NULL,
  `read_time` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`notify_id`),
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_is_read` (`is_read`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='消息通知表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_operation_log`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `operator_id` bigint NOT NULL,
  `operator_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `module` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `operation` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `target_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `target_id` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `target_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `request_method` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `request_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `request_params` text COLLATE utf8mb4_general_ci,
  `response_result` text COLLATE utf8mb4_general_ci,
  `ip_address` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `user_agent` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `error_msg` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `cost_time` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`log_id`),
  KEY `idx_operator_id` (`operator_id`) USING BTREE,
  KEY `idx_module` (`module`) USING BTREE,
  KEY `idx_operation` (`operation`) USING BTREE,
  KEY `idx_target` (`target_type`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_request_id` (`request_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_personal_seal`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_personal_seal` (
  `seal_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `seal_file_id` bigint NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `review_version` int NOT NULL,
  `reject_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `disabled_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`seal_id`),
  UNIQUE KEY `uk_user_current` (`user_id`) USING BTREE,
  KEY `idx_user_status` (`user_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_play_progress`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_play_progress` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `episode_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `progress_seconds` int DEFAULT NULL,
  `total_duration` int DEFAULT NULL,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_episode` (`user_id`,`episode_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=129 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_points_account`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_points_account` (
  `account_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `balance` int NOT NULL,
  `total_earned` int NOT NULL,
  `total_spent` int NOT NULL,
  `today_earned` int NOT NULL,
  `last_update_date` date NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=147 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='积分账户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_points_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_points_record` (
  `record_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `change_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `points` int NOT NULL,
  `balance_after` int NOT NULL,
  `source` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `related_id` bigint DEFAULT NULL,
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`record_id`),
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='积分流水表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_polish_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_polish_record` (
  `record_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `text_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `polish_dir` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `intensity` varchar(10) COLLATE utf8mb4_general_ci NOT NULL,
  `target_use` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `keep_words` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reference_doc_ids` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `source_text` text COLLATE utf8mb4_general_ci NOT NULL,
  `polished_text` text COLLATE utf8mb4_general_ci NOT NULL,
  `ai_model` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `tokens_used` int NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`record_id`),
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='润色记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_read_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_read_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `chapter_id` bigint DEFAULT NULL,
  `read_duration` int NOT NULL,
  `read_progress` decimal(5,2) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_work` (`user_id`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=16482 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='阅读记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_reference_doc`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_reference_doc` (
  `doc_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `doc_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `doc_url` varchar(500) COLLATE utf8mb4_general_ci NOT NULL,
  `doc_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `file_size` bigint NOT NULL,
  `use_count` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`doc_id`),
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_doc_type` (`doc_type`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='参考文档表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_report`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_report` (
  `report_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `target_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `target_id` bigint NOT NULL,
  `reason` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `handler_id` bigint DEFAULT NULL,
  `handle_result` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`report_id`),
  KEY `idx_user` (`user_id`) USING BTREE,
  KEY `idx_target` (`target_type`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_review`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_review` (
  `review_id` int NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `submitter_id` bigint NOT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `review_comment` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`review_id`),
  KEY `idx_work` (`work_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_review_log`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_review_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT,
  `review_id` bigint NOT NULL COMMENT '审核记录ID',
  `action` varchar(20) NOT NULL COMMENT '审核动作',
  `operator_id` bigint NOT NULL DEFAULT '1' COMMENT '操作人ID',
  `operator_name` varchar(50) DEFAULT NULL,
  `before_status` varchar(20) DEFAULT NULL,
  `after_status` varchar(20) DEFAULT NULL,
  `review_opinion` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`log_id`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审核操作日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_review_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_review_record` (
  `review_id` bigint NOT NULL AUTO_INCREMENT,
  `review_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `target_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `target_id` bigint NOT NULL,
  `submitter_id` bigint NOT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `ai_result` json DEFAULT NULL,
  `ai_risk_level` varchar(10) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `ai_sensitive_words` text COLLATE utf8mb4_general_ci,
  `review_result` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `review_opinion` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `ai_start_time` datetime DEFAULT NULL,
  `ai_end_time` datetime DEFAULT NULL,
  `review_start_time` datetime DEFAULT NULL,
  `review_end_time` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`review_id`),
  UNIQUE KEY `uk_review_no` (`review_no`) USING BTREE,
  KEY `idx_target` (`target_type`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_reviewer_id` (`reviewer_id`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=92 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审核记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_risk_rule`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_risk_rule` (
  `rule_id` int NOT NULL AUTO_INCREMENT,
  `rule_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `rule_code` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `rule_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `trigger_condition` json NOT NULL,
  `action` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `risk_level` varchar(10) COLLATE utf8mb4_general_ci NOT NULL,
  `threshold` decimal(10,2) DEFAULT NULL,
  `time_window` int DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `hit_count` int NOT NULL,
  `sort` int NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`rule_id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`) USING BTREE,
  KEY `idx_rule_type` (`rule_type`) USING BTREE,
  KEY `idx_risk_level` (`risk_level`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='风控规则表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_script_outline`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_script_outline` (
  `outline_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_id` bigint DEFAULT NULL,
  `title` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `genre` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `length_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `core_setting` text COLLATE utf8mb4_general_ci NOT NULL,
  `character_setting` text COLLATE utf8mb4_general_ci NOT NULL,
  `tags` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `outline_content` text COLLATE utf8mb4_general_ci NOT NULL,
  `ai_model` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`outline_id`),
  KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='剧本大纲表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_seal_material`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_seal_material` (
  `material_id` bigint NOT NULL AUTO_INCREMENT,
  `seal_id` bigint NOT NULL,
  `material_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `file_id` bigint NOT NULL,
  `file_hash` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `review_version` int NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`material_id`),
  UNIQUE KEY `uk_seal_version_type` (`seal_id`,`review_version`,`material_type`) USING BTREE,
  KEY `idx_file_hash` (`file_hash`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_seal_review`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_seal_review` (
  `review_id` bigint NOT NULL AUTO_INCREMENT,
  `review_no` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `seal_id` bigint NOT NULL,
  `review_version` int NOT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `result` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `opinion` varchar(1000) COLLATE utf8mb4_general_ci NOT NULL,
  `created_at` datetime NOT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`review_id`),
  UNIQUE KEY `uk_seal_review_version` (`seal_id`,`review_version`) USING BTREE,
  KEY `idx_status_result` (`result`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_search_history`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_search_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `keyword` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `search_count` int NOT NULL,
  `last_search_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_keyword` (`keyword`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=178 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='搜索历史表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_sensitive_word`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_sensitive_word` (
  `word_id` int NOT NULL AUTO_INCREMENT,
  `word` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `category` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `level` varchar(10) COLLATE utf8mb4_general_ci NOT NULL,
  `action` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `replace_word` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `hit_count` int NOT NULL,
  `source` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`word_id`),
  UNIQUE KEY `uk_word` (`word`) USING BTREE,
  KEY `idx_category` (`category`) USING BTREE,
  KEY `idx_level` (`level`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='敏感词库表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_settlement`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_settlement` (
  `settlement_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `settlement_no` varchar(32) NOT NULL COMMENT 'Settlement Number',
  `author_id` bigint NOT NULL COMMENT 'Author User ID',
  `period_start` date NOT NULL COMMENT 'Period Start',
  `period_end` date NOT NULL COMMENT 'Period End',
  `order_count` int NOT NULL DEFAULT '0' COMMENT 'Order Count',
  `total_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Total Amount',
  `platform_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Platform Amount',
  `author_amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Author Amount',
  `platform_ratio` int NOT NULL DEFAULT '20' COMMENT 'Platform Ratio',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'Status: pending/settled/abnormal',
  `settlement_time` datetime DEFAULT NULL COMMENT 'Settlement Time',
  `abnormal_reason` varchar(500) DEFAULT NULL COMMENT 'Abnormal Reason',
  `handle_remark` varchar(1000) DEFAULT NULL COMMENT 'Handle Remark',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`settlement_id`),
  UNIQUE KEY `uk_settlement_no` (`settlement_no`),
  KEY `idx_author_id` (`author_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Settlement Table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_subscribe`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_subscribe` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `notify_enabled` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_work` (`user_id`,`work_id`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='追更订阅表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_system_config`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_system_config` (
  `config_id` int NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `config_value` text COLLATE utf8mb4_general_ci NOT NULL,
  `config_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `category` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `is_encrypted` tinyint NOT NULL,
  `sort` int NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `uk_config_key` (`config_key`) USING BTREE,
  KEY `idx_category` (`category`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_task`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_task` (
  `task_id` int NOT NULL AUTO_INCREMENT,
  `task_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `task_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `task_code` varchar(30) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `description` varchar(255) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `target_count` int NOT NULL DEFAULT '1',
  `reward_points` int NOT NULL DEFAULT '0',
  `icon` varchar(255) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '',
  `start_time` datetime DEFAULT NULL COMMENT '生效开始时间(空=立即)',
  `end_time` datetime DEFAULT NULL COMMENT '生效结束时间(空=长期)',
  `daily_limit` int NOT NULL DEFAULT '0' COMMENT '每日可领取次数上限(0=不限)',
  `sort` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_task_code` (`task_code`) USING BTREE,
  KEY `idx_task_type` (`task_type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_unlock_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_unlock_record` (
  `unlock_id` int NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `episode_id` bigint NOT NULL,
  `work_id` bigint NOT NULL,
  `unlock_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `amount` decimal(10,2) DEFAULT NULL,
  `ad_task_id` int DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `expire_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`unlock_id`),
  UNIQUE KEY `uk_user_episode` (`user_id`,`episode_id`) USING BTREE,
  KEY `idx_user` (`user_id`) USING BTREE,
  KEY `idx_work` (`work_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_auth`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_user_auth` (
  `auth_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `auth_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `real_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `id_card` varchar(18) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `id_card_front` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `id_card_back` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `credit_code` varchar(18) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `business_license` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `reject_reason` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`auth_id`),
  UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='实名认证表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_follow`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_user_follow` (
  `id` int NOT NULL AUTO_INCREMENT,
  `follower_id` int NOT NULL,
  `followee_id` int NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_followee` (`follower_id`,`followee_id`),
  KEY `idx_follower` (`follower_id`) USING BTREE,
  KEY `idx_followee` (`followee_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_task`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_user_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `task_id` int NOT NULL,
  `progress` int NOT NULL,
  `is_completed` tinyint NOT NULL,
  `is_claimed` tinyint NOT NULL,
  `complete_time` datetime DEFAULT NULL,
  `claim_time` datetime DEFAULT NULL,
  `period_date` date NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_task_period` (`user_id`,`task_id`,`period_date`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户任务完成表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_withdraw_apply`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_withdraw_apply` (
  `withdraw_id` bigint NOT NULL AUTO_INCREMENT,
  `withdraw_no` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `fee` decimal(12,2) NOT NULL,
  `actual_amount` decimal(12,2) NOT NULL,
  `withdraw_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `account_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `account_no` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `bank_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `review_opinion` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `pay_time` datetime DEFAULT NULL,
  `pay_trade_no` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `client_token` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '?ͻ????ݵ????',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`withdraw_id`),
  UNIQUE KEY `uk_withdraw_no` (`withdraw_no`) USING BTREE,
  UNIQUE KEY `uk_user_client_token` (`user_id`,`client_token`),
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8008 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='提现申请表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_work_access_grant`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_work_access_grant` (
  `grant_id` bigint NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `grant_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `source_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL,
  `source_id` bigint DEFAULT NULL,
  `start_at` datetime NOT NULL,
  `end_at` datetime DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`grant_id`),
  UNIQUE KEY `uk_user_work_grant` (`user_id`,`work_id`,`grant_type`) USING BTREE,
  KEY `idx_work_user` (`work_id`) USING BTREE,
  KEY `idx_expire_at` (`end_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_work_chapter`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_work_chapter` (
  `chapter_id` bigint NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `chapter_no` int NOT NULL,
  `chapter_title` varchar(100) COLLATE utf8mb4_general_ci NOT NULL,
  `content` longtext COLLATE utf8mb4_general_ci NOT NULL,
  `word_count` int NOT NULL,
  `is_free` tinyint NOT NULL,
  `status` tinyint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`chapter_id`),
  UNIQUE KEY `uk_work_chapter_no` (`work_id`,`chapter_no`) USING BTREE,
  KEY `idx_work_id` (`work_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=298 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作品章节表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_work_file`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_work_file` (
  `file_id` bigint NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `file_url` varchar(500) COLLATE utf8mb4_general_ci NOT NULL,
  `file_type` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `file_size` bigint NOT NULL,
  `is_preview` tinyint NOT NULL,
  `sort` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`file_id`),
  KEY `idx_work_id` (`work_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=68 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作品文件表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_work_rating`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_work_rating` (
  `rating_id` bigint NOT NULL AUTO_INCREMENT COMMENT '评分记录ID',
  `work_id` bigint NOT NULL COMMENT '作品ID',
  `user_id` bigint NOT NULL COMMENT '评分用户ID',
  `score` tinyint NOT NULL COMMENT '评分（满分 10 分，每星 2 分：2/4/6/8/10）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次评分时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近评分时间',
  PRIMARY KEY (`rating_id`),
  UNIQUE KEY `uk_work_user` (`work_id`,`user_id`),
  KEY `idx_work_id` (`work_id`)
) ENGINE=InnoDB AUTO_INCREMENT=556 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作品读者评分表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_work_version`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `sys_work_version` (
  `version_id` int NOT NULL AUTO_INCREMENT,
  `work_id` bigint NOT NULL,
  `version_no` varchar(20) COLLATE utf8mb4_general_ci NOT NULL,
  `title` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `content` longtext COLLATE utf8mb4_general_ci,
  `file_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `change_log` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `creator_id` bigint NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `is_current` tinyint DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL,
  PRIMARY KEY (`version_id`),
  KEY `idx_work` (`work_id`) USING BTREE,
  KEY `idx_creator` (`creator_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_creator_application`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `user_creator_application` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `user_id` bigint NOT NULL COMMENT '申请人用户ID',
  `pen_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '笔名',
  `direction` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '创作方向/擅长类型',
  `portfolio` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '代表作品',
  `intro` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '申请说明',
  `contact` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '联系方式',
  `status` varchar(32) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/APPROVED/REJECTED',
  `auditor_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `audited_at` datetime DEFAULT NULL COMMENT '审核时间',
  `reject_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '驳回原因',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'system' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_user_creator_application_user` (`user_id`,`status`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='创作者申请';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-30 10:28:15
