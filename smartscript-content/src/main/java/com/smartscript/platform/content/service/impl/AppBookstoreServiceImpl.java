package com.smartscript.platform.content.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ruoyi.common.utils.DateUtils;
import com.smartscript.platform.content.domain.SysBanner;
import com.smartscript.platform.content.domain.SysCategory;
import com.smartscript.platform.content.domain.SysPromotionApplication;
import com.smartscript.platform.content.domain.SysPromotionSlot;
import com.smartscript.platform.content.domain.SysTag;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.domain.SysWorkFile;
import com.smartscript.platform.content.dto.AppCategoryNavDto;
import com.smartscript.platform.content.dto.AppChapterDetailDto;
import com.smartscript.platform.content.dto.AppChapterDto;
import com.smartscript.platform.content.dto.AppChapterListDto;
import com.smartscript.platform.content.dto.AppContactDto;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppPreviewDto;
import com.smartscript.platform.content.dto.AppRankingItem;
import com.smartscript.platform.content.dto.AppRankingResult;
import com.smartscript.platform.content.dto.AppTagDto;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.dto.AppWorkFileDto;
import com.smartscript.platform.content.dto.AppWorkQuery;
import com.smartscript.platform.content.dto.AppWorkTagDto;
import com.smartscript.platform.content.mapper.AppContactProfileMapper;
import com.smartscript.platform.content.mapper.AppReadRecordMapper;
import com.smartscript.platform.content.mapper.AppWorkAuthorizationMapper;
import com.smartscript.platform.content.mapper.SysBannerMapper;
import com.smartscript.platform.content.mapper.SysCategoryMapper;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysPromotionApplicationMapper;
import com.smartscript.platform.content.mapper.SysPromotionSlotMapper;
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
 * 1. 启用口径以 PC 管理页为准：频道/标签 normal 为 status='0'（'1'=停用），
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
    private static final Logger log = LoggerFactory.getLogger(AppBookstoreServiceImpl.class);

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

    /** 频道/标签「正常」状态（PC 端口径：'0'=正常 '1'=停用） */
    private static final String STATUS_NORMAL = "0";

    /** 试读开关「开启」取值（sys_work.preview_enabled 为 tinyint ↔ String） */
    private static final String PREVIEW_ENABLED = "1";

    /** 章节免费标记「免费」取值（sys_work_chapter.is_free：0 付费 / 1 免费） */
    private static final String CHAPTER_FREE = "1";

    /** 榜单类型白名单 */
    private static final List<String> RANKING_TYPES = List.of("view", "favorite", "sale", "rating", "new");

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
    private SysPromotionApplicationMapper promotionApplicationMapper;

    @Autowired
    private SysPromotionSlotMapper promotionSlotMapper;

    @Autowired
    private SysWorkChapterMapper chapterMapper;

    @Autowired
    private SysWorkFileMapper fileMapper;

    @Autowired
    private AppWorkAuthorizationMapper authorizationMapper;

    @Autowired
    private AppContactProfileMapper contactProfileMapper;

    @Autowired
    private AppReadRecordMapper readRecordMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    public List<SysBanner> listBanners(String position)
    {
        SysBanner query = new SysBanner();
        query.setPosition(position);
        List<SysBanner> rows = bannerMapper.selectAppBannerList(query);

        if (!"home_top".equals(position))
        {
            return rows;
        }

        // 首页轮播完全由推广申请派生：同位次锁定单优先，其次是运营兜底单（exclusive=0）。
        // 旧 sys_banner 兜底素材已随 Banner 管理页下线清理，不再读取。上下线完全由时间窗决定。
        Map<Integer, SysBanner> bySortOrder = new TreeMap<>();
        int maxSlotNo = maxSlotNo("banner");
        for (SysPromotionApplication app : promotionApplicationMapper.selectActiveBannerApplications(new Date()))
        {
            if (app.getSlotNo() == null || app.getSlotNo() < 1
                    || (maxSlotNo > 0 && app.getSlotNo() > maxSlotNo))
            {
                continue;
            }
            SysBanner banner = new SysBanner();
            banner.setBannerId(app.getBannerId() != null ? app.getBannerId() : app.getApplyId());
            banner.setTitle(app.getWorkTitle());
            banner.setImageUrl(resolvePromotionMaterial(app));
            if (banner.getImageUrl() == null)
            {
                continue;
            }
            banner.setLinkType("work");
            banner.setLinkId(app.getWorkId());
            banner.setPosition(position);
            banner.setSortOrder(app.getSlotNo());
            banner.setStartTime(app.getStartTime());
            banner.setEndTime(app.getEndTime());
            banner.setStatus("on");
            bySortOrder.putIfAbsent(app.getSlotNo(), banner);
        }

        // 轮播展示严格按坑位配置位次取数，超出坑位数量的投放不再下发，
        // 避免 App 比 PC 多出轮播图。
        return new ArrayList<>(bySortOrder.values());
    }

    /** 坑位配置的最大位次；未配置时返回 0（调用方按不限位次处理） */
    private int maxSlotNo(String slotType)
    {
        int max = 0;
        for (SysPromotionSlot slot : promotionSlotMapper.selectSlots(slotType))
        {
            if (slot.getSlotNo() != null)
            {
                max = Math.max(max, slot.getSlotNo());
            }
        }
        return max;
    }

    /** 推广申请素材：优先作者提交的素材，其次回退到管理员绑定的运营 Banner */
    private String resolvePromotionMaterial(SysPromotionApplication app)
    {
        String material = app.getMaterialUrl();
        if (material != null && !material.trim().isEmpty())
        {
            return material.trim();
        }
        if (app.getBannerId() != null)
        {
            SysBanner bound = bannerMapper.selectBannerById(app.getBannerId());
            return bound == null ? null : bound.getImageUrl();
        }
        return null;
    }

    @Override
    public List<SysTag> listTags(String tagType, Long channelId)
    {
        return tagMapper.selectAppTagList(tagType, channelId);
    }

    @Override
    public AppCategoryNavDto getCategoryNav(Long channelId)
    {
        AppCategoryNavDto nav = new AppCategoryNavDto();

        SysCategory query = new SysCategory();
        query.setStatus(STATUS_NORMAL);
        List<SysCategory> rows = categoryMapper.selectCategoryList(query);

        // 频道 Tab：只取频道类顶级分类，展示名去掉「频道」后缀
        List<AppCategoryNavDto.Channel> channelDtos = new ArrayList<>();
        for (SysCategory row : rows)
        {
            Long parentId = row.getParentId();
            if (parentId == null || parentId == 0L)
            {
                if ("channel".equals(row.getCategoryType())
                        || (row.getCategoryName() != null && row.getCategoryName().endsWith("频道")))
                {
                    String name = row.getCategoryName() == null ? "" : row.getCategoryName().replaceAll("频道$", "");
                    channelDtos.add(new AppCategoryNavDto.Channel(row.getCategoryId(), name));
                }
            }
        }
        nav.setChannels(channelDtos);
        if (channelDtos.isEmpty())
        {
            return nav;
        }

        final Long requestChannelId = channelId;
        Long selectedId = requestChannelId;
        if (requestChannelId == null
                || channelDtos.stream().noneMatch(c -> c.getChannelId().equals(requestChannelId)))
        {
            selectedId = channelDtos.get(0).getChannelId();
        }
        nav.setChannelId(selectedId);

        // 从 sys_tag 表读取当前频道的所有标签
        List<SysTag> allTags = tagMapper.selectAppTagList(null, selectedId);

        // 四个维度：热门标签 / 主题 / 角色 / 情节，按 tag_type 分组
        nav.getDimensions().add(buildTagTypeDimension("hot", "热门标签", allTags));
        nav.getDimensions().add(buildTagTypeDimension("theme", "主题", allTags));
        nav.getDimensions().add(buildTagTypeDimension("role", "角色", allTags));
        nav.getDimensions().add(buildTagTypeDimension("plot", "情节", allTags));

        return nav;
    }

    /** 按 tag_type 构建维度：所有标签平铺，无分组标题 */
    private static AppCategoryNavDto.Dimension buildTagTypeDimension(String tagType, String name, List<SysTag> allTags)
    {
        AppCategoryNavDto.Dimension dimension = new AppCategoryNavDto.Dimension(tagType, name);
        AppCategoryNavDto.Group group = new AppCategoryNavDto.Group("");

        for (SysTag tag : allTags)
        {
            if (tagType.equals(tag.getTagType()))
            {
                group.getItems().add(tagItem(tag));
            }
        }

        if (!group.getItems().isEmpty())
        {
            dimension.getGroups().add(group);
        }
        return dimension;
    }

    private static AppCategoryNavDto.Item tagItem(SysTag tag)
    {
        return new AppCategoryNavDto.Item("tag", tag.getTagId(), tag.getTagName(),
                tag.getUseCount() == null ? 0 : tag.getUseCount());
    }

    @Override
    public AppPageResult<AppWorkDto> pageWorks(AppWorkQuery query, int pageNum, int pageSize)
    {
        int safePageNum = pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, PAGE_SIZE_MAX);
        AppWorkQuery safeQuery = query == null ? new AppWorkQuery() : query;
        // 首页热门精选与搜索页推荐是「坑位列表」，不是普通分页列表：
        // 按坑位配置数量（热门 20 / 搜索 5）合成，付费投放占对应位次，空位由自然作品补位。
        if ("home_hot".equals(safeQuery.getRecommendStatus()))
        {
            return composeSlotWorks("hot", safePageNum);
        }
        if ("search".equals(safeQuery.getShowScope())
                && (safeQuery.getKeyword() == null || safeQuery.getKeyword().trim().isEmpty()))
        {
            return composeSlotWorks("search", safePageNum);
        }
        safeQuery.setSort(normalizeSort(safeQuery.getSort()));
        PageHelper.startPage(safePageNum, safePageSize);
        List<AppWorkDto> rows = workMapper.selectAppWorkList(safeQuery);
        // 必须在映射/包装前取 total：PageInfo 依赖 PageHelper 返回的 Page 类型
        long total = new PageInfo<>(rows).getTotal();
        // 一次批量回填标签，避免逐作品查询造成 N+1
        fillTags(rows);
        return AppPageResult.of(total, rows);
    }

    /**
     * 按坑位合成资源位列表（首页热门精选 / 搜索页推荐）。
     *
     * 位次 1..N 固定：已支付投放按 slot_no 占位，空位按浏览量自然补位；
     * 作品下架或素材缺失的付费申请视为空位。列表不分页，page&gt;1 返回空页。
     */
    private AppPageResult<AppWorkDto> composeSlotWorks(String slotType, int pageNum)
    {
        int slotCount = maxSlotNo(slotType);
        if (slotCount <= 0)
        {
            return AppPageResult.of(0, new ArrayList<>());
        }
        if (pageNum > 1)
        {
            return AppPageResult.of(slotCount, new ArrayList<>());
        }

        Map<Integer, AppWorkDto> paidBySlot = new HashMap<>();
        Set<Long> usedWorkIds = new HashSet<>();
        for (SysPromotionApplication app : promotionApplicationMapper.selectActiveSlotApplications(slotType, new Date()))
        {
            Integer slotNo = app.getSlotNo();
            if (slotNo == null || paidBySlot.containsKey(slotNo))
            {
                continue;
            }
            AppWorkDto paid = getWork(app.getWorkId());
            if (paid == null || usedWorkIds.contains(paid.getWorkId()))
            {
                // 作品下架或同一作品重复占位，按空位处理
                continue;
            }
            paidBySlot.put(slotNo, paid);
            usedWorkIds.add(paid.getWorkId());
        }

        // 自然补位候选：按浏览量排序，多取付费占用数保证空位够填
        AppWorkQuery naturalQuery = new AppWorkQuery();
        naturalQuery.setSort("view");
        PageHelper.startPage(DEFAULT_PAGE_NUM,
                Math.min(slotCount + paidBySlot.size(), PAGE_SIZE_MAX), false);
        List<AppWorkDto> candidates = workMapper.selectAppWorkList(naturalQuery);
        fillTags(candidates);

        List<AppWorkDto> result = new ArrayList<>(slotCount);
        int index = 0;
        for (int slotNo = 1; slotNo <= slotCount; slotNo++)
        {
            AppWorkDto paid = paidBySlot.get(slotNo);
            if (paid != null)
            {
                result.add(paid);
                continue;
            }
            while (index < candidates.size() && usedWorkIds.contains(candidates.get(index).getWorkId()))
            {
                index++;
            }
            if (index < candidates.size())
            {
                AppWorkDto filler = candidates.get(index++);
                result.add(filler);
                usedWorkIds.add(filler.getWorkId());
            }
        }
        return AppPageResult.of(result.size(), result);
    }

    @Override
    public AppWorkDto getWork(Long workId)
    {
        if (workId == null)
        {
            return null;
        }
        AppWorkDto work = workMapper.selectAppWorkById(workId);
        if (work != null)
        {
            fillTags(List.of(work));
            // 作品级试读状态需含作者逐章标记的免费章节：preview_enabled/preview_episodes
            // 全项目无写入方，只认这两列会恒显示「未开启试读」
            work.setFreeChapterCount(chapterMapper.countFreeChapters(workId));
        }
        return work;
    }

    @Override
    public AppWorkDto getWorkDetail(Long workId)
    {
        if (workId == null)
        {
            return null;
        }
        // 详情入口浏览一次即 +1：先自增再查询，使本次返回的 viewCount 已含本次访问
        // （作品不存在时自增 0 行，随后 getWork 返回 null，由控制层按 404 处理）
        workMapper.incrementViewCount(workId);
        return getWork(workId);
    }

    @Override
    public AppRankingResult listRankings(String type, int limit)
    {
        int safeLimit = limit < 1 ? DEFAULT_RANKING_LIMIT : Math.min(limit, RANKING_LIMIT_MAX);
        String safeType = normalizeRankingType(type);
        // 各榜的指标口径（时间窗口 / 平均评分）由 SQL 按 type 分支计算，
        // rank_score 直接映射为条目 score，服务层只负责编号与更新时间
        List<AppRankingItem> rows = workMapper.selectAppRankingList(safeType, safeLimit);
        for (int i = 0; i < rows.size(); i++)
        {
            rows.get(i).setRankNo(i + 1);
        }
        AppRankingResult result = new AppRankingResult();
        result.setList(rows);
        // 榜单更新时间即本次实时取数时刻，对齐 PRD 5.2「榜单数据按规则排序并标明更新时间」
        result.setUpdatedAt(DateUtils.getTime());
        return result;
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
        // 未获授权时目录只展示可试读章节（试读范围 + 作者标记免费），
        // 已发布但未开放试读的付费章节直接不下发；获授权后展示全部已发布章节。
        dto.setPublishedTotal(list.size());
        if (!unlocked)
        {
            list = list.stream().filter(chapter -> Boolean.TRUE.equals(chapter.getReadable())).toList();
        }
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
        // 可读 = 在试读范围内 或 作者标记的免费章节 或 当前身份已获授权
        boolean readable = isChapterReadable(work, chapter);
        // 阅读埋点：正文实际下发（可读）时记一条阅读事件，供阅读行为统计使用；
        // 仅登录用户写入，游客不写，写失败不影响正文返回（见 recordRead）
        if (readable)
        {
            recordRead(chapter.getWorkId(), chapter.getChapterId());
        }
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
        // 试读包只收「可读」章节：试读范围 + 作者标记的免费章节，
        // 不因已获授权而扩大为全文（unlocked 传 false）
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
        // hasContact: 档案有密文或 sys_user 有手机号即视为已登记
        boolean hasPhone = contact.getPhone() != null && !contact.getPhone().isEmpty();
        boolean hasEmail = contact.getEmail() != null && !contact.getEmail().isEmpty();
        boolean hasWechat = contact.getWechat() != null && !contact.getWechat().isEmpty();
        dto.setHasContact(hasPhone || hasEmail || hasWechat || Boolean.TRUE.equals(contact.getHasContact()));
        dto.setDisplayScope(contact.getDisplayScope());
        dto.setPhone(contact.getPhone());
        dto.setEmail(contact.getEmail());
        dto.setWechat(contact.getWechat());
        return dto;
    }

    /**
     * 当前用户对作品是否已获授权（可读全文）。
     *
     * 判定口径：sys_copyright_authorization 中存在「生效中」的授权记录
     * （work_id=作品 且 licensee_id=当前 App 用户 且 status='active' 且时间窗覆盖今天），
     * 或 sys_order 中存在该用户已购买该作品且已完成的订单（D 模块未落地前的成交口径）。
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
        if (authorizationMapper.countEffectiveAuthorization(workId, userId) > 0)
        {
            return true;
        }
        return authorizationMapper.countPurchasedOrder(workId, userId) > 0;
    }

    /**
     * 阅读埋点：为当前登录用户补写一条 sys_read_record（幂等，同一用户当天同一章节一条）。
     *
     * 依据：附件5.1 表3-31；该表为阅读行为的带时间戳数据源
     * （热门榜已改为累计 view_count 排序，不再依赖此表取数）。
     * 边界：游客（无身份）不写，不伪造用户 ID；埋点属旁路统计，
     * 任何写入异常只记 warn 日志，绝不向上抛出影响正文下发。
     */
    private void recordRead(Long workId, Long chapterId)
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return;
        }
        try
        {
            readRecordMapper.insertReadRecordIfAbsent(userId, workId, chapterId);
        }
        catch (RuntimeException e)
        {
            log.warn("阅读记录写入失败，不影响正文返回：userId={}, workId={}, chapterId={}",
                    userId, workId, chapterId, e);
        }
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
            list.add(toChapterDto(row, unlocked || isChapterReadable(work, row)));
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

    /**
     * 章节对当前身份是否可读。三条放开分支，任一成立即可读：
     *
     *   1. 在作品试读范围内（见 {@link #isInPreviewRange}，由平台在审核通过时配置）；
     *   2. 作者标记为免费章节（sys_work_chapter.is_free='1'，0 付费 / 1 免费）——
     *      作者可在 App 章节编辑页逐章开启，若不纳入本判定，作者的标记在阅读端将完全无效；
     *   3. 当前身份已获版权授权（见 {@link #isWorkAuthorized}）。
     */
    private boolean isChapterReadable(AppWorkDto work, SysWorkChapter chapter)
    {
        return isInPreviewRange(work, chapter.getChapterNo())
                || CHAPTER_FREE.equals(chapter.getIsFree())
                || isWorkAuthorized(work.getWorkId());
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
     * 为作品集合批量回填标签。
     *
     * 一次查询（IN 全部 workId）后按 workId 分组回填，避免逐作品查询造成 N+1；
     * 无标签的作品回填空集合（而非 null），前端可无脑渲染 chips。
     */
    private void fillTags(List<AppWorkDto> works)
    {
        if (works == null || works.isEmpty())
        {
            return;
        }
        List<Long> workIds = new ArrayList<>(works.size());
        for (AppWorkDto work : works)
        {
            if (work != null && work.getWorkId() != null)
            {
                workIds.add(work.getWorkId());
            }
        }
        if (workIds.isEmpty())
        {
            return;
        }
        List<AppWorkTagDto> rows = tagMapper.selectWorkTagRows(workIds);
        Map<Long, List<AppTagDto>> grouped = new HashMap<>();
        for (AppWorkTagDto row : rows)
        {
            grouped.computeIfAbsent(row.getWorkId(), key -> new ArrayList<>()).add(toTagDto(row));
        }
        for (AppWorkDto work : works)
        {
            if (work == null || work.getWorkId() == null)
            {
                continue;
            }
            work.setTags(grouped.getOrDefault(work.getWorkId(), List.of()));
        }
    }

    private static AppTagDto toTagDto(AppWorkTagDto row)
    {
        AppTagDto dto = new AppTagDto();
        dto.setTagId(row.getTagId());
        dto.setTagName(row.getTagName());
        dto.setTagType(row.getTagType());
        return dto;
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
}