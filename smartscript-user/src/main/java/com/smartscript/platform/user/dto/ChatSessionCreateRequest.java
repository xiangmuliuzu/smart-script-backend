package com.smartscript.platform.user.dto;

/**
 * A3 创建/获取会话请求。
 *
 * 前端传入 businessType + businessId 定位业务上下文；
 * 后端以 (user1_id, user2_id, business_type, business_id) 唯一键保证幂等。
 */
public class ChatSessionCreateRequest
{
    /** 业务类型：WORK / SEAL / COPYRIGHT / ORDER / GENERAL */
    private String businessType;
    /** 业务 ID */
    private Long   businessId;
    /** 业务名称（冗余快照，方便列表展示） */
    private String businessName;
    /** 目标管理员 user_id（管理员端发起时传；用户端发起时可选） */
    private Long   targetAdminId;
    /** 目标 App 用户 user_id（管理端主动联系用户时传） */
    private Long   targetUserId;

    public String getBusinessType()             { return businessType; }
    public void setBusinessType(String t)       { this.businessType = t; }

    public Long getBusinessId()                 { return businessId; }
    public void setBusinessId(Long id)          { this.businessId = id; }

    public String getBusinessName()             { return businessName; }
    public void setBusinessName(String n)       { this.businessName = n; }

    public Long getTargetAdminId()              { return targetAdminId; }
    public void setTargetAdminId(Long id)       { this.targetAdminId = id; }

    public Long getTargetUserId()               { return targetUserId; }
    public void setTargetUserId(Long id)        { this.targetUserId = id; }
}
