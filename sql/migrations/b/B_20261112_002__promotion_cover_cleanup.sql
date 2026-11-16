-- =====================================================================
-- B 模块内容 · 推广位：不锁定投放被覆盖后的历史数据清理
--
-- 规则：不锁定（exclusive=0）的运营投放与生效中的锁定/付费投放
--       （exclusive=1 且 status='paid'）时间窗重叠时，自动下架。
--       本次把已存在的重叠记录按新规则置为已下架。
--
-- 幂等：仅处理 status='paid' 的记录；末行输出 PASS/FAIL。
-- =====================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_general_ci;

UPDATE sys_promotion_application f
JOIN sys_promotion_application p
  ON p.slot_type = f.slot_type
 AND p.slot_no = f.slot_no
 AND p.exclusive = 1
 AND p.status = 'paid'
 AND p.start_time < f.end_time
 AND p.end_time > f.start_time
 AND p.start_time <= NOW()
 AND p.end_time >= NOW()
SET f.status = 'cancelled',
    f.remark = '被付费投放覆盖自动下架',
    f.update_time = NOW()
WHERE f.exclusive = 0
  AND f.status = 'paid'
  AND f.start_time <= NOW()
  AND f.end_time >= NOW();

-- verify：不应再存在「生效中不锁定投放 + 生效中锁定投放」的时间窗重叠
SET @fail = (
  SELECT COUNT(*)
  FROM sys_promotion_application f
  JOIN sys_promotion_application p
    ON p.slot_type = f.slot_type
   AND p.slot_no = f.slot_no
   AND p.exclusive = 1
   AND p.status = 'paid'
   AND p.start_time < f.end_time
   AND p.end_time > f.start_time
   AND p.start_time <= NOW()
   AND p.end_time >= NOW()
  WHERE f.exclusive = 0
    AND f.status = 'paid'
    AND f.start_time <= NOW()
    AND f.end_time >= NOW()
);

SELECT IF(@fail = 0, 'PASS', 'FAIL') AS result, @fail AS fail_cnt;
