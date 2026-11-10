package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.copyright.*;

/**
 * App 端版权管理服务接口。
 */
public interface IAppCopyrightService
{
    /**
     * 获取我的版权申请列表（分页）。
     *
     * @param userId 用户ID
     * @param status 状态筛选（可选）
     * @param workName 作品名称关键词（可选）
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    AppPageResult<AppCopyrightApplicationDto> getMyApplications(
            Long userId, String status, String workName, Integer pageNum, Integer pageSize);

    /**
     * 获取版权申请详情。
     *
     * @param applyId 申请ID
     * @param userId 用户ID（权限验证）
     * @return 申请详情，不存在或无权限返回 null
     */
    AppCopyrightApplicationDto getApplicationDetail(Long applyId, Long userId);

    /**
     * 询价：按作品字数 + 版权中心计算登记费。
     */
    AppCopyrightFeeQuoteDto quoteFee(Long userId, Long workId, String copyrightCenter);

    /**
     * 创建待支付单（支付前校验；付完后再创建申请）。
     */
    AppCopyrightPayOrderDto createPayOrder(Long userId, AppCopyrightApplyRequest request);

    /**
     * 驳回后重新缴费下单。
     */
    AppCopyrightPayOrderDto createResubmitPayOrder(Long userId, AppCopyrightResubmitRequest request);

    /**
     * 查询待支付单。
     */
    AppCopyrightPayOrderDto getPayOrder(Long payOrderId, Long userId);

    /**
     * 临时直付（跳过支付宝沙箱）：履约建申请并提交第三方。
     *
     * @return paid/applyId 等
     */
    java.util.Map<String, Object> directPay(Long userId, Long payOrderId);

    /**
     * @deprecated 请走 createPayOrder；保留兼容时内部转调建支付单。
     */
    @Deprecated
    default AppCopyrightPayOrderDto submitApplication(Long userId, AppCopyrightApplyRequest request)
    {
        return createPayOrder(userId, request);
    }

    /**
     * @deprecated 请走 createResubmitPayOrder。
     */
    @Deprecated
    default AppCopyrightPayOrderDto resubmitApplication(Long userId, AppCopyrightResubmitRequest request)
    {
        return createResubmitPayOrder(userId, request);
    }

    /**
     * 删除版权申请（仅限pending状态）。
     *
     * @param applyId 申请ID
     * @param userId 用户ID（权限验证）
     * @throws IllegalArgumentException 状态不允许删除或无权限
     */
    void deleteApplication(Long applyId, Long userId);

    /**
     * 更新版权申请（仅限pending状态）。
     *
     * @param applyId 申请ID
     * @param userId 用户ID（权限验证）
     * @param request 更新信息
     * @throws IllegalArgumentException 状态不允许编辑或无权限
     */
    void updateApplication(Long applyId, Long userId, AppCopyrightApplyRequest request);

    /**
     * 获取我的版权证书列表（分页）。
     *
     * @param userId 用户ID
     * @param workName 作品名称关键词（可选）
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    AppPageResult<AppCopyrightCertDto> getMyCertificates(
            Long userId, String workName, Integer pageNum, Integer pageSize);

    /**
     * 获取版权证书详情。
     *
     * @param certId 证书ID
     * @param userId 用户ID（权限验证）
     * @return 证书详情，不存在或无权限返回 null
     */
    AppCopyrightCertDto getCertificateDetail(Long certId, Long userId);

    /**
     * 下载版权证书。
     *
     * @param certId 证书ID
     * @param userId 用户ID（权限验证）
     * @return 下载信息，不存在或无权限返回 null
     */
    AppCopyrightCertDownloadDto downloadCertificate(Long certId, Long userId);

    /**
     * 获取可用的版权中心列表。
     *
     * @return 版权中心列表
     */
    List<AppCopyrightCenterDto> getActiveCenters();
}
