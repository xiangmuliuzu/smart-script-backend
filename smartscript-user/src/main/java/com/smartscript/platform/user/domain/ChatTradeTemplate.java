package com.smartscript.platform.user.domain;

import java.util.Date;

/**
 * 交易会话定型文模板（sys_chat_trade_template）。
 *
 * TRADE 会话中买卖双方仅允许发送定型文消息，禁止自由打字。
 * content 含 {price} 占位符时表示需填入金额并同步更新报价。
 */
public class ChatTradeTemplate
{
    private Long    templateId;
    private String  category;
    private String  content;
    private String  role;
    private Integer hasAmount;
    private Integer sortOrder;
    private Integer isEnabled;
    private Date    createTime;

    public Long getTemplateId()                 { return templateId; }
    public void setTemplateId(Long id)          { this.templateId = id; }

    public String getCategory()                 { return category; }
    public void setCategory(String c)           { this.category = c; }

    public String getContent()                  { return content; }
    public void setContent(String c)            { this.content = c; }

    public String getRole()                     { return role; }
    public void setRole(String r)               { this.role = r; }

    public Integer getHasAmount()               { return hasAmount; }
    public void setHasAmount(Integer a)         { this.hasAmount = a; }

    public Integer getSortOrder()               { return sortOrder; }
    public void setSortOrder(Integer o)         { this.sortOrder = o; }

    public Integer getIsEnabled()               { return isEnabled; }
    public void setIsEnabled(Integer e)         { this.isEnabled = e; }

    public Date getCreateTime()                 { return createTime; }
    public void setCreateTime(Date t)           { this.createTime = t; }
}
