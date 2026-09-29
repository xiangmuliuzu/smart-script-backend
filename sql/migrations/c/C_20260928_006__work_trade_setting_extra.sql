-- C 模块：交易设置扩展（分工 PC2 置顶/推荐/排序）
-- 为 sys_work 增加交易侧设置列；均为可空/带默认值的向后兼容变更，不影响内容模块既有读写。
ALTER TABLE sys_work
    ADD COLUMN is_top       TINYINT NOT NULL DEFAULT 0 COMMENT '交易设置：是否置顶 0否 1是',
    ADD COLUMN is_recommend TINYINT NOT NULL DEFAULT 0 COMMENT '交易设置：是否推荐 0否 1是',
    ADD COLUMN sort_order   INT     NOT NULL DEFAULT 0 COMMENT '交易设置：手动排序权重（升序）';

-- 收口验证
SELECT COLUMN_NAME, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sys_work'
  AND COLUMN_NAME IN ('is_top', 'is_recommend', 'sort_order');
