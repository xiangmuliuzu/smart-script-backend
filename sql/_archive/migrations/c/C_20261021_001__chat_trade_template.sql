-- =====================================================================
-- C_20261021_001  交易会话定型文模板表
-- 买卖双方 TRADE 会话仅允许使用定型文消息，禁止自由打字。
-- =====================================================================

-- 1. 建表（幂等：先判表是否存在）
SET @tbl = 'sys_chat_trade_template';
SELECT COUNT(*) INTO @exists FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = @tbl;
SET @sql = IF(@exists = 0, CONCAT(
  'CREATE TABLE ', @tbl, ' (',
  '  template_id   BIGINT AUTO_INCREMENT PRIMARY KEY,',
  '  category      VARCHAR(32)  NOT NULL COMMENT ''分类：price_param/price_text/progress/general'',',
  '  content       VARCHAR(200) NOT NULL COMMENT ''定型文内容，含{price}占位符表示需填入金额'',',
  '  role          VARCHAR(16)  NOT NULL DEFAULT ''both'' COMMENT ''buyer/seller/both'',',
  '  has_amount    TINYINT      NOT NULL DEFAULT 0 COMMENT ''1=含{price}占位符，发送时须填入金额并同步更新报价'',',
  '  sort_order    INT          NOT NULL DEFAULT 0,',
  '  is_enabled    TINYINT      NOT NULL DEFAULT 1,',
  '  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP',
  ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''交易会话定型文模板'''),
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 清空旧数据（首次部署时幂等）
TRUNCATE TABLE sys_chat_trade_template;

-- 3. 插入 40 条定型文
-- ===== 价格协商（含价格输入 {price}）=====
INSERT INTO sys_chat_trade_template (category, content, role, has_amount, sort_order) VALUES
('price_param', '希望能便宜一些，¥{price}',               'buyer',  1, 1),
('price_param', '我的预算是 ¥{price}，能再降一些吗',       'buyer',  1, 2),
('price_param', '价格有点高，¥{price} 可以吗',             'buyer',  1, 3),
('price_param', '我出 ¥{price}，这是我的最终报价',         'seller', 1, 4),
('price_param', '¥{price} 可以接受吗',                     'seller', 1, 5),
('price_param', '最低 ¥{price}，不能再低了',               'seller', 1, 6),
('price_param', '¥{price} 的话我可以考虑接受',             'buyer',  1, 7),
('price_param', '这个价格 ¥{price} 你觉得怎么样',          'both',   1, 8);

-- ===== 价格协商（纯文字）=====
INSERT INTO sys_chat_trade_template (category, content, role, has_amount, sort_order) VALUES
('price_text', '这个价格可以接受',                         'both',   0, 20),
('price_text', '可以再优惠一些吗',                         'buyer',  0, 21),
('price_text', '价格已经很合理了',                         'buyer',  0, 22),
('price_text', '这个价格恐怕不行',                         'seller', 0, 23),
('price_text', '价格方面我们可以再商量',                   'seller', 0, 24),
('price_text', '价格不太合适，需要再谈谈',                 'both',   0, 25),
('price_text', '我对价格没什么异议',                       'both',   0, 26),
('price_text', '有没有什么优惠活动可以参加',               'buyer',  0, 27);

-- ===== 交易进展 =====
INSERT INTO sys_chat_trade_template (category, content, role, has_amount, sort_order) VALUES
('progress', '请问交割进度如何',                          'buyer',  0, 40),
('progress', '我正在准备交割材料，请稍等',                 'seller', 0, 41),
('progress', '请尽快确认，以便推进流程',                   'both',   0, 42),
('progress', '作品已经交付，请查收',                       'seller', 0, 43),
('progress', '我已确认收到，正在审核',                     'buyer',  0, 44),
('progress', '合同什么时候可以签署',                       'both',   0, 45),
('progress', '付款已提交，请确认',                         'buyer',  0, 46),
('progress', '作品有些问题需要修改',                       'buyer',  0, 47),
('progress', '修改已完成，请再次确认',                     'seller', 0, 48),
('progress', '交割材料已发送，请查看',                     'seller', 0, 49),
('progress', '我这边已经确认无误',                         'buyer',  0, 50),
('progress', '版权转让手续需要多久',                       'both',   0, 51);

-- ===== 通用沟通 =====
INSERT INTO sys_chat_trade_template (category, content, role, has_amount, sort_order) VALUES
('general', '感谢您的回复',                               'both',   0, 60),
('general', '我还需要再考虑一下',                         'both',   0, 61),
('general', '请问还有其他问题吗',                         'both',   0, 62),
('general', '好的，我明白了',                             'both',   0, 63),
('general', '请稍等，我确认一下',                         'both',   0, 64),
('general', '方便电话沟通吗',                             'both',   0, 65),
('general', '希望尽快回复，谢谢',                         'both',   0, 66),
('general', '您好，想咨询一下这个作品',                   'both',   0, 67),
('general', '收到，我处理一下',                           'both',   0, 68),
('general', '不好意思，回复晚了',                         'both',   0, 69),
('general', '合作愉快，期待下次交易',                     'both',   0, 70),
('general', '这个作品我很满意',                           'both',   0, 71);
