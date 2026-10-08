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

    public String getMsgType()              { return msgType; }
    public void setMsgType(String t)        { this.msgType = t; }

    public String getContent()              { return content; }
    public void setContent(String c)        { this.content = c; }
}
