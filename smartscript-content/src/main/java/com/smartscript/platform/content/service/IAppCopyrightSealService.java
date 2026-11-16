package com.smartscript.platform.content.service;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import com.smartscript.platform.content.domain.SysCopyrightSeal;

/**
 * App 端用户印章服务（用户侧：上传、查看、停用、重新提交）。
 */
public interface IAppCopyrightSealService
{
    /**
     * 我的印章列表（单用户通常只有 1 条记录，支持多个历史记录）。
     * 
     * @param userId 当前登录用户 ID
     * @return 印章列表（含审核状态、印章状态、驳回原因）
     */
    List<Map<String, Object>> mySeals(Long userId);

    /**
     * 印章详情（含材料 URL、审核状态、驳回原因、操作日志）。
     * 
     * @param userId 当前登录用户 ID
     * @param sealId 印章 ID
     * @return 印章详情（不存在或无权限返回 null）
     */
    Map<String, Object> sealDetail(Long userId, Long sealId);

    /**
     * 上传印章图片（首次提交或重新提交）。
     * 
     * @param userId 当前登录用户 ID
     * @param sealName 印章名称
     * @param sealImageUrl 印章图片 URL（已上传到 OSS）
     * @param materialUrls 审核材料 URL 列表（逗号分隔，已上传到 OSS）
     * @return 新建或更新的印章 ID
     */
    Long submitSeal(Long userId, String sealName, String sealImageUrl, String materialUrls);

    /**
     * 补充审核材料（印章已存在时追加材料）。
     * 
     * @param userId 当前登录用户 ID
     * @param sealId 印章 ID
     * @param additionalMaterialUrls 新增材料 URL（逗号分隔）
     * @return 是否成功
     */
    boolean addMaterials(Long userId, Long sealId, String additionalMaterialUrls);

    /**
     * 重新提交印章（驳回后重新上传）。
     * 
     * @param userId 当前登录用户 ID
     * @param sealId 印章 ID
     * @param sealImageUrl 新印章图片 URL
     * @param materialUrls 新审核材料 URL
     * @return 是否成功
     */
    boolean resubmitSeal(Long userId, Long sealId, String sealImageUrl, String materialUrls);

    /**
     * 停用印章（用户主动停用，仅限已审核通过的印章）。
     * 
     * @param userId 当前登录用户 ID
     * @param sealId 印章 ID
     * @return 是否成功
     */
    boolean disableSeal(Long userId, Long sealId);

    /**
     * 更新印章信息（编辑印章名称、图片、材料，修改后重置审核状态为 pending）。
     * 
     * @param userId 当前登录用户 ID
     * @param sealId 印章 ID
     * @param sealName 新印章名称（可选，null 表示不修改）
     * @param sealImageUrl 新印章图片 URL（可选，null 表示不修改）
     * @param materialUrls 新审核材料 URL（可选，null 表示不修改）
     * @return 是否成功
     */
    boolean updateSeal(Long userId, Long sealId, String sealName, String sealImageUrl, String materialUrls);

    /**
     * 删除印章（软删除）。
     * 
     * @param userId 当前登录用户 ID
     * @param sealId 印章 ID
     * @return 是否成功
     */
    boolean deleteSeal(Long userId, Long sealId);
}
