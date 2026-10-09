package com.smartscript.platform.content.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.domain.SysWorkVersion;
import com.smartscript.platform.content.dto.AppDraftWorkItem;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppReviewStatusDto;
import com.smartscript.platform.content.dto.AppWorkCreateRequest;
import com.smartscript.platform.content.dto.AppWorkUpdateRequest;
import com.smartscript.platform.content.dto.AppWorkVersionDto;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysWorkVersionMapper;
import com.smartscript.platform.content.service.IAppWorkManageService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 上传与创作（App 侧写入）服务实现
 *
 * 依据：接口文档 2.9.2~2.9.9 + 云端 sys_work / sys_work_version 表结构（附件5.1 表3-14/3-17）。
 *
 * 反推处理点：
 * 1. sys_work 多列 NOT NULL 且接口文档未定义取值：创建时补业务默认值
 *    （work_type='script'、length_type='short'、status='draft'、trade_enabled='0'、
 *    quote_valid_days=0、word_count=0、is_free='0'、is_copyrighted='0'、
 *    三计数=0、rating=0.00、is_deleted='0'），不改库结构、不编造新增字段。
 * 2. genre_id（接口 category_id）NOT NULL：控制层已校验必填，此处只落库。
 * 3. 2.9.6 审核状态：sys_work 无 review_result/review_comment 列，
 *    status←sys_work.status、review_comment←reject_reason、review_result 由 status 派生。
 * 4. 2.9.5 草稿箱以作品级 status='draft' 为口径（sys_draft 是内容级自动保存表，本批不使用）。
 * 5. 分页 total 必须在包装 DTO 之前取（PageInfo 只对 PageHelper 返回的 Page 生效）。
 * 6. 归属一律取身份上下文；非本人作品按「不存在」处理（返回 null/false）。
 * 7. createBy/updateBy 落当前用户ID 字符串：App 域无若依用户名，留空会使审计无迹可查。
 *
 * @author xiangsipeng
 */
@Service
public class AppWorkManageServiceImpl implements IAppWorkManageService
{
    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页条数上限 */
    private static final int PAGE_SIZE_MAX = 50;

    /** 作品类型默认值（库中真实取值：script） */
    private static final String DEFAULT_WORK_TYPE = "script";

    /** 篇幅类型默认值（库中真实取值：short/long） */
    private static final String DEFAULT_LENGTH_TYPE = "short";

    /** 草稿状态（PRD 9.3 作品状态枚举） */
    private static final String STATUS_DRAFT = "draft";

    /** 审核结果派生：已通过（status=approved/published） */
    private static final String RESULT_APPROVED = "approved";

    /** 审核结果派生：已驳回（status=rejected） */
    private static final String RESULT_REJECTED = "rejected";

    /** 审核结果派生：审核中/未提交（其余状态） */
    private static final String RESULT_PENDING = "pending";

    /** 当前版本标记 */
    private static final String FLAG_YES = "1";

    @Autowired
    private SysContentWorkMapper workMapper;

    @Autowired
    private SysWorkVersionMapper versionMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    public Long createWork(AppWorkCreateRequest request)
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return null;
        }
        SysWork work = new SysWork();
        work.setTitle(trimToNull(request.getTitle()));
        work.setCover(trimToNull(request.getCover()));
        work.setSummary(trimToNull(request.getDescription()));
        work.setGenreId(request.getCategoryId());
        work.setPrice(request.getPrice() == null ? BigDecimal.ZERO : request.getPrice());
        // 归属取当前身份，绝不取请求体
        work.setAuthorId(userId);
        // NOT NULL 列默认值（见类注释 反推处理点 1）
        work.setWorkType(DEFAULT_WORK_TYPE);
        work.setLengthType(DEFAULT_LENGTH_TYPE);
        work.setTradeEnabled("0");
        work.setQuoteValidDays(0);
        work.setWordCount(0);
        work.setIsFree("0");
        work.setStatus(STATUS_DRAFT);
        work.setIsCopyrighted("0");
        work.setViewCount(0);
        work.setFavoriteCount(0);
        work.setSaleCount(0);
        work.setRating(BigDecimal.ZERO);
        work.setIsDeleted("0");
        work.setCreateBy(String.valueOf(userId));
        return workMapper.insertWork(work) > 0 ? work.getWorkId() : null;
    }

    @Override
    public boolean updateWork(Long workId, AppWorkUpdateRequest request)
    {
        Long userId = identityProvider.currentUserId();
        if (requireOwned(workId, userId) == null)
        {
            return false;
        }
        SysWork update = new SysWork();
        update.setWorkId(workId);
        // 只放行文档定义的三项可改字段，避免调用方误传其他字段被动态 SQL 一并更新
        update.setTitle(trimToNull(request.getTitle()));
        update.setSummary(trimToNull(request.getDescription()));
        update.setPrice(request.getPrice());
        update.setUpdateBy(String.valueOf(userId));
        return workMapper.updateWorkBasic(update) > 0;
    }

    @Override
    public boolean deleteWork(Long workId)
    {
        Long userId = identityProvider.currentUserId();
        if (requireOwned(workId, userId) == null)
        {
            return false;
        }
        SysWork update = new SysWork();
        update.setWorkId(workId);
        update.setUpdateBy(String.valueOf(userId));
        return workMapper.softDeleteWork(update) > 0;
    }

    @Override
    public AppPageResult<AppDraftWorkItem> pageDrafts(int pageNum, int pageSize)
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return AppPageResult.of(0, List.of());
        }
        int safePageNum = pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, PAGE_SIZE_MAX);
        PageHelper.startPage(safePageNum, safePageSize);
        List<AppDraftWorkItem> rows = workMapper.selectDraftWorks(userId);
        // 必须在包装前取 total：PageInfo 依赖 PageHelper 返回的 Page 类型
        long total = new PageInfo<>(rows).getTotal();
        return AppPageResult.of(total, rows);
    }

    @Override
    public AppReviewStatusDto getReviewStatus(Long workId)
    {
        SysWork work = requireOwned(workId, identityProvider.currentUserId());
        if (work == null)
        {
            return null;
        }
        AppReviewStatusDto dto = new AppReviewStatusDto();
        dto.setStatus(work.getStatus());
        dto.setReviewComment(work.getRejectReason());
        dto.setReviewResult(deriveReviewResult(work.getStatus()));
        return dto;
    }

    @Override
    public List<AppWorkVersionDto> listVersions(Long workId)
    {
        if (requireOwned(workId, identityProvider.currentUserId()) == null)
        {
            return null;
        }
        SysWorkVersion query = new SysWorkVersion();
        query.setWorkId(workId);
        List<SysWorkVersion> rows = versionMapper.selectVersionList(query);
        List<AppWorkVersionDto> list = new ArrayList<>(rows.size());
        for (SysWorkVersion row : rows)
        {
            // 列表不下发 content 全文（长文本）
            list.add(toVersionDto(row, false));
        }
        return list;
    }

    @Override
    @Transactional
    public Long createVersion(Long workId, String versionDesc, String content)
    {
        Long userId = identityProvider.currentUserId();
        if (requireOwned(workId, userId) == null)
        {
            return null;
        }
        Integer maxVersionNo = versionMapper.selectMaxVersionNo(workId);
        SysWorkVersion version = new SysWorkVersion();
        version.setWorkId(workId);
        version.setVersionNo(String.valueOf((maxVersionNo == null ? 0 : maxVersionNo) + 1));
        version.setContent(content);
        version.setChangeLog(trimToNull(versionDesc));
        version.setCreatorId(userId);
        version.setIsCurrent(FLAG_YES);
        version.setCreateBy(String.valueOf(userId));
        // 先清旧当前标记再插入，保证同一作品仅一条 is_current='1'
        versionMapper.clearCurrentFlag(workId);
        return versionMapper.insertVersion(version) > 0 ? version.getVersionId() : null;
    }

    @Override
    public AppWorkVersionDto getVersionDetail(Long versionId)
    {
        if (versionId == null)
        {
            return null;
        }
        SysWorkVersion version = versionMapper.selectVersionById(versionId);
        if (version == null)
        {
            return null;
        }
        // 归属按版本所属作品判定，非本人按「不存在」处理
        if (requireOwned(version.getWorkId(), identityProvider.currentUserId()) == null)
        {
            return null;
        }
        return toVersionDto(version, true);
    }

    /**
     * 取「本人且未删除」的作品；不存在或非本人返回 null。
     */
    private SysWork requireOwned(Long workId, Long userId)
    {
        if (workId == null || userId == null)
        {
            return null;
        }
        return workMapper.selectOwnedWork(workId, userId);
    }

    /**
     * 由作品状态派生审核结果（sys_work 无 review_result 列）。
     */
    private static String deriveReviewResult(String status)
    {
        if (status == null)
        {
            return RESULT_PENDING;
        }
        return switch (status)
        {
            case "approved", "published" -> RESULT_APPROVED;
            case "rejected" -> RESULT_REJECTED;
            default -> RESULT_PENDING;
        };
    }

    private static AppWorkVersionDto toVersionDto(SysWorkVersion row, boolean withContent)
    {
        AppWorkVersionDto dto = new AppWorkVersionDto();
        dto.setVersionId(row.getVersionId());
        dto.setVersionNo(row.getVersionNo());
        dto.setChangeLog(row.getChangeLog());
        dto.setIsCurrent(row.getIsCurrent());
        dto.setCreateTime(row.getCreateTime());
        if (withContent)
        {
            dto.setContent(row.getContent());
        }
        return dto;
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}