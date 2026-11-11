package com.smartscript.platform.content.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 版权申请状态工具类
 * 提供状态码与中文描述、备注信息的映射
 * 
 * 状态流转：
 * unpaid → reviewing → need_supplement ⇄ reviewing
 *                  → supplement_overdue → rejected
 *                  → certificated | rejected
 * 历史：pending/approved/submitted 对 App 仍展示为审核中。
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
    public static final String STATUS_NEED_SUPPLEMENT = "need_supplement";
    public static final String STATUS_SUPPLEMENT_OVERDUE = "supplement_overdue";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_CERTIFICATED = "certificated";
    /** App 列表展示用：待支付单尚未履约（非申请表真实状态） */
    public static final String STATUS_UNPAID = "unpaid";

    /** 补正默认天数（国版常见 30 天） */
    public static final int SUPPLEMENT_DEADLINE_DAYS = 30;
    
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
        textMap.put(STATUS_NEED_SUPPLEMENT, "待补正");
        textMap.put(STATUS_SUPPLEMENT_OVERDUE, "逾期待驳回");
        textMap.put(STATUS_REJECTED, "已拒绝");
        textMap.put(STATUS_CERTIFICATED, "已发证");
        textMap.put(STATUS_UNPAID, "待付款");
        STATUS_TEXT_MAP = Collections.unmodifiableMap(textMap);
        
        Map<String, String> remarkMap = new HashMap<>();
        remarkMap.put(STATUS_PENDING, "待本地审核");
        remarkMap.put(STATUS_APPROVED, "待提交到版权中心");
        remarkMap.put(STATUS_SUBMITTED, "已分配到版权中心配置页，等待提交到第三方API");
        remarkMap.put(STATUS_REVIEWING, "已提交到第三方API，等待审核");
        remarkMap.put(STATUS_NEED_SUPPLEMENT, "版权中心要求补充材料，请在截止日前补正（不重交官费）");
        remarkMap.put(STATUS_SUPPLEMENT_OVERDUE, "补正已超时，本轮可能不予登记");
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

    /**
     * App 端展示状态：历史 pending/approved/submitted 统一展示为审核中。
     */
    public static String toDisplayStatus(String status)
    {
        if (STATUS_UNPAID.equals(status)
                || STATUS_NEED_SUPPLEMENT.equals(status)
                || STATUS_SUPPLEMENT_OVERDUE.equals(status)
                || STATUS_CERTIFICATED.equals(status)
                || STATUS_REJECTED.equals(status))
        {
            return status;
        }
        if (STATUS_PENDING.equals(status)
                || STATUS_APPROVED.equals(status)
                || STATUS_SUBMITTED.equals(status)
                || STATUS_REVIEWING.equals(status))
        {
            return STATUS_REVIEWING;
        }
        return status;
    }

    /**
     * App 端展示文案。
     */
    public static String toDisplayStatusText(String status)
    {
        String display = toDisplayStatus(status);
        if (STATUS_REVIEWING.equals(display))
        {
            return "审核中";
        }
        return getStatusText(display);
    }

    /** 是否算「进行中」（禁止同作品再建支付单） */
    public static boolean isActiveApplicationStatus(String status)
    {
        return STATUS_PENDING.equals(status)
                || STATUS_APPROVED.equals(status)
                || STATUS_SUBMITTED.equals(status)
                || STATUS_REVIEWING.equals(status)
                || STATUS_NEED_SUPPLEMENT.equals(status)
                || STATUS_SUPPLEMENT_OVERDUE.equals(status);
    }
}
