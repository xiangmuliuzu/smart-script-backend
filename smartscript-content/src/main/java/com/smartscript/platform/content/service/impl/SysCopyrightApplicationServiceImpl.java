package com.smartscript.platform.content.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.DateUtils;
import com.smartscript.platform.content.domain.SysCopyrightApplication;
import com.smartscript.platform.content.domain.SysCopyrightCenterConfig;
import com.smartscript.platform.content.mapper.SysCopyrightApplicationMapper;
import com.smartscript.platform.content.service.ISysCopyrightApplicationService;
import com.smartscript.platform.content.service.ISysCopyrightCenterConfigService;
import com.smartscript.platform.content.service.ICopyrightCenterApiService;
import com.smartscript.platform.content.service.ISysCopyrightCertService;
import com.smartscript.platform.content.util.CopyrightStatusUtil;

/**
 * 版权申请Service业务层处理
 * 
 * 主要功能：
 * 1. 申请的CRUD操作
 * 2. 申请状态流转：pending → approved → submitted → reviewing → certificated/rejected
 * 3. 与第三方版权中心API对接
 * 4. 证书自动生成
 * 
 * @author SmartScript Team
 * @date 2024-01-15
 */
@Service
public class SysCopyrightApplicationServiceImpl implements ISysCopyrightApplicationService 
{
    private static final Logger log = LoggerFactory.getLogger(SysCopyrightApplicationServiceImpl.class);
    
    private static final String APPLY_NO_FORMAT = "CA%08d";
    private static final String DEFAULT_CENTER_UNASSIGNED = "未分配";
    private static final String DEFAULT_CENTER_UNKNOWN = "未知中心";

    @Autowired
    private SysCopyrightApplicationMapper copyrightApplicationMapper;

    @Autowired
    private ISysCopyrightCenterConfigService configService;

    @Autowired
    private ICopyrightCenterApiService copyrightCenterApiService;

    @Autowired
    private ISysCopyrightCertService copyrightCertService;

    @Override
    public SysCopyrightApplication selectCopyrightApplicationByApplyId(Long applyId)
    {
        SysCopyrightApplication app = copyrightApplicationMapper.selectCopyrightApplicationByApplyId(applyId);
        if (app != null) {
            enrichApplicationData(app);
        }
        return app;
    }

    @Override
    public List<SysCopyrightApplication> selectCopyrightApplicationList(SysCopyrightApplication copyrightApplication)
    {
        List<SysCopyrightApplication> list = copyrightApplicationMapper.selectCopyrightApplicationList(copyrightApplication);
        list.forEach(this::enrichApplicationData);
        return list;
    }
    
    /**
     * 填充申请的扩展数据
     * 提取公共逻辑，避免代码重复
     * 
     * @param app 版权申请对象
     */
    private void enrichApplicationData(SysCopyrightApplication app)
    {
        if (app.getApplyId() != null) {
            app.setApplyNo(String.format(APPLY_NO_FORMAT, app.getApplyId()));
        }
        
        app.setExternalApplyNo(app.getExternalNo());
        app.setStatusText(CopyrightStatusUtil.getStatusText(app.getStatus()));
        
        if (app.getCenterName() == null || app.getCenterName().isEmpty()) {
            app.setCenterName(app.getConfigId() == null ? DEFAULT_CENTER_UNASSIGNED : DEFAULT_CENTER_UNKNOWN);
        }
    }

    @Override
    public int insertCopyrightApplication(SysCopyrightApplication copyrightApplication)
    {
        copyrightApplication.setCreateTime(DateUtils.getNowDate());
        return copyrightApplicationMapper.insertCopyrightApplication(copyrightApplication);
    }

    @Override
    public int updateCopyrightApplication(SysCopyrightApplication copyrightApplication)
    {
        copyrightApplication.setUpdateTime(DateUtils.getNowDate());
        return copyrightApplicationMapper.updateCopyrightApplication(copyrightApplication);
    }

    @Override
    public int deleteCopyrightApplicationByApplyIds(Long[] applyIds)
    {
        return copyrightApplicationMapper.deleteCopyrightApplicationByApplyIds(applyIds);
    }

    @Override
    public int deleteCopyrightApplicationByApplyId(Long applyId)
    {
        return copyrightApplicationMapper.deleteCopyrightApplicationByApplyId(applyId);
    }

    @Override
    @Transactional
    public int submitToCenterConfig(Long applyId, Long configId)
    {
        SysCopyrightApplication application = copyrightApplicationMapper.selectCopyrightApplicationByApplyId(applyId);
        if (application == null) {
            throw new RuntimeException("申请不存在");
        }

        if (!CopyrightStatusUtil.STATUS_APPROVED.equals(application.getStatus())) {
            throw new RuntimeException("只有已审核通过的申请才能提交到版权中心");
        }

        SysCopyrightCenterConfig config = configService.selectCopyrightCenterConfigByConfigId(configId);
        if (config == null) {
            throw new RuntimeException("版权中心配置不存在");
        }

        if (!"enabled".equals(config.getStatus())) {
            throw new RuntimeException("版权中心未启用，请先在配置页启用该版权中心");
        }

        application.setConfigId(configId);
        application.setStatus(CopyrightStatusUtil.STATUS_SUBMITTED);
        application.setUpdateTime(new Date());
        application.setRemark(CopyrightStatusUtil.getDefaultRemark(CopyrightStatusUtil.STATUS_SUBMITTED));
        
        log.info("提交申请到版权中心配置页: applyId={}, configId={}, centerName={}", 
            applyId, configId, config.getCenterName());

        return copyrightApplicationMapper.updateCopyrightApplication(application);
    }

    @Override
    @Transactional
    public int submitApplicationToCenter(Long applyId)
    {
        SysCopyrightApplication application = copyrightApplicationMapper.selectCopyrightApplicationByApplyId(applyId);
        if (application == null) {
            throw new RuntimeException("申请不存在");
        }

        if (!CopyrightStatusUtil.STATUS_SUBMITTED.equals(application.getStatus())) {
            throw new RuntimeException("只有已提交到配置页的申请才能提交到第三方");
        }

        if (application.getConfigId() == null) {
            throw new RuntimeException("申请未分配到版权中心");
        }

        SysCopyrightCenterConfig config = configService.selectCopyrightCenterConfigByConfigId(application.getConfigId());
        if (config == null || !"enabled".equals(config.getStatus())) {
            throw new RuntimeException("版权中心配置未启用");
        }

        try {
            String externalNo = copyrightCenterApiService.submitApplication(
                application, 
                config.getApiUrl(), 
                config.getApiKey(), 
                config.getApiSecret()
            );

            application.setExternalNo(externalNo);
            application.setStatus(CopyrightStatusUtil.STATUS_REVIEWING);
            application.setSubmitTime(new Date());
            application.setUpdateTime(new Date());
            application.setRemark(CopyrightStatusUtil.getDefaultRemark(CopyrightStatusUtil.STATUS_REVIEWING));

            log.info("提交申请到第三方版权中心: applyId={}, externalNo={}", applyId, externalNo);

            return copyrightApplicationMapper.updateCopyrightApplication(application);
        }
        catch (Exception e) {
            log.error("提交版权申请到第三方失败", e);
            throw new RuntimeException("提交失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public int syncApplicationStatus(Long applyId)
    {
        SysCopyrightApplication application = copyrightApplicationMapper.selectCopyrightApplicationByApplyId(applyId);
        if (application == null) {
            throw new RuntimeException("申请不存在");
        }

        if (CopyrightStatusUtil.STATUS_PENDING.equals(application.getStatus())) {
            throw new RuntimeException("申请待审核，请先完成本地审核");
        }
        else if (CopyrightStatusUtil.STATUS_APPROVED.equals(application.getStatus()))
        {
            throw new RuntimeException("申请待提交，请先提交到版权中心配置页");
        }

        if (CopyrightStatusUtil.STATUS_CERTIFICATED.equals(application.getStatus()) 
            || CopyrightStatusUtil.STATUS_REJECTED.equals(application.getStatus())) {
            log.info("申请 {} 已处于终态 {}，无需再次查询状态", applyId, application.getStatus());
            return 1;
        }

        if (application.getExternalNo() == null || application.getExternalNo().isEmpty()) {
            log.info("申请 {} 处于 {} 状态，尚未提交到第三方，返回当前状态", applyId, application.getStatus());
            return 1;
        }

        if (application.getConfigId() == null) {
            throw new RuntimeException("申请未分配到版权中心");
        }

        SysCopyrightCenterConfig config = configService.selectCopyrightCenterConfigByConfigId(application.getConfigId());
        if (config == null) {
            throw new RuntimeException("版权中心配置不存在");
        }

        try {
            Map<String, Object> statusInfo = copyrightCenterApiService.queryApplicationStatus(
                application.getExternalNo(),
                config.getApiUrl(),
                config.getApiKey(),
                config.getApiSecret()
            );

            String status = (String) statusInfo.get("status");
            application.setStatus(status);
            application.setUpdateTime(new Date());
            application.setRemark(CopyrightStatusUtil.getDefaultRemark(status));

            if (CopyrightStatusUtil.STATUS_CERTIFICATED.equals(status)) {
                application.setCertNo((String) statusInfo.get("certNo"));
                application.setCertTime((Date) statusInfo.get("certTime"));
                application.setCertUrl((String) statusInfo.get("certUrl"));
            }
            else if (CopyrightStatusUtil.STATUS_REJECTED.equals(status)) {
                String rejectReason = (String) statusInfo.get("rejectReason");
                application.setRejectReason(rejectReason);
                if (rejectReason != null && !rejectReason.isEmpty()) {
                    application.setRemark("审核不通过：" + rejectReason);
                }
            }

            log.info("同步申请状态: applyId={}, status={}", applyId, status);

            int updateResult = copyrightApplicationMapper.updateCopyrightApplication(application);
            
            if (CopyrightStatusUtil.STATUS_CERTIFICATED.equals(status)) {
                try {
                    copyrightCertService.createCertFromApplication(application);
                    log.info("自动创建电子证书成功: applyId={}, certNo={}", applyId, application.getCertNo());
                }
                catch (Exception certEx) {
                    log.error("自动创建电子证书失败: applyId=" + applyId, certEx);
                    // 不抛出异常，避免影响状态同步
                }
            }
            
            return updateResult;
        }
        catch (Exception e)
        {
            log.error("同步版权申请状态失败", e);
            throw new RuntimeException("同步失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public int reviewApplication(Long applyId, String status, String rejectReason)
    {
        SysCopyrightApplication application = copyrightApplicationMapper.selectCopyrightApplicationByApplyId(applyId);
        if (application == null) {
            throw new RuntimeException("申请不存在");
        }

        if (!CopyrightStatusUtil.STATUS_PENDING.equals(application.getStatus())) {
            throw new RuntimeException("只有待审核状态的申请才能审核");
        }

        if (!CopyrightStatusUtil.STATUS_APPROVED.equals(status) 
            && !CopyrightStatusUtil.STATUS_REJECTED.equals(status)) {
            throw new RuntimeException("审核状态只能是approved或rejected");
        }

        if (CopyrightStatusUtil.STATUS_REJECTED.equals(status) 
            && (rejectReason == null || rejectReason.trim().isEmpty())) {
            throw new RuntimeException("驳回时必须填写驳回原因");
        }

        application.setStatus(status);
        application.setUpdateTime(new Date());

        if (CopyrightStatusUtil.STATUS_APPROVED.equals(status)) {
            application.setRemark(CopyrightStatusUtil.getDefaultRemark(CopyrightStatusUtil.STATUS_APPROVED));
            application.setRejectReason(null);
            log.info("审核通过申请: applyId={}", applyId);
        }
        else {
            application.setRejectReason(rejectReason);
            application.setRemark("审核驳回：" + rejectReason);
            log.info("审核驳回申请: applyId={}, reason={}", applyId, rejectReason);
        }

        return copyrightApplicationMapper.updateCopyrightApplication(application);
    }
}
