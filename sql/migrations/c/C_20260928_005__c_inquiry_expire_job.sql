-- =====================================================================
-- C_20260928_005__c_inquiry_expire_job.sql
-- 用途: 注册「询盘过期自动关闭」Quartz 定时任务（问题档案 P1-08）。
-- 适用: MySQL 8.0.x
-- 事务: 幂等；可重复执行，不产生重复任务。
--
-- 背景：
--   sys_inquiry.expire_at 到期后此前无任何自动关闭逻辑（P1-08）。
--   报价侧过期校验已在 acceptQuote / submitQuote 落地；本任务补齐询盘侧：
--   定时扫描 status ∈ {pending,accepted,quoted} 且 expire_at < now 的询盘，批量置 closed。
--
-- 调用目标：bean 名调用 tradeInquiryTask.closeExpiredInquiries
--   （com.smartscript.platform.trade.task.TradeInquiryTask，见 smartscript-trade 模块）。
--   注：若依 quartz 白名单 JOB_WHITELIST_STR 仅在 SysJobController 新增/编辑时校验，
--       运行期不校验；本脚本直接注册 sys_job，可正常调度，并可在 UI 暂停/恢复/立即执行。
--
-- 调度：每 10 分钟一次（cron 0 0/10 * * * ?）；禁止并发（concurrent=1）；
--       misfire 放弃执行（3）；status=0 正常启用。
--
-- 回滚: 见文件末尾「回滚指引」注释。
-- =====================================================================

SET NAMES utf8mb4;

SELECT 'C_INQUIRY_JOB_START' AS step, DATABASE() AS db_name, NOW() AS ts;

-- 幂等注册：invoke_target 已存在则跳过（job_id 由 auto_increment 分配）
INSERT INTO sys_job
  (job_name, job_group, invoke_target, cron_expression, misfire_policy,
   concurrent, status, create_by, create_time, remark)
SELECT 'C-询盘过期自动关闭', 'DEFAULT', 'tradeInquiryTask.closeExpiredInquiries',
       '0 0/10 * * * ?', '3', '1', '0', 'c-quartz-migration', NOW(),
       'P1-08：每10分钟扫描 expire_at 过期且非终态的询盘置 closed'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM sys_job WHERE invoke_target = 'tradeInquiryTask.closeExpiredInquiries'
);

-- 校验
SELECT 'C_INQUIRY_JOB_SUMMARY' AS step,
       (SELECT COUNT(*) FROM sys_job
         WHERE invoke_target = 'tradeInquiryTask.closeExpiredInquiries') AS job_registered;

SELECT job_id, job_name, job_group, invoke_target, cron_expression, concurrent, status
  FROM sys_job
 WHERE invoke_target = 'tradeInquiryTask.closeExpiredInquiries';

SELECT 'C_INQUIRY_JOB_DONE' AS step;

-- ---------------------------------------------------------------------
-- 回滚指引（如需撤销本迁移）：
--   DELETE FROM sys_job WHERE invoke_target = 'tradeInquiryTask.closeExpiredInquiries'
--     AND create_by = 'c-quartz-migration';
--   -- 若已产生执行日志，可一并清理：
--   -- DELETE FROM sys_job_log WHERE job_name = 'C-询盘过期自动关闭';
-- ---------------------------------------------------------------------
