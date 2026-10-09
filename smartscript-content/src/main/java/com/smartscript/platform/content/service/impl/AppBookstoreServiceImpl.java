package com.smartscript.platform.content.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.content.domain.SysBanner;
import com.smartscript.platform.content.domain.SysCategory;
import com.smartscript.platform.content.domain.SysTag;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.domain.SysWorkFile;
import com.smartscript.platform.content.dto.AppChapterDetailDto;
import com.smartscript.platform.content.dto.AppChapterDto;
import com.smartscript.platform.content.dto.AppChapterListDto;
import com.smartscript.platform.content.dto.AppContactDto;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppPreviewDto;
import com.smartscript.platform.content.dto.AppRankingItem;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.dto.AppWorkFileDto;
import com.smartscript.platform.content.dto.AppWorkQuery;
import com.smartscript.platform.content.mapper.AppContactProfileMapper;
import com.smartscript.platform.content.mapper.AppWorkAuthorizationMapper;
import com.smartscript.platform.content.mapper.SysBannerMapper;
import com.smartscript.platform.content.mapper.SysCategoryMapper;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysTagMapper;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.mapper.SysWorkFileMapper;
import com.smartscript.platform.content.service.IAppBookstoreService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 书城浏览（App 侧只读）服务实现
 *
 * 依据：接口文档 2.7 + 云端 script_platform_dev 库真实表结构。
 * 反推处理点：
 * 1. 启用口径以 PC 管理页为准：分类/标签 normal 为 status='0'（'1'=停用），
 *    Banner 启用为 status='on'；作品可见性为 is_deleted=0 且 status='on_shelf'。
 * 2. 分页总数必须在映射成 DTO 之前取（PageInfo 只对 Page 类型有效），
 *    否则越界页会退化为 total=0。故先取 Mapper 返回的原集合再取 total。
 * 3. sort/type 在此收敛为白名单，非法值回落到默认排序，不做列名拼接。
 *
 * @author xiangsipeng
 */
@Service
public class AppBookstoreServiceImpl implements IAppBookstoreService
{
    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限 */
    private static final int PAGE_SIZE_MAX = 50;

    /** 榜单默认条数 */
    private static final int DEFAULT_RANKING_LIMIT = 10;

    /** 榜单条数上限 */
    private static final int RANKING_LIMIT_MAX = 50;

    /** 分类/标签「正常」状态（PC 端口径：'0'=正常 '1'=停用） */
    private static final String STATUS_NORMAL = "0";

    /** 试读开关「开启」取值（sys_work.preview_enabled 为 tinyint ↔ String） */
    private static final String PREVIEW_ENABLED = "1";

    /** 榜单类型白名单 */
    private static final List<String> RANKING_TYPES = List.of("view", "favorite", "sale", "rating");

    /** 访问范围：仅试读（未获授权） */
    private static final String ACCESS_SCOPE_PREVIEW = "preview";

    /** 访问范围：全文（已获授权） */
    private static final String ACCESS_SCOPE_FULL = "full";

    @Autowired
    private SysBannerMapper bannerMapper;

    @Autowired
    private SysCategoryMapper categoryMapper;

    @Autowired
    private SysTagMapper tagMapper;

    @Autowired
    private SysContentWorkMapper workMapper;

    @Autowired
    private SysWorkChapterMapper chapterMapper;

    @Autowired
    private SysWorkFileMapper fileMapper;

    @Autowired
    private AppWorkAuthorizationMapper authorizationMapper;

    @Autowired
    private AppContactProfileMapper contactProfileMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    public List<SysBanner> listBanners(String position)
    {
        SysBanner query = new SysBanner();
        query.setPosition(position);
        return bannerMapper.selectAppBannerList(query);
    }

    @Override
    public List<SysCategory> listCategories(String categoryType, Long parentId)
    {
        SysCategory query = new SysCategory();
        query.setCategoryType(categoryType);
        query.setParentId(parentId);
        // 只下发正常分类，停用分类不出现在 App 分类入口与筛选项
        query.setStatus(STATUS_NORMAL);
        return categoryMapper.selectCategoryList(query);
    }

    @Override
    public List<SysTag> listTags(String tagType, Long categoryId)
    {
        return tagMapper.selectAppTagList(tagType, categoryId);
    }

    @Override
    public AppPageResult<AppWorkDto> pageWorks(AppWorkQuery query, int pageNum, int pageSize)
    {
        int safePageNum = pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, PAGE_SIZE_MAX);
        AppWorkQuery safeQuery = query == null ? new AppWorkQuery() : query;
        safeQuery.setSort(normalizeSort(safeQuery.getSort()));
        PageHelper.startPage(safePageNum, safePageSize);
        List<AppWorkDto> rows = workMapper.selectAppWorkList(safeQuery);
        // 必须在映射/包装前取 total：PageInfo 依赖 PageHelper 返回的 Page 类型
        long total = new PageInfo<>(rows).getTotal();
        return AppPageResult.of(total, rows);
    }

    @Override
    public AppWorkDto getWork(Long workId)
    {
        if (workId == null)
        {
            return null;
        }
        return workMapper.selectAppWorkById(workId);
    }

    @Override
    public List<AppRankingItem> listRankings(String type, int limit)
    {
        int safeLimit = limit < 1 ? DEFAULT_RANKING_LIMIT : Math.min(limit, RANKING_LIMIT_MAX);
        String safeType = normalizeRankingType(type);
        List<AppRankingItem> rows = workMapper.selectAppRankingList(safeType, safeLimit);
        for (int i = 0; i < rows.size(); i++)
        {
            AppRankingItem item = rows.get(i);
            item.setRankNo(i + 1);
            item.setScore(scoreOf(item, safeType));
        }
        return rows;
    }

    @Override
    public AppChapterListDto getChapterCatalog(AppWorkDto work)
    {
        AppChapterListDto dto = new AppChapterListDto();
        dto.setPreviewEpisodes(0);
        dto.setAccessScope(ACCESS_SCOPE_PREVIEW);
        dto.setUnlocked(Boolean.FALSE);
        dto.setTotal(0);
        dto.setList(List.of());
        if (work == null || work.getWorkId() == null)
        {
            return dto;
        }
        // 授权判定只查一次，随后同时决定 accessScope 与逐章 readable，避免两处口径漂移
        boolean unlocked = isWorkAuthorized(work.getWorkId());
        dto.setWorkId(work.getWorkId());
        dto.setPreviewEnabled(work.getPreviewEnabled());
        dto.setPreviewEpisodes(previewEpisodes(work));
        dto.setAccessScope(unlocked ? ACCESS_SCOPE_FULL : ACCESS_SCOPE_PREVIEW);
        dto.setUnlocked(unlocked);
        List<AppChapterDto> list = toChapterDtos(work, unlocked);
        dto.setTotal(list.size());
        dto.setList(list);
        return dto;
    }

    @Override
    public AppChapterDetailDto getChapterContent(Long chapterId)
    {
        if (chapterId == null)
        {
            return null;
        }
        SysWorkChapter chapter = chapterMapper.selectChapterDetail(chapterId);
        if (chapter == null)
        {
            return null;
        }
        // 停用章节与目录同一口径：目录不下发停用章节，正文也不得由直连章节ID读到，
        // 统一按「不存在」处理（否则会出现目录看不到、直连能读正文的越权面）。
        if (!STATUS_NORMAL.equals(chapter.getStatus()))
        {
            return null;
        }
        // 作品可见性统一走 getWork（未上架/已删除返回 null），避免各处自拼条件
        AppWorkDto work = getWork(chapter.getWorkId());
        if (work == null)
        {
            return null;
        }
        // 可读 = 在试读范围内 或 当前身份已获授权（两条分支，缺一不可）
        boolean readable = isInPreviewRange(work, chapter.getChapterNo()) || isWorkAuthorized(work.getWorkId());
        AppChapterDetailDto dto = new AppChapterDetailDto();
        dto.setChapterId(chapter.getChapterId());
        dto.setWorkId(chapter.getWorkId());
        dto.setChapterNo(chapter.getChapterNo());
        dto.setChapterTitle(chapter.getChapterTitle());
        dto.setWordCount(chapter.getWordCount());
        dto.setReadable(readable);
        // 不可读时不下发正文，避免整章内容穿透到客户端
        dto.setContent(readable ? chapter.getContent() : null);
        return dto;
    }

    @Override
    public AppPreviewDto getPreview(AppWorkDto work)
    {
        AppPreviewDto dto = new AppPreviewDto();
        if (work == null || work.getWorkId() == null)
        {
            return dto;
        }
        dto.setWorkId(work.getWorkId());
        dto.setPreviewEnabled(work.getPreviewEnabled());
        dto.setPreviewEpisodes(previewEpisodes(work));
        List<AppChapterDto> readable = new ArrayList<>();
        // 免费试读语义固定为试读范围，不因已获授权而扩大为全文
        for (AppChapterDto chapter : toChapterDtos(work, false))
        {
            if (Boolean.TRUE.equals(chapter.getReadable()))
            {
                readable.add(chapter);
            }
        }
        dto.setPreviewChapters(readable);
        dto.setPreviewFiles(toFileDtos(fileMapper.selectPreviewFilesByWorkId(work.getWorkId())));
        return dto;
    }

    @Override
    public AppContactDto getWorkContact(AppWorkDto work)
    {
        AppContactDto dto = new AppContactDto();
        dto.setHasContact(Boolean.FALSE);
        if (work == null || work.getWorkId() == null)
        {
            return dto;
        }
        dto.setWorkId(work.getWorkId());
        // 档案归属人 = 作品作者；作者缺失时按「无档案」处理，不下发展示范围
        if (work.getAuthorId() == null)
        {
            return dto;
        }
        AppContactDto contact = contactProfileMapper.selectContactByOwnerId(work.getAuthorId());
        if (contact == null)
        {
            return dto;
        }
        // hasContact/displayScope 由档案决定；workId 以作品为准，避免 Mapper 入参外泄
        dto.setHasContact(Boolean.TRUE.equals(contact.getHasContact()));
        dto.setDisplayScope(contact.getDisplayScope());
        return dto;
    }

    /**
     * 当前用户对作品是否已获授权（可读全文）。
     *
     * 判定口径：sys_copyright_authorization 中存在「生效中」的授权记录
     * （work_id=作品 且 licensee_id=当前 App 用户 且 status='active' 且时间窗覆盖今天）。
     * 不细分 license_type/license_scope：二者属 D 模块（版权财务域）语义，本批不越界解释。
     * 游客（无身份）一律视为未授权，不伪造用户 ID。
     */
    private boolean isWorkAuthorized(Long workId)
    {
        if (workId == null)
        {
            return false;
        }
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return false;
        }
        return authorizationMapper.countEffectiveAuthorization(workId, userId) > 0;
    }

    /**
     * 组装章节摘要集合；unlocked 为 true 时全部章节可读，否则逐章按试读范围判定。
     */
    private List<AppChapterDto> toChapterDtos(AppWorkDto work, boolean unlocked)
    {
        SysWorkChapter query = new SysWorkChapter();
        query.setWorkId(work.getWorkId());
        // 停用章节不出现在 App 目录中（口径与分类/标签一致：'0'=正常）
        query.setStatus(STATUS_NORMAL);
        List<SysWorkChapter> rows = chapterMapper.selectChapterListByWorkId(query);
        List<AppChapterDto> list = new ArrayList<>(rows.size());
        for (SysWorkChapter row : rows)
        {
            list.add(toChapterDto(row, unlocked || isInPreviewRange(work, row.getChapterNo())));
        }
        return list;
    }

    /**
     * 试读开关是否开启（库中 tinyint 以字符串 "0"/"1" 持有，NULL 视为未开启）。
     */
    private static boolean isPreviewEnabled(AppWorkDto work)
    {
        return work != null && PREVIEW_ENABLED.equals(work.getPreviewEnabled());
    }

    /**
     * 试读集数（未配置为 NULL 时归一为 0，避免 NPE 与负数边界）。
     */
    private static int previewEpisodes(AppWorkDto work)
    {
        Integer episodes = work == null ? null : work.getPreviewEpisodes();
        return episodes == null || episodes < 0 ? 0 : episodes;
    }

    /**
     * 章节是否在试读范围内：开关开启 且 试读集数>0 且 chapter_no<=试读集数。
     *
     * 仅代表「免费试读」这一条放开分支；是否可读还需 OR 上「当前身份已获授权」
     * （见 {@link #isWorkAuthorized}）。命名刻意不含 readable 字样，避免与
     * 「授权放开全文」混淆。
     */
    private static boolean isInPreviewRange(AppWorkDto work, Integer chapterNo)
    {
        int episodes = previewEpisodes(work);
        return isPreviewEnabled(work) && episodes > 0 && chapterNo != null && chapterNo <= episodes;
    }

    private static AppChapterDto toChapterDto(SysWorkChapter row, boolean readable)
    {
        AppChapterDto dto = new AppChapterDto();
        dto.setChapterId(row.getChapterId());
        dto.setChapterNo(row.getChapterNo());
        dto.setChapterTitle(row.getChapterTitle());
        dto.setWordCount(row.getWordCount());
        dto.setIsFree(row.getIsFree());
        dto.setReadable(readable);
        return dto;
    }

    private static List<AppWorkFileDto> toFileDtos(List<SysWorkFile> rows)
    {
        List<AppWorkFileDto> list = new ArrayList<>(rows.size());
        for (SysWorkFile row : rows)
        {
            AppWorkFileDto dto = new AppWorkFileDto();
            dto.setFileId(row.getFileId());
            dto.setFileName(row.getFileName());
            dto.setFileUrl(row.getFileUrl());
            dto.setFileType(row.getFileType());
            dto.setFileSize(row.getFileSize());
            list.add(dto);
        }
        return list;
    }

    /**
     * 排序白名单收敛：非法值回落为最新（create_time desc）。
     */
    private static String normalizeSort(String sort)
    {
        if (sort == null || sort.isEmpty())
        {
            return "latest";
        }
        return switch (sort)
        {
            case "latest", "view", "favorite", "sale", "rating", "price_asc", "price_desc" -> sort;
            default -> "latest";
        };
    }

    /**
     * 榜单类型白名单收敛：非法值回落为浏览量榜。
     */
    private static String normalizeRankingType(String type)
    {
        if (type == null || type.isEmpty())
        {
            return "view";
        }
        return RANKING_TYPES.contains(type) ? type : "view";
    }

    /**
     * 榜单分数 = type 对应的指标值（view/favorite/sale/rating 四种排序）。
     */
    private static BigDecimal scoreOf(AppRankingItem item, String type)
    {
        return switch (type)
        {
            case "favorite" -> toDecimal(item.getFavoriteCount());
            case "sale" -> toDecimal(item.getSaleCount());
            case "rating" -> item.getRating();
            default -> toDecimal(item.getViewCount());
        };
    }

    private static BigDecimal toDecimal(Integer value)
    {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}