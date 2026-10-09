package com.smartscript.platform.content.dto;

/**
 * 内容举报入参（B 模块 2.8.17 表 2-110）。
 *
 * 依据：云端 script_platform_dev 库 sys_report 表 + 接口文档表 2-110。
 *
 * 反推处理点：
 * 1. 契约输入 target_type / target_id / reason（必填）、description（可选）；
 *    sys_report 对应列 target_type varchar(30) / target_id bigint / reason varchar(50) /
 *    description varchar(500)，长度上限在控制层校验。
 * 2. 字段名兼容 snake_case（target_type / target_id）与 camelCase（targetType / targetId）
 *    两种写法，由 Jackson 的规范命名策略在控制层统一解析（见 AppReportController）。
 *
 * @author xiangsipeng
 */
public class AppReportRequest
{
    /** 举报对象类型（如 work / external_drama） */
    private String targetType;

    /** 举报对象ID */
    private Long targetId;

    /** 举报原因（≤50 字，落 sys_report.reason varchar(50)） */
    private String reason;

    /** 补充说明（可选，≤500 字，落 sys_report.description varchar(500)） */
    private String description;

    public String getTargetType()
    {
        return targetType;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
    }

    public Long getTargetId()
    {
        return targetId;
    }

    public void setTargetId(Long targetId)
    {
        this.targetId = targetId;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }
}