-- 创作台账追加「重要道具档案」与「写法基线」两列
-- 背景：items 只记逐集去向，没有全书唯一的一份道具主数据，件数在 40 集后开始自相矛盾（11/12/13 面并存）；
--       写法没有任何落库位置，模型中途换叙述体（括注体/心理独白）读者会觉得换了写手。
-- 兼容：仅新增可空列，不改既有数据；历史作品的两列为空时，前端与摘要接口按「未建立」处理。
-- 说明：dev 库已于 2026-11-11 手工执行，本脚本供其它环境复现。

ALTER TABLE sys_work_ledger
    ADD COLUMN props LONGTEXT NULL COMMENT '重要道具档案：名称｜件数｜持有人｜状态｜外观' AFTER items;

ALTER TABLE sys_work_ledger
    ADD COLUMN style_guide LONGTEXT NULL COMMENT '写法基线：人称/句式/对白比例/标点/禁用写法' AFTER props;
