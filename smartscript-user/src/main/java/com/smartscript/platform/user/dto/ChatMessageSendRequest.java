package com.smartscript.platform.user.dto;

/**
 * A3 发送消息请求。第一版仅支持 TEXT。
 */
public class ChatMessageSendRequest
{
    /** 消息类型：TEXT（默认） */
    private String msgType;
    /** 消息正文 */
    private String content;
    /** 被引用的消息ID（可空；须为同会话内未撤回的 TEXT 消息） */
    private Long   quoteMessageId;

    public String getMsgType()              { return msgType; }
    public void setMsgType(String t)        { this.msgType = t; }

    public String getContent()              { return content; }
    public void setContent(String c)        { this.content = c; }

    public Long getQuoteMessageId()         { return quoteMessageId; }
    public void setQuoteMessageId(Long id)  { this.quoteMessageId = id; }
}
