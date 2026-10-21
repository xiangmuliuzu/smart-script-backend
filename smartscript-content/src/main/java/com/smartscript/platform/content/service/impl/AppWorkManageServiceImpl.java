package com.smartscript.platform.content.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ruoyi.common.exception.ServiceException;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.domain.SysWorkFile;
import com.smartscript.platform.content.domain.SysWorkReviewRecord;
import com.smartscript.platform.content.domain.SysWorkVersion;
import com.smartscript.platform.content.dto.AppDraftWorkItem;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppReviewStatusDto;
import com.smartscript.platform.content.dto.AppWorkCreateRequest;
import com.smartscript.platform.content.dto.AppWorkUpdateRequest;
import com.smartscript.platform.content.dto.AppWorkVersionDto;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysTagMapper;
import com.smartscript.platform.content.mapper.SysWorkFileMapper;
import com.smartscript.platform.content.mapper.SysWorkReviewRecordMapper;
import com.smartscript.platform.content.mapper.SysWorkVersionMapper;
import com.smartscript.platform.content.service.IAppWorkManageService;
import com.smartscript.platform.content.util.WorkStatusDeriver;
import com.smartscript.platform.identity.IdentityProvider;
import com.smartscript.platform.identity.WorkDeletionVerification;

/**
 * 上传与创作（App 侧写入）服务实现
 *
 * 依据：接口文档 2.9.2~2.9.9 + 云端 sys_work / sys_work_version 表结构（附件5.1 表3-14/3-17）。
 *
 * 反推处理点：
 * 1. sys_work 多列 NOT NULL 且接口文档未定义取值：创建时补业务默认值
 *    （work_type='script'、length_type='short'、status='off_shelf'、trade_enabled='0'、
 *    quote_valid_days=0、word_count=0、is_free='0'、is_copyrighted='0'、
 *    三计数=0、rating=0.00、is_deleted='0'），不改库结构、不编造新增字段。
 *    status 只写可见性开关（on_shelf/off_shelf），不写生命周期值：新作品未上架故为 off_shelf。
 * 2. genre_id（接口 category_id）NOT NULL：控制层已校验必填，此处只落库。
 * 3. 2.9.6 审核状态：sys_work 无 review_result/review_comment 列，
 *    状态一律由 sys_review_record（target_type='work'）最新一条派生（见 WorkStatusDeriver），
 *    review_comment←sys_work.reject_reason，review_result 由同一来源派生。
 * 4. 2.9.5 草稿箱按「本人 + 未删除 + 从未提交审核」判定（sys_draft 是内容级自动保存表，本批不使用）。
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

    /** 审核结果派生：已通过（最新审核记录 status=approved） */
    private static final String RESULT_APPROVED = "approved";

    /** 审核结果派生：已驳回（最新审核记录 status=rejected） */
    private static final String RESULT_REJECTED = "rejected";

    /** 审核结果派生：审核中/未提交（其余状态，含无记录与 revision） */
    private static final String RESULT_PENDING = "pending";

    /** 当前版本标记 */
    private static final String FLAG_YES = "1";

    /** 通用「否」标记（is_preview） */
    private static final String FLAG_NO = "0";

    /** 剧本文件类型（sys_work_file.file_type） */
    private static final String FILE_TYPE_SCRIPT = "script";

    /** 剧本文件默认排序 */
    private static final int FILE_SORT_DEFAULT = 1;

    /** 剧本文件名缺省占位（file_name 为 NOT NULL，url 无法推出名称时兜底） */
    private static final String FILE_NAME_FALLBACK = "剧本文件";

    @Autowired
    private SysContentWorkMapper workMapper;

    @Autowired
    private SysTagMapper tagMapper;

    @Autowired
    private WorkDeletionVerification deletionVerification;

    @Autowired
    private SysWorkFileMapper workFileMapper;

    @Autowired
    private SysWorkReviewRecordMapper reviewRecordMapper;

    @Autowired
    private SysWorkVersionMapper versionMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    @Transactional
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
        // 可见性开关只写 off_shelf：新作品未经审核，读者不可见
        work.setStatus(WorkStatusDeriver.OFF_SHELF);
        work.setIsCopyrighted("0");
        work.setViewCount(0);
        work.setFavoriteCount(0);
        work.setSaleCount(0);
        work.setRating(BigDecimal.ZERO);
        work.setIsDeleted("0");
        work.setCreateBy(String.valueOf(userId));
        if (workMapper.insertWork(work) <= 0)
        {
            return null;
        }
        insertScriptFile(work.getWorkId(), request, userId);
        return work.getWorkId();
    }

    /**
     * 登记随作品提交的剧本文件（sys_work_file）。
     *
     * 上传接口（2.9.1）在作品创建前无从落库（work_id NOT NULL），故在此补齐：
     * 仅当调用方带了 scriptUrl 时落库；fileName/fileSize 为 NOT NULL，缺失时兜底。
     */
    private void insertScriptFile(Long workId, AppWorkCreateRequest request, Long userId)
    {
        String scriptUrl = trimToNull(request.getScriptUrl());
        if (workId == null || scriptUrl == null)
        {
            return;
        }
        SysWorkFile file = new SysWorkFile();
        file.setWorkId(workId);
        file.setFileName(resolveFileName(request.getScriptName(), scriptUrl));
        file.setFileUrl(scriptUrl);
        file.setFileType(FILE_TYPE_SCRIPT);
        file.setFileSize(request.getScriptSize() == null ? 0L : request.getScriptSize());
        file.setIsPreview(FLAG_NO);
        file.setSort(FILE_SORT_DEFAULT);
        file.setCreateBy(String.valueOf(userId));
        workFileMapper.insertWorkFile(file);
    }

    /**
     * 剧本文件名兜底：优先用上送名称，其次取 url 末段，最后用占位名。
     */
    private static String resolveFileName(String scriptName, String scriptUrl)
    {
        String name = trimToNull(scriptName);
        if (name != null)
        {
            return name;
        }
        int slash = scriptUrl.lastIndexOf('/');
        String fromUrl = slash >= 0 ? trimToNull(scriptUrl.substring(slash + 1)) : null;
        return fromUrl == null ? FILE_NAME_FALLBACK : fromUrl;
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
    @Transactional
    public boolean deleteWork(Long workId, String code)
    {
        Long userId = identityProvider.currentUserId();
        if (!canDelete(workId, userId))
        {
            return false;
        }
        if (code == null || !code.matches("\\d{4,8}"))
        {
            throw new ServiceException("请输入正确格式的短信验证码", 40000);
        }
        deletionVerification.consumeCode(userId, code);
        SysWork update = new SysWork();
        update.setWorkId(workId);
        update.setAuthorId(userId);
        update.setUpdateBy(String.valueOf(userId));
        boolean ok = workMapper.softDeleteWork(update) > 0;
        if (!ok)
        {
            // 验证码消费与删除同事务；失败必须抛出异常，避免把验证码提交为已使用。
            throw new ServiceException("作品状态已变化，未删除，请刷新后重试", 409);
        }
        // 作品逻辑删除后其标签引用不再计入：全量刷新标签使用数量，避免计数虚高
        tagMapper.syncTagUseCount();
        return true;
    }

    @Override
    public Map<String, Object> sendDeletionCode(Long workId, String ip, String requestId)
    {
        Long userId = identityProvider.currentUserId();
        if (!canDelete(workId, userId))
        {
            return null;
        }
        return deletionVerification.sendCode(userId, ip, requestId);
    }

    private boolean canDelete(Long workId, Long userId)
    {
        if (requireOwned(workId, userId) == null)
        {
            return false;
        }
        if (workMapper.hasWorkTransactions(workId))
        {
            throw new ServiceException("作品存在订单、合同或付费记录，不能删除，请使用下架功能", 409);
        }
        return true;
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
        // 生命周期状态一律由 sys_review_record（target_type='work'）最新一条派生，
        // sys_work.status 只是可见性开关，不能当审核状态使用。
        List<SysWorkReviewRecord> records = reviewRecordMapper.selectByWorkId(workId);
        String latest = records.isEmpty() ? null : records.get(0).getStatus();
        AppReviewStatusDto dto = new AppReviewStatusDto();
        dto.setStatus(WorkStatusDeriver.derive(latest));
        dto.setReviewComment(work.getRejectReason());
        dto.setReviewResult(reviewResultOf(latest));
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
     * 由最新审核记录状态派生审核结果（sys_work 无 review_result 列）。
     * approved→approved；rejected→rejected；其余（含 null/revision/reviewing）→pending。
     */
    private static String reviewResultOf(String latestReviewStatus)
    {
        if (RESULT_APPROVED.equals(latestReviewStatus))
        {
            return RESULT_APPROVED;
        }
        if (RESULT_REJECTED.equals(latestReviewStatus))
        {
            return RESULT_REJECTED;
        }
        return RESULT_PENDING;
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
