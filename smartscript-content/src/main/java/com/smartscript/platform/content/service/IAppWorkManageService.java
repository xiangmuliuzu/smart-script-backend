package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.dto.AppDraftWorkItem;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppReviewStatusDto;
import com.smartscript.platform.content.dto.AppWorkCreateRequest;
import com.smartscript.platform.content.dto.AppWorkUpdateRequest;
import com.smartscript.platform.content.dto.AppWorkVersionDto;

/**
 * 上传与创作（App 侧写入）服务层
 *
 * 依据：接口文档 2.9.2~2.9.9 + 云端 script_platform_dev 库 sys_work / sys_work_version 表。
 *
 * 边界与归属：
 *   - 全部为 App 私有操作，归属一律取 {@code IdentityProvider.currentUserId()}，
 *     不接收请求体 userId；非本人作品一律按「不存在」处理（返回 null / false），
 *     不区分「不存在」与「无权」，避免通过错误信息探测他人作品。
 *   - 本服务只做单表写入与最小派生，不做审核状态流转（状态由 E 模块管理）。
 *   - 创建作品时补齐 sys_work NOT NULL 列默认值（见实现类），不隐式改库结构。
 *
 * @author xiangsipeng
 */
public interface IAppWorkManageService
{
    /**
     * 创建作品（2.9.2）
     *
     * @param request 创建入参（title/categoryId 已由控制层校验非空）
     * @return 新作品ID；写入失败返回 null
     */
    public Long createWork(AppWorkCreateRequest request);

    /**
     * 更新作品基础信息（2.9.3，仅 title/description/price）
     *
     * @param workId  作品ID
     * @param request 更新入参（为 null 的字段不更新）
     * @return true 更新成功；false 作品不存在或非本人
     */
    public boolean updateWork(Long workId, AppWorkUpdateRequest request);

    /**
     * 删除作品（2.9.4，逻辑删除）
     *
     * @param workId 作品ID
     * @return true 删除成功；false 作品不存在或非本人
     */
    public boolean deleteWork(Long workId);

    /**
     * 草稿箱列表（2.9.5，分页）
     *
     * 口径：status='draft' 且 author_id=当前用户 且 is_deleted=0。
     *
     * @param pageNum  页码（从 1 起）
     * @param pageSize 每页条数
     * @return 分页结果（total + list）
     */
    public AppPageResult<AppDraftWorkItem> pageDrafts(int pageNum, int pageSize);

    /**
     * 审核状态查询（2.9.6）
     *
     * @param workId 作品ID
     * @return 审核状态（status/reviewResult/reviewComment）；不存在或非本人返回 null
     */
    public AppReviewStatusDto getReviewStatus(Long workId);

    /**
     * 作品版本列表（2.9.7，不含 content 全文）
     *
     * @param workId 作品ID
     * @return 版本列表；作品不存在或非本人返回 null（本人但无版本返回空列表）
     */
    public List<AppWorkVersionDto> listVersions(Long workId);

    /**
     * 创建作品版本（2.9.8）
     *
     * version_no 按作品内自增（max+1）；新版本置 is_current='1'，历史版本置 '0'。
     *
     * @param workId      作品ID
     * @param versionDesc 变更说明（落库 change_log，可选）
     * @param content     版本内容（可选）
     * @return 新版本ID；作品不存在或非本人返回 null
     */
    public Long createVersion(Long workId, String versionDesc, String content);

    /**
     * 作品版本详情（2.9.9，含 content）
     *
     * @param versionId 版本ID
     * @return 版本详情；版本不存在、或所属作品非本人返回 null
     */
    public AppWorkVersionDto getVersionDetail(Long versionId);
}