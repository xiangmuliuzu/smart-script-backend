package com.smartscript.platform.content.dto;

/**
 * 内容举报结果（B 模块 2.8.17 表 2-110）。
 *
 * 依据：云端 script_platform_dev 库 sys_report 表 + 接口文档表 2-110。
 *
 * 反推处理点：
 * 1. 契约输出 report_id / status；report_id 为 sys_report.report_id（bigint）。
 * 2. status 取新举报的初始态 'pending'（待处理）——库端该列无文档枚举，
 *    此处按「待处理」这一最小必要初值写入并原样回显，不代表已定义完整状态机。
 *
 * @author xiangsipeng
 */
public class AppReportResultDto
{
    /** 举报ID */
    private Long reportId;

    /** 状态（新举报初始为 pending） */
    private String status;

    public AppReportResultDto()
    {
    }

    public AppReportResultDto(Long reportId, String status)
    {
        this.reportId = reportId;
        this.status = status;
    }

    public Long getReportId()
    {
        return reportId;
    }

    public void setReportId(Long reportId)
    {
        this.reportId = reportId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}