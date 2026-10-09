package com.smartscript.platform.content.service;

import com.smartscript.platform.content.dto.AppReportRequest;
import com.smartscript.platform.content.dto.AppReportResultDto;

/**
 * 内容举报 服务（App 2.8.17）
 *
 * 依据：云端 script_platform_dev 库 sys_report 表 + 接口文档表 2-110。
 *
 * 边界：私有接口，举报人一律取当前登录身份，请求体不接受 userId；
 * 本批只做提交，不做举报进度回查（无对应契约）。
 *
 * @author xiangsipeng
 */
public interface IAppReportService
{
    /**
     * 提交举报（接口 2.8.17）
     *
     * @param request 举报内容（targetType / targetId / reason 必填，已由控制层校验长度与空值）
     * @return {reportId, status}（status 为初始态 pending）
     */
    public AppReportResultDto submitReport(AppReportRequest request);
}