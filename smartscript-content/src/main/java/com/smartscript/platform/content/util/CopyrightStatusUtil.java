package com.smartscript.platform.content.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 版权申请状态工具类
 * 提供状态码与中文描述、备注信息的映射
 * 
 * 状态流转：
 * pending(待审核) → approved(待提交) → submitted(待提交到第三方) 
 * → reviewing(审核中) → certificated(已发证) | rejected(已拒绝)
 * 
 * @author SmartScript Team
 * @date 2024-01
 */
public class CopyrightStatusUtil 
{
    // 状态常量
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_SUBMITTED = "submitted";
    public static final String STATUS_REVIEWING = "reviewing";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_CERTIFICATED = "certificated";
    
    /** 状态码到中文映射（不可变） */
    private static final Map<String, String> STATUS_TEXT_MAP;
    
    /** 状态到默认备注映射（不可变） */
    private static final Map<String, String> STATUS_REMARK_MAP;
    
    static {
        Map<String, String> textMap = new HashMap<>();
        textMap.put(STATUS_PENDING, "待审核");
        textMap.put(STATUS_APPROVED, "待提交");
        textMap.put(STATUS_SUBMITTED, "待提交到第三方");
        textMap.put(STATUS_REVIEWING, "审核中");
        textMap.put(STATUS_REJECTED, "已拒绝");
        textMap.put(STATUS_CERTIFICATED, "已发证");
        STATUS_TEXT_MAP = Collections.unmodifiableMap(textMap);
        
        Map<String, String> remarkMap = new HashMap<>();
        remarkMap.put(STATUS_PENDING, "待本地审核");
        remarkMap.put(STATUS_APPROVED, "待提交到版权中心");
        remarkMap.put(STATUS_SUBMITTED, "已分配到版权中心配置页，等待提交到第三方API");
        remarkMap.put(STATUS_REVIEWING, "已提交到第三方API，等待审核");
        remarkMap.put(STATUS_REJECTED, "第三方审核驳回");
        remarkMap.put(STATUS_CERTIFICATED, "已通过第三方审核并发证");
        STATUS_REMARK_MAP = Collections.unmodifiableMap(remarkMap);
    }
    
    private CopyrightStatusUtil() {
        // 工具类不允许实例化
    }
    
    /**
     * 获取状态中文描述
     * 
     * @param status 状态码
     * @return 中文描述，若状态码不存在则返回原状态码
     */
    public static String getStatusText(String status) 
    {
        return STATUS_TEXT_MAP.getOrDefault(status, status);
    }
    
    /**
     * 获取状态对应的默认备注
     * 
     * @param status 状态码
     * @return 默认备注，若状态码不存在则返回空字符串
     */
    public static String getDefaultRemark(String status) 
    {
        return STATUS_REMARK_MAP.getOrDefault(status, "");
    }
    
    /**
     * 根据config_id调整备注（已分配到版权中心）
     * 
     * @param status 状态码
     * @param hasConfigId 是否已分配版权中心
     * @return 调整后的备注
     */
    public static String getRemarkByStatus(String status, boolean hasConfigId) 
    {
        if (STATUS_APPROVED.equals(status) && hasConfigId) {
            return "已分配到版权中心，待提交到第三方API";
        }
        return getDefaultRemark(status);
    }
    
    /**
     * 检查状态是否有效
     * 
     * @param status 状态码
     * @return 是否为有效状态
     */
    public static boolean isValidStatus(String status) 
    {
        return STATUS_TEXT_MAP.containsKey(status);
    }
}
