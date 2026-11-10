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
import com.smartscript.platform.content.util.CopyrightStatusUtil;
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
    private final ICopyrightPayOrderService copyrightPayOrderService;

    public AppCopyrightServiceImpl(
            ISysCopyrightApplicationService copyrightApplicationService,
            ISysCopyrightCertService copyrightCertService,
            ISysCopyrightCenterConfigService centerConfigService,
            ISysWorkService workService,
            ICopyrightPayOrderService copyrightPayOrderService)
    {
        this.copyrightApplicationService = copyrightApplicationService;
        this.copyrightCertService = copyrightCertService;
        this.centerConfigService = centerConfigService;
        this.workService = workService;
        this.copyrightPayOrderService = copyrightPayOrderService;
    }

    @Override
    public AppPageResult<AppCopyrightApplicationDto> getMyApplications(
            Long userId, String status, String workName, Integer pageNum, Integer pageSize)
    {
        log.info("查询用户版权申请列表: userId={}, status={}, workName={}, pageNum={}, pageSize={}", 
                 userId, status, workName, pageNum, pageSize);

        int safePageNum = (pageNum != null && pageNum > 0) ? pageNum : 1;
        int safePageSize = (pageSize != null && pageSize > 0) ? pageSize : 10;

        // 待付款：只查未关闭支付单
        if (CopyrightStatusUtil.STATUS_UNPAID.equals(status))
        {
            List<AppCopyrightApplicationDto> unpaid = copyrightPayOrderService
                    .listOpenPayOrders(userId, workName).stream()
                    .map(this::convertPayOrderToApplicationDto)
                    .collect(Collectors.toList());
            int from = Math.min((safePageNum - 1) * safePageSize, unpaid.size());
            int to = Math.min(from + safePageSize, unpaid.size());
            return new AppPageResult<>(unpaid.size(), unpaid.subList(from, to));
        }

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

        PageHelper.startPage(safePageNum, safePageSize);
        List<SysCopyrightApplication> list = copyrightApplicationService.selectCopyrightApplicationList(query);
        PageInfo<SysCopyrightApplication> pageInfo = new PageInfo<>(list);

        List<AppCopyrightApplicationDto> dtoList = list.stream()
                .map(this::convertToApplicationDto)
                .collect(Collectors.toCollection(ArrayList::new));

        // 全部：第一页前置待付款支付单
        int unpaidCount = 0;
        if (StringUtils.isBlank(status))
        {
            List<AppCopyrightApplicationDto> unpaid = copyrightPayOrderService
                    .listOpenPayOrders(userId, workName).stream()
                    .map(this::convertPayOrderToApplicationDto)
                    .collect(Collectors.toList());
            unpaidCount = unpaid.size();
            if (safePageNum == 1 && !unpaid.isEmpty())
            {
                List<AppCopyrightApplicationDto> merged = new ArrayList<>(unpaid.size() + dtoList.size());
                merged.addAll(unpaid);
                merged.addAll(dtoList);
                dtoList = merged;
            }
        }

        return new AppPageResult<>((int) pageInfo.getTotal() + unpaidCount, dtoList);
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
    public AppCopyrightFeeQuoteDto quoteFee(Long userId, Long workId, String copyrightCenter)
    {
        return copyrightPayOrderService.quoteFee(userId, workId, copyrightCenter);
    }

    @Override
    public AppCopyrightPayOrderDto createPayOrder(Long userId, AppCopyrightApplyRequest request)
    {
        return copyrightPayOrderService.createPayOrder(userId, request);
    }

    @Override
    public AppCopyrightPayOrderDto createResubmitPayOrder(Long userId, AppCopyrightResubmitRequest request)
    {
        return copyrightPayOrderService.createResubmitPayOrder(userId, request);
    }

    @Override
    public AppCopyrightPayOrderDto getPayOrder(Long payOrderId, Long userId)
    {
        return copyrightPayOrderService.getPayOrder(payOrderId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> directPay(Long userId, Long payOrderId)
    {
        return copyrightPayOrderService.directPay(payOrderId, userId);
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
     * 待支付单转列表项（status=unpaid，尚无 applyId）。
     */
    private AppCopyrightApplicationDto convertPayOrderToApplicationDto(AppCopyrightPayOrderDto order)
    {
        AppCopyrightApplicationDto dto = new AppCopyrightApplicationDto();
        dto.setApplyId(null);
        dto.setApplyNo(order.getPayNo());
        dto.setWorkId(order.getWorkId());
        dto.setWorkName(order.getWorkName());
        dto.setCopyrightCenter(order.getCopyrightCenter());
        dto.setCenterName(order.getCenterName());
        dto.setStatus(CopyrightStatusUtil.STATUS_UNPAID);
        dto.setDisplayStatus(CopyrightStatusUtil.STATUS_UNPAID);
        dto.setDisplayStatusText(CopyrightStatusUtil.toDisplayStatusText(CopyrightStatusUtil.STATUS_UNPAID));
        dto.setWordCount(order.getWordCount());
        dto.setFeeAmount(order.getFeeAmount());
        dto.setPayOrderId(order.getPayOrderId());
        dto.setCreateTime(order.getCreateTime());
        if (order.getWorkId() != null)
        {
            SysWork work = workService.selectWorkById(order.getWorkId());
            if (work != null)
            {
                dto.setWorkType(work.getWorkType());
                dto.setWorkSummary(work.getSummary());
                dto.setWorkCoverUrl(work.getCover());
            }
        }
        return dto;
    }

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

        dto.setWordCount(application.getWordCount());
        dto.setFeeAmount(application.getFeeAmount());
        dto.setPayOrderId(application.getPayOrderId());
        dto.setDisplayStatus(CopyrightStatusUtil.toDisplayStatus(application.getStatus()));
        dto.setDisplayStatusText(CopyrightStatusUtil.toDisplayStatusText(application.getStatus()));
        
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
