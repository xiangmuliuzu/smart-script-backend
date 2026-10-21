package com.smartscript.platform.content.service.impl;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.content.domain.*;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.copyright.*;
import com.smartscript.platform.content.mapper.*;
import com.smartscript.platform.content.service.*;
import com.ruoyi.common.utils.StringUtils;

/**
 * App 端版权管理服务实现。
 */
@Service
public class AppCopyrightServiceImpl implements IAppCopyrightService
{
    private static final Logger log = LoggerFactory.getLogger(AppCopyrightServiceImpl.class);

    private final ISysCopyrightApplicationService copyrightApplicationService;
    private final ISysCopyrightCertService copyrightCertService;
    private final ISysCopyrightCenterConfigService centerConfigService;
    private final ISysWorkService workService;

    public AppCopyrightServiceImpl(
            ISysCopyrightApplicationService copyrightApplicationService,
            ISysCopyrightCertService copyrightCertService,
            ISysCopyrightCenterConfigService centerConfigService,
            ISysWorkService workService)
    {
        this.copyrightApplicationService = copyrightApplicationService;
        this.copyrightCertService = copyrightCertService;
        this.centerConfigService = centerConfigService;
        this.workService = workService;
    }

    @Override
    public AppPageResult<AppCopyrightApplicationDto> getMyApplications(
            Long userId, String status, String workName, Integer pageNum, Integer pageSize)
    {
        log.info("查询用户版权申请列表: userId={}, status={}, workName={}, pageNum={}, pageSize={}", 
                 userId, status, workName, pageNum, pageSize);

        // 构建查询条件
        SysCopyrightApplication query = new SysCopyrightApplication();
        query.setUserId(userId);
        if (StringUtils.isNotBlank(status))
        {
            query.setStatus(status);
        }
        if (StringUtils.isNotBlank(workName))
        {
            query.setWorkName(workName);
        }

        // 安全处理分页参数
        int safePageNum = (pageNum != null && pageNum > 0) ? pageNum : 1;
        int safePageSize = (pageSize != null && pageSize > 0) ? pageSize : 10;

        // 启动分页
        PageHelper.startPage(safePageNum, safePageSize);
        List<SysCopyrightApplication> list = copyrightApplicationService.selectCopyrightApplicationList(query);
        PageInfo<SysCopyrightApplication> pageInfo = new PageInfo<>(list);

        log.info("数据库查询结果: total={}, listSize={}", pageInfo.getTotal(), list.size());

        // 转换 DTO
        List<AppCopyrightApplicationDto> dtoList = list.stream()
                .map(this::convertToApplicationDto)
                .collect(Collectors.toList());

        log.info("DTO转换完成: dtoListSize={}", dtoList.size());

        return new AppPageResult<>((int) pageInfo.getTotal(), dtoList);
    }

    @Override
    public AppCopyrightApplicationDto getApplicationDetail(Long applyId, Long userId)
    {
        log.info("查询版权申请详情: applyId={}, userId={}", applyId, userId);

        SysCopyrightApplication application = copyrightApplicationService.selectCopyrightApplicationByApplyId(applyId);
        if (application == null || !userId.equals(application.getUserId()))
        {
            return null;
        }

        return convertToApplicationDto(application);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitApplication(Long userId, AppCopyrightApplyRequest request)
    {
        log.info("提交版权申请: userId={}, workId={}", userId, request.getWorkId());

        // 验证作品是否存在且属于该用户
        SysWork work = workService.selectWorkById(request.getWorkId());
        if (work == null)
        {
            throw new IllegalArgumentException("作品不存在");
        }
        if (!userId.equals(work.getAuthorId()))
        {
            throw new IllegalArgumentException("无权限申请该作品版权");
        }

        // 检查是否已有进行中的申请
        SysCopyrightApplication existQuery = new SysCopyrightApplication();
        existQuery.setUserId(userId);
        existQuery.setWorkId(request.getWorkId());
        List<SysCopyrightApplication> existList = copyrightApplicationService
                .selectCopyrightApplicationList(existQuery);
        
        for (SysCopyrightApplication exist : existList)
        {
            String status = exist.getStatus();
            if ("pending".equals(status) || "approved".equals(status) || 
                "submitted".equals(status) || "reviewing".equals(status))
            {
                throw new IllegalArgumentException("该作品已有进行中的版权申请");
            }
        }

        // 验证版权中心配置（通过 centerCode 查找 configId）
        SysCopyrightCenterConfig centerConfig = findCenterByCode(request.getCopyrightCenter());
        if (centerConfig == null || !"enabled".equals(centerConfig.getStatus()))
        {
            throw new IllegalArgumentException("版权中心不可用");
        }

        // 创建申请记录（包含所有必填字段）
        SysCopyrightApplication application = new SysCopyrightApplication();
        application.setUserId(userId);
        application.setWorkId(request.getWorkId());
        application.setConfigId(centerConfig.getConfigId());
        application.setCopyrightCenter(centerConfig.getCenterCode());  // 版权中心代码（必填）
        application.setStatus("pending");
        application.setSyncStatus("pending");  // 同步状态（必填）
        
        // 生成申请编号（必填字段）
        String applyNo = "CA" + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
        application.setApplyNo(applyNo);
        
        // 设置提交时间（必填字段）
        application.setSubmitTime(new Date());
        application.setCreateTime(new Date());
        application.setRemark(request.getRemark());
        
        // 保存申请材料文件URL
        if (request.getFileUrl() != null && !request.getFileUrl().isEmpty()) {
            application.setAttachmentUrl(request.getFileUrl());
        }
        
        // 保存分类材料（新版本）
        if (request.getOriginalityProofUrls() != null && !request.getOriginalityProofUrls().isEmpty()) {
            application.setOriginalityProof(request.getOriginalityProofUrls());
        }
        if (request.getAuthorIdentityDocUrls() != null && !request.getAuthorIdentityDocUrls().isEmpty()) {
            application.setAuthorIdentityDoc(request.getAuthorIdentityDocUrls());
        }
        if (request.getCreationProcessUrls() != null && !request.getCreationProcessUrls().isEmpty()) {
            application.setCreationProcess(request.getCreationProcessUrls());
        }
        if (request.getOtherProofUrls() != null && !request.getOtherProofUrls().isEmpty()) {
            application.setOtherProof(request.getOtherProofUrls());
        }

        copyrightApplicationService.insertCopyrightApplication(application);
        log.info("版权申请创建成功: applyId={}, applyNo={}", application.getApplyId(), applyNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmitApplication(Long userId, AppCopyrightResubmitRequest request)
    {
        log.info("重新提交版权申请: userId={}, originalApplyId={}", userId, request.getOriginalApplyId());

        // 查询原申请
        SysCopyrightApplication originalApplication = copyrightApplicationService
                .selectCopyrightApplicationByApplyId(request.getOriginalApplyId());
        
        if (originalApplication == null)
        {
            throw new IllegalArgumentException("原申请记录不存在");
        }
        if (!userId.equals(originalApplication.getUserId()))
        {
            throw new IllegalArgumentException("无权限操作该申请");
        }
        if (!"rejected".equals(originalApplication.getStatus()))
        {
            throw new IllegalArgumentException("只有已驳回的申请才能重新提交");
        }

        // 创建新申请记录（包含所有必填字段）
        SysCopyrightApplication newApplication = new SysCopyrightApplication();
        newApplication.setUserId(originalApplication.getUserId());
        newApplication.setWorkId(originalApplication.getWorkId());
        newApplication.setConfigId(originalApplication.getConfigId());
        newApplication.setCopyrightCenter(originalApplication.getCopyrightCenter());  // 版权中心代码（必填）
        newApplication.setStatus("pending");
        newApplication.setSyncStatus("pending");  // 同步状态（必填）
        
        // 生成新的申请编号（必填字段）
        String applyNo = "CA" + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
        newApplication.setApplyNo(applyNo);
        
        // 设置提交时间（必填字段）
        newApplication.setSubmitTime(new Date());
        newApplication.setCreateTime(new Date());
        newApplication.setRemark(request.getRemark());
        
        newApplication.setExternalNo(null);  // 清空外部申请号
        newApplication.setRejectReason(null); // 清空拒绝原因

        copyrightApplicationService.insertCopyrightApplication(newApplication);
        log.info("重新提交成功: applyId={}, applyNo={}", newApplication.getApplyId(), applyNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApplication(Long applyId, Long userId)
    {
        log.info("删除版权申请: applyId={}, userId={}", applyId, userId);

        // 查询申请记录
        SysCopyrightApplication application = copyrightApplicationService.selectCopyrightApplicationByApplyId(applyId);
        if (application == null)
        {
            throw new IllegalArgumentException("申请记录不存在");
        }
        if (!userId.equals(application.getUserId()))
        {
            throw new IllegalArgumentException("无权限删除该申请");
        }
        if (!"pending".equals(application.getStatus()))
        {
            throw new IllegalArgumentException("只有待审核状态的申请才能删除");
        }

        // 执行删除
        copyrightApplicationService.deleteCopyrightApplicationByApplyId(applyId);
        log.info("删除版权申请成功: applyId={}", applyId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateApplication(Long applyId, Long userId, AppCopyrightApplyRequest request)
    {
        log.info("更新版权申请: applyId={}, userId={}, workId={}", applyId, userId, request.getWorkId());

        // 查询申请记录
        SysCopyrightApplication application = copyrightApplicationService.selectCopyrightApplicationByApplyId(applyId);
        if (application == null)
        {
            throw new IllegalArgumentException("申请记录不存在");
        }
        if (!userId.equals(application.getUserId()))
        {
            throw new IllegalArgumentException("无权限修改该申请");
        }
        if (!"pending".equals(application.getStatus()))
        {
            throw new IllegalArgumentException("只有待审核状态的申请才能修改");
        }

        // 验证作品是否存在且属于该用户
        SysWork work = workService.selectWorkById(request.getWorkId());
        if (work == null)
        {
            throw new IllegalArgumentException("作品不存在");
        }
        if (!userId.equals(work.getAuthorId()))
        {
            throw new IllegalArgumentException("无权限申请该作品版权");
        }

        // 验证版权中心配置
        SysCopyrightCenterConfig centerConfig = findCenterByCode(request.getCopyrightCenter());
        if (centerConfig == null || !"enabled".equals(centerConfig.getStatus()))
        {
            throw new IllegalArgumentException("版权中心不可用");
        }

        // 更新申请记录
        application.setWorkId(request.getWorkId());
        application.setConfigId(centerConfig.getConfigId());
        application.setCopyrightCenter(centerConfig.getCenterCode());
        application.setRemark(request.getRemark());
        
        // 更新申请材料文件URL
        if (request.getFileUrl() != null && !request.getFileUrl().isEmpty()) {
            application.setAttachmentUrl(request.getFileUrl());
        }

        copyrightApplicationService.updateCopyrightApplication(application);
        log.info("更新版权申请成功: applyId={}", applyId);
    }

    @Override
    public AppPageResult<AppCopyrightCertDto> getMyCertificates(
            Long userId, String workName, Integer pageNum, Integer pageSize)
    {
        log.info("查询用户版权证书列表: userId={}, workName={}", userId, workName);

        // 安全处理分页参数
        int safePageNum = (pageNum != null && pageNum > 0) ? pageNum : 1;
        int safePageSize = (pageSize != null && pageSize > 0) ? pageSize : 10;

        // 分页查询 - 使用按userId过滤的方法
        PageHelper.startPage(safePageNum, safePageSize);
        List<SysCopyrightCert> list = copyrightCertService.selectCertListByUserId(userId, null, workName);
        PageInfo<SysCopyrightCert> pageInfo = new PageInfo<>(list);

        log.info("查询结果: total={}, listSize={}", pageInfo.getTotal(), list.size());

        // 转换 DTO
        List<AppCopyrightCertDto> dtoList = list.stream()
                .map(this::convertToCertDto)
                .collect(Collectors.toList());

        return new AppPageResult<>((int) pageInfo.getTotal(), dtoList);
    }

    @Override
    public AppCopyrightCertDto getCertificateDetail(Long certId, Long userId)
    {
        log.info("查询版权证书详情: certId={}, userId={}", certId, userId);

        SysCopyrightCert cert = copyrightCertService.selectCertById(certId);
        if (cert == null || !userId.equals(cert.getUserId()))
        {
            return null;
        }

        return convertToCertDto(cert);
    }

    @Override
    public AppCopyrightCertDownloadDto downloadCertificate(Long certId, Long userId)
    {
        log.info("下载版权证书: certId={}, userId={}", certId, userId);

        SysCopyrightCert cert = copyrightCertService.selectCertById(certId);
        if (cert == null || !userId.equals(cert.getUserId()))
        {
            return null;
        }

        if (StringUtils.isBlank(cert.getCertUrl()))
        {
            throw new IllegalArgumentException("证书文件不存在");
        }

        return new AppCopyrightCertDownloadDto(cert.getCertUrl());
    }

    @Override
    public List<AppCopyrightCenterDto> getActiveCenters()
    {
        log.info("查询可用版权中心列表");

        List<SysCopyrightCenterConfig> configs = centerConfigService.getActiveCenterList();
        
        return configs.stream()
                .map(config -> new AppCopyrightCenterDto(config.getCenterCode(), config.getCenterName()))
                .collect(Collectors.toList());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 转换为申请 DTO。
     */
    private AppCopyrightApplicationDto convertToApplicationDto(SysCopyrightApplication application)
    {
        AppCopyrightApplicationDto dto = new AppCopyrightApplicationDto();
        BeanUtils.copyProperties(application, dto);
        
        // 补充版权中心名称
        if (StringUtils.isNotBlank(application.getCopyrightCenter()))
        {
            SysCopyrightCenterConfig config = findCenterByCode(application.getCopyrightCenter());
            if (config != null)
            {
                dto.setCenterName(config.getCenterName());
            }
        }
        
        // 补充作品详细信息
        if (application.getWorkId() != null)
        {
            SysWork work = workService.selectWorkById(application.getWorkId());
            if (work != null)
            {
                dto.setWorkType(work.getWorkType());
                // 注：SysWork中字段名为summary、cover等，非category、intro等
                dto.setWorkSummary(work.getSummary());
                dto.setWorkCoverUrl(work.getCover());
            }
        }
        
        // 补充申请材料URL（从application的字段读取并映射到DTO）
        if (StringUtils.isNotBlank(application.getAttachmentUrl())) {
            dto.setWorkFileUrl(application.getAttachmentUrl());
        }
        if (StringUtils.isNotBlank(application.getOriginalityProof())) {
            dto.setOriginalityProof(application.getOriginalityProof());
        }
        if (StringUtils.isNotBlank(application.getAuthorIdentityDoc())) {
            dto.setAuthorIdentityDoc(application.getAuthorIdentityDoc());
        }
        if (StringUtils.isNotBlank(application.getCreationProcess())) {
            dto.setCreationProcess(application.getCreationProcess());
        }
        if (StringUtils.isNotBlank(application.getOtherProof())) {
            dto.setOtherProof(application.getOtherProof());
        }
        
        return dto;
    }

    /**
     * 根据代码查找版权中心配置。
     */
    private SysCopyrightCenterConfig findCenterByCode(String centerCode)
    {
        if (StringUtils.isBlank(centerCode))
        {
            return null;
        }
        
        SysCopyrightCenterConfig query = new SysCopyrightCenterConfig();
        query.setCenterCode(centerCode);
        List<SysCopyrightCenterConfig> list = centerConfigService.selectCopyrightCenterConfigList(query);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 转换为证书 DTO。
     */
    private AppCopyrightCertDto convertToCertDto(SysCopyrightCert cert)
    {
        AppCopyrightCertDto dto = new AppCopyrightCertDto();
        BeanUtils.copyProperties(cert, dto);
        return dto;
    }

    /**
     * 解析日期字符串（yyyy-MM-dd）。
     */
    private Date parseDate(String dateStr)
    {
        if (StringUtils.isBlank(dateStr))
        {
            return null;
        }
        try
        {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            return sdf.parse(dateStr);
        }
        catch (Exception e)
        {
            log.warn("日期解析失败: {}", dateStr, e);
            return null;
        }
    }
}
