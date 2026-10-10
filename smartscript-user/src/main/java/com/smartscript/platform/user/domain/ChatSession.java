package com.smartscript.platform.user.domain;

import java.util.Date;

/**
 * A3 聊天会话（sys_chat_session）。
 *
 * 会话双方为「App 用户」与「管理员」，按业务上下文（作品/印章/订单等）隔离。
 * status：0=进行中 2=已结束，历史值 1 兼容为进行中。
 */
public class ChatSession
{
    private Long    sessionId;
    private String  sessionType;
    private Long    inquiryId;
    private Long    user1Id;
    private Long    user2Id;
    private String  lastMessage;
    private Date    lastMessageTime;
    private Integer user1Unread;
    private Integer user2Unread;
    private Integer status;
    private Long    assignedAdminId;
    private String  businessType;
    private Long    businessId;
    private String  businessName;
    private Date    createdAt;
    private Date    updatedAt;

    /* ---- 联表派生字段（非落库） ---- */
    private String  user1Name;
    private String  user2Name;
    private String  user1Avatar;
    private String  user2Avatar;

    public Long getSessionId()                  { return sessionId; }
    public void setSessionId(Long sessionId)    { this.sessionId = sessionId; }

    public String getSessionType()              { return sessionType; }
    public void setSessionType(String t)        { this.sessionType = t; }

    public Long getInquiryId()                  { return inquiryId; }
    public void setInquiryId(Long inquiryId)    { this.inquiryId = inquiryId; }

    public Long getUser1Id()                    { return user1Id; }
    public void setUser1Id(Long user1Id)        { this.user1Id = user1Id; }

    public Long getUser2Id()                    { return user2Id; }
    public void setUser2Id(Long user2Id)        { this.user2Id = user2Id; }

    public String getLastMessage()              { return lastMessage; }
    public void setLastMessage(String msg)      { this.lastMessage = msg; }

    public Date getLastMessageTime()            { return lastMessageTime; }
    public void setLastMessageTime(Date t)      { this.lastMessageTime = t; }

    public Integer getUser1Unread()             { return user1Unread; }
    public void setUser1Unread(Integer c)       { this.user1Unread = c; }

    public Integer getUser2Unread()             { return user2Unread; }
    public void setUser2Unread(Integer c)       { this.user2Unread = c; }

    public Integer getStatus()                  { return status; }
    public void setStatus(Integer status)       { this.status = status; }

    public Long getAssignedAdminId()            { return assignedAdminId; }
    public void setAssignedAdminId(Long id)     { this.assignedAdminId = id; }

    public String getBusinessType()             { return businessType; }
    public void setBusinessType(String t)       { this.businessType = t; }

    public Long getBusinessId()                 { return businessId; }
    public void setBusinessId(Long id)          { this.businessId = id; }

    public String getBusinessName()             { return businessName; }
    public void setBusinessName(String n)       { this.businessName = n; }

    public Date getCreatedAt()                  { return createdAt; }
    public void setCreatedAt(Date t)            { this.createdAt = t; }

    public Date getUpdatedAt()                  { return updatedAt; }
    public void setUpdatedAt(Date t)            { this.updatedAt = t; }

    public String getUser1Name()                { return user1Name; }
    public void setUser1Name(String n)          { this.user1Name = n; }

    public String getUser2Name()                { return user2Name; }
    public void setUser2Name(String n)          { this.user2Name = n; }

    public String getUser1Avatar()              { return user1Avatar; }
    public void setUser1Avatar(String a)        { this.user1Avatar = a; }

    public String getUser2Avatar()              { return user2Avatar; }
    public void setUser2Avatar(String a)        { this.user2Avatar = a; }
}
