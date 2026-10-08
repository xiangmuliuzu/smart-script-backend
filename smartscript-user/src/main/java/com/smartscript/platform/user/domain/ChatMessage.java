package com.smartscript.platform.user.domain;

import java.util.Date;

/**
 * A3 聊天消息（sys_chat_message）。
 *
 * msg_type 第一版仅 TEXT；预留 IMAGE / FILE 扩展位。
 */
public class ChatMessage
{
    private Long    messageId;
    private Long    sessionId;
    private Long    senderId;
    private Long    receiverId;
    private String  msgType;
    private String  content;
    private String  fileUrl;
    private Boolean isRead;
    private Date    readTime;
    private Date    createdAt;

    /* ---- 联表派生字段 ---- */
    private String  senderName;
    private String  senderAvatar;

    public Long getMessageId()                  { return messageId; }
    public void setMessageId(Long id)           { this.messageId = id; }

    public Long getSessionId()                  { return sessionId; }
    public void setSessionId(Long id)           { this.sessionId = id; }

    public Long getSenderId()                   { return senderId; }
    public void setSenderId(Long id)            { this.senderId = id; }

    public Long getReceiverId()                 { return receiverId; }
    public void setReceiverId(Long id)          { this.receiverId = id; }

    public String getMsgType()                  { return msgType; }
    public void setMsgType(String t)            { this.msgType = t; }

    public String getContent()                  { return content; }
    public void setContent(String c)            { this.content = c; }

    public String getFileUrl()                  { return fileUrl; }
    public void setFileUrl(String u)            { this.fileUrl = u; }

    public Boolean getIsRead()                  { return isRead; }
    public void setIsRead(Boolean r)            { this.isRead = r; }

    public Date getReadTime()                   { return readTime; }
    public void setReadTime(Date t)             { this.readTime = t; }

    public Date getCreatedAt()                  { return createdAt; }
    public void setCreatedAt(Date t)            { this.createdAt = t; }

    public String getSenderName()               { return senderName; }
    public void setSenderName(String n)         { this.senderName = n; }

    public String getSenderAvatar()             { return senderAvatar; }
    public void setSenderAvatar(String a)       { this.senderAvatar = a; }
}
