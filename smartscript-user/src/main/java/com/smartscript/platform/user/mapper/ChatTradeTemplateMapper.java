package com.smartscript.platform.user.mapper;

import java.util.List;
import com.smartscript.platform.user.domain.ChatTradeTemplate;

/**
 * 交易会话定型文模板 Mapper。
 */
public interface ChatTradeTemplateMapper
{
    /** 查询所有已启用的定型文模板（按 sort_order 排序）。 */
    List<ChatTradeTemplate> selectEnabledTemplates();
}
