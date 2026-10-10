package com.smartscript.platform.content.constants;

/**
 * 版权管理常量
 * 
 * @author SmartScript
 */
public class CopyrightConstants
{
    /** 审核状态：待审核 */
    public static final String REVIEW_PENDING = "pending";
    
    /** 审核状态：已通过 */
    public static final String REVIEW_APPROVED = "approved";
    
    /** 审核状态：已驳回 */
    public static final String REVIEW_REJECTED = "rejected";

    /** 印章状态：已停用 */
    public static final String SEAL_DISABLED = "disabled";
    
    /** 印章状态：已启用 */
    public static final String SEAL_ENABLED = "enabled";
    
    /** 印章状态：异常 */
    public static final String SEAL_ABNORMAL = "abnormal";

    /** 证书状态：申请中 */
    public static final String CERT_PENDING = "pending";
    
    /** 证书状态：已签发 */
    public static final String CERT_ISSUED = "issued";
    
    /** 证书状态：已失效 */
    public static final String CERT_EXPIRED = "expired";

    /** 印章操作类型：审核通过 */
    public static final String OPERATION_REVIEW_APPROVE = "review_approve";
    
    /** 印章操作类型：审核驳回 */
    public static final String OPERATION_REVIEW_REJECT = "review_reject";
    
    /** 印章操作类型：启用 */
    public static final String OPERATION_ENABLE = "enable";
    
    /** 印章操作类型：停用 */
    public static final String OPERATION_DISABLE = "disable";
    
    /** 印章操作类型：处理异常 */
    public static final String OPERATION_RESOLVE_ABNORMAL = "resolve_abnormal";

    /** 审核原因最大长度 */
    public static final int REASON_MAX_LENGTH = 500;
    
    /** 关键词搜索最大长度 */
    public static final int KEYWORD_MAX_LENGTH = 100;
}
