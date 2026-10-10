package com.smartscript.platform.content.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smartscript.platform.content.dto.AppReportRequest;
import com.smartscript.platform.content.dto.AppReportResultDto;
import com.smartscript.platform.content.mapper.AppReportMapper;
import com.smartscript.platform.content.service.IAppReportService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 内容举报 服务实现（App 2.8.17）
 *
 * 依据：接口文档表 2-110 + 云端 script_platform_dev 库 sys_report 表（附件5.1 表3-36）。
 *
 * 反推处理点：
 * 1. 身份不做空值兜底：本接口未登记 App 凭证域白名单，过滤器已保证非游客。
 * 2. report_id 在同事务内用 selectLastInsertId 取回：sys_report 无对应 domain 类，
 *    不为此新建 domain；同事务保证 insertReport 与 selectLastInsertId 走同一连接，
 *    避免跨连接取到他人 last_insert_id。
 * 3. status 写初始态 'pending'（库端该列无文档枚举，此处只定「待处理」这一最小初值）。
 *
 * @author xiangsipeng
 */
@Service
public class AppReportServiceImpl implements IAppReportService
{
    /** 新举报初始状态（库端无文档枚举，取待处理） */
    private static final String STATUS_PENDING = "pending";

    @Autowired
    private AppReportMapper reportMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppReportResultDto submitReport(AppReportRequest request)
    {
        reportMapper.insertReport(identityProvider.currentUserId(), request.getTargetType(),
                request.getTargetId(), request.getReason(), request.getDescription(), STATUS_PENDING);
        Long reportId = reportMapper.selectLastInsertId();
        return new AppReportResultDto(reportId, STATUS_PENDING);
    }
}