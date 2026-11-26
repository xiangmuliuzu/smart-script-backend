-- 台账追加 outline（剧本大纲 / 分集事件卡），跨端共用。
-- 之前大纲只存在 PC 端 localStorage 草稿，换浏览器或换设备后大纲为空，
-- 续写并入时会把已有集号当成空大纲处理，导致 1..N 集丢失。
alter table sys_work_ledger add column outline longtext null comment '剧本大纲（分集事件卡）' after style_guide;
