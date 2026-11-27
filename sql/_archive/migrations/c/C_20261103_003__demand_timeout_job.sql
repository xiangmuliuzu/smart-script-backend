-- =====================================================================
-- C_20261103_003：征集任务超时实时处置定时任务（幂等）
-- 需求：活动轮到点后不再等用户打开页面才判失败，改为秒级扫描主动终止；
--       发布者超过 24 小时未处理的延时申请由系统自动驳回（否则 pending 会一直暂停超时判定）。
-- 口径：判定与处置全部复用 tradeDemandTask.expireOverdueRounds（与读路径同一套方法），
--       终止走 CAS（仅 in_progress 生效）、通知按 requestId 幂等，故重复执行与多实例并发均安全。
-- 幂等：invoke_target 已存在则跳过，可重复执行。
-- =====================================================================

INSERT INTO sys_job
  (job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT 'C-任务超时实时处置', 'DEFAULT', 'tradeDemandTask.expireOverdueRounds',
       '0/10 * * * * ?', '3', '1', '0', 'c-quartz-migration', NOW(),
       '每10秒扫描：①驳回发布者超24小时未处理的延时申请 ②终止已过截止且仍在计时的活动轮(押金没收/托管退回发布者)'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_job WHERE invoke_target = 'tradeDemandTask.expireOverdueRounds');
