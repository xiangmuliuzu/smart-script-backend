package com.smartscript.platform.trade.agent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartscript.platform.content.domain.SysTag;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.dto.AppChapterDto;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.dto.AppWorkQuery;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.service.IAppBookstoreService;
import com.smartscript.platform.content.service.IAppWorkStoryboardService;
import com.smartscript.platform.content.util.ChapterTrialAccess;
import com.smartscript.platform.trade.service.TradeInquiryService;

@Service
public class EnterpriseAgentToolRegistry
{
    private static final Set<String> SORTS = Set.of("latest", "view", "favorite", "sale", "rating",
            "price_asc", "price_desc");
    private static final Pattern COMPARISON_BUDGET = Pattern.compile(
            "预算上限\\s*[:：]\\s*(\\d{1,10}(?:\\.\\d{1,2})?)\\s*元");
    private static final List<String> TASK_TYPES = List.of("script", "script_storyboard", "storyboard");
    private static final int MARKET_PAGE_SIZE = 50;
    private static final int MAX_MARKET_PAGES = 5;
    private static final Map<String, Object> STRING = Map.of("type", "string");
    private static final Map<String, Object> POSITIVE_INTEGER = Map.of("type", "integer", "minimum", 1);
    private static final Map<String, Object> NON_NEGATIVE_MONEY = Map.of("type", "number", "minimum", 0);
    private static final Map<String, Object> STRINGS = Map.of("type", "array", "items", STRING);

    private static final Pattern GENRE_SUFFIX = Pattern.compile("(?:短剧|剧本|小说|题材|类型|类|文|剧)+$");
    private static final int MAX_MATCHED_TAG_IDS = 8;

    private static final List<Tool> TOOLS = List.of(
        new Tool("list_tags", "正在查询作品标签", "列出已启用的作品标签，以获取真实 tagId。", Map.of(), List.of()),
        new Tool("search_works", "正在检索作品市场", "按标签或关键词检索书城已上架作品（支持题材词自动匹配标签），价格单位为元。", Map.of(
            "keyword", STRING, "tagId", POSITIVE_INTEGER, "maxPriceYuan", NON_NEGATIVE_MONEY,
            "minEpisodes", POSITIVE_INTEGER, "maxEpisodes", POSITIVE_INTEGER,
            "tradeOnly", Map.of("type", "boolean", "default", false),
            "sort", Map.of("type", "string", "enum", SORTS.stream().sorted().toList()),
            "limit", Map.of("type", "integer", "minimum", 1, "maximum", 10, "default", 8)), List.of()),
        new Tool("get_work_detail", "正在核对作品与版权", "读取已上架作品详情、版权登记和询盘条件。",
            Map.of("workId", POSITIVE_INTEGER), List.of("workId")),
        new Tool("get_work_preview", "正在查阅公开试读", "读取最多两章公开试读正文及已审核分镜目录。",
            Map.of("workId", POSITIVE_INTEGER,
                "maxChapters", Map.of("type", "integer", "minimum", 1, "maximum", 2, "default", 2)),
            List.of("workId")),
        new Tool("compare_works", "正在对比候选作品", "对比两到四部已上架作品。已知预算时，criteria 包含「预算上限：50000 元」格式。",
            Map.of("workIds", Map.of("type", "array", "items", POSITIVE_INTEGER,
                "minItems", 2, "maxItems", 4, "uniqueItems", true), "criteria", STRING),
            List.of("workIds", "criteria")),
        new Tool("draft_procurement_brief", "正在起草定制需求", "仅起草可交由用户确认的采购需求。明确字段后调用。",
            Map.of("title", STRING, "taskType", Map.of("type", "string", "enum", TASK_TYPES),
                "genreTags", STRINGS, "totalEpisodes", POSITIVE_INTEGER, "budgetYuan", NON_NEGATIVE_MONEY,
                "durationDays", POSITIVE_INTEGER, "commercialGoal", STRING, "requirement", STRING,
                "deliveryChecklist", STRINGS),
            List.of("title", "taskType", "genreTags", "totalEpisodes", "budgetYuan", "durationDays",
                "commercialGoal", "requirement", "deliveryChecklist")));

    private final IAppBookstoreService bookstore;
    private final SysWorkChapterMapper chapterMapper;
    private final IAppWorkStoryboardService storyboards;
    private final TradeInquiryService inquiries;
    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    public EnterpriseAgentToolRegistry(IAppBookstoreService bookstore, SysWorkChapterMapper chapterMapper,
            IAppWorkStoryboardService storyboards, TradeInquiryService inquiries)
    {
        this.bookstore = bookstore;
        this.chapterMapper = chapterMapper;
        this.storyboards = storyboards;
        this.inquiries = inquiries;
    }

    public List<Map<String, Object>> definitions()
    {
        return TOOLS.stream().map(tool -> Map.<String, Object>of("type", "function", "function", Map.of(
            "name", tool.name(), "description", tool.description(), "parameters", Map.of("type", "object",
                "properties", tool.properties(), "required", tool.required(), "additionalProperties", false)))).toList();
    }

    public String label(String name)
    {
        Tool tool = tool(name);
        return tool == null ? "未知工具" : tool.label();
    }

    public String argsSummary(String name, String rawArgs)
    {
        try
        {
            JsonNode args = arguments(name, rawArgs);
            return switch (name)
            {
                case "search_works" -> String.join(" · ", searchSummary(args));
                case "get_work_detail", "get_work_preview" -> "作品 " + id(args.path("workId"), "workId");
                case "compare_works" -> args.path("workIds").size() + " 部候选作品";
                case "draft_procurement_brief" -> "整理采购字段及交付验收清单";
                default -> "";
            };
        }
        catch (IllegalArgumentException e)
        {
            return "参数校验";
        }
    }

    public ToolResult execute(String name, String rawArgs, long userId)
    {
        try
        {
            JsonNode args = arguments(name, rawArgs);
            return switch (name)
            {
                case "list_tags" -> listTags();
                case "search_works" -> search(args, userId);
                case "get_work_detail" -> new ToolResult(workItem(work(id(args.path("workId"), "workId")),
                        userId, 1000), "已核对作品信息与登记状态", null);
                case "get_work_preview" -> preview(args);
                case "compare_works" -> compare(args, userId);
                case "draft_procurement_brief" -> draft(args);
                default -> throw new IllegalArgumentException("未知工具");
            };
        }
        catch (IllegalArgumentException e)
        {
            return new ToolResult(Map.of("error", e.getMessage()), e.getMessage(), null);
        }
    }

    private ToolResult listTags()
    {
        Map<String, Long> unique = new LinkedHashMap<>();
        for (var tag : bookstore.listTags(null, null))
        {
            if (!"0".equals(tag.getStatus()) || tag.getTagId() == null || tag.getTagName() == null)
            {
                continue;
            }
            String name = clip(tag.getTagName().strip(), 48);
            if (!name.isEmpty())
            {
                unique.putIfAbsent(name, tag.getTagId());
            }
        }
        var tags = unique.entrySet().stream()
            .map(entry -> Map.<String, Object>of("tagId", entry.getValue(), "name", entry.getKey()))
            .toList();
        return new ToolResult(tags, "已读取 " + tags.size() + " 个启用标签", null);
    }

    private ToolResult search(JsonNode args, long userId)
    {
        String keyword = text(args, "keyword", 80, false);
        Long tagId = args.has("tagId") ? id(args.path("tagId"), "tagId") : null;
        if (keyword.isEmpty() && tagId == null)
        {
            throw new IllegalArgumentException("请指定作品关键词或有效标签");
        }
        var enabledTags = bookstore.listTags(null, null).stream()
            .filter(tag -> "0".equals(tag.getStatus()) && tag.getTagId() != null && tag.getTagName() != null)
            .toList();
        String selectedTagName = null;
        if (tagId != null)
        {
            var matchedTag = enabledTags.stream()
                .filter(tag -> tagId.equals(tag.getTagId()))
                .findFirst()
                .orElse(null);
            if (matchedTag == null)
            {
                throw new IllegalArgumentException("请选择已启用的作品标签");
            }
            selectedTagName = matchedTag.getTagName().strip();
        }
        BigDecimal budget = args.has("maxPriceYuan") ? money(args, "maxPriceYuan") : null;
        Integer min = args.has("minEpisodes") ? integer(args, "minEpisodes", 1, Integer.MAX_VALUE) : null;
        Integer max = args.has("maxEpisodes") ? integer(args, "maxEpisodes", 1, Integer.MAX_VALUE) : null;
        if (min != null && max != null && min > max)
        {
            throw new IllegalArgumentException("最低集数不能超过最高集数");
        }
        if (args.has("tradeOnly") && !args.path("tradeOnly").isBoolean())
        {
            throw new IllegalArgumentException("tradeOnly 必须为布尔值");
        }
        boolean tradeOnly = args.path("tradeOnly").asBoolean(false);
        String sort = args.has("sort") ? text(args, "sort", 20, true) : "latest";
        if (!SORTS.contains(sort))
        {
            throw new IllegalArgumentException("排序方式无效");
        }
        int limit = args.has("limit") ? integer(args, "limit", 1, 10) : 8;

        // 构造检索计划：
        // 1) 若指定 tagId：自动展开同名的其他启用标签（不同频道/分组可能存在同名标签，如两个「种田」），
        //    且当 keyword 只是重复题材名（如「种田文」）时不拿它硬卡标题；
        // 2) 若仅传 keyword：除按标题检索外，自动匹配相关题材标签，避免书名不含题材词时搜不到。
        List<AppWorkQuery> queries = new ArrayList<>();
        if (tagId != null)
        {
            String effectiveKeyword = keyword;
            if (!keyword.isEmpty() && selectedTagName != null && keywordOverlapsTag(keyword, selectedTagName))
            {
                effectiveKeyword = "";
            }
            Set<Long> siblingIds = new LinkedHashSet<>();
            siblingIds.add(tagId);
            if (selectedTagName != null && !selectedTagName.isEmpty())
            {
                for (var tag : enabledTags)
                {
                    if (selectedTagName.equalsIgnoreCase(tag.getTagName().strip()))
                    {
                        siblingIds.add(tag.getTagId());
                    }
                }
            }
            for (Long id : siblingIds)
            {
                AppWorkQuery q = new AppWorkQuery();
                q.setKeyword(effectiveKeyword);
                q.setTagId(id);
                q.setSort(sort);
                queries.add(q);
            }
        }
        else
        {
            AppWorkQuery titleQuery = new AppWorkQuery();
            titleQuery.setKeyword(keyword);
            titleQuery.setSort(sort);
            queries.add(titleQuery);

            for (Long matchedTagId : findTagIdsForKeyword(keyword, enabledTags))
            {
                AppWorkQuery tagQuery = new AppWorkQuery();
                tagQuery.setKeyword("");
                tagQuery.setTagId(matchedTagId);
                tagQuery.setSort(sort);
                queries.add(tagQuery);
            }
        }

        List<Map<String, Object>> matches = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        boolean moreCandidates = false;
        for (AppWorkQuery query : queries)
        {
            if (matches.size() >= limit)
            {
                break;
            }
            boolean queryHasMore = false;
            for (int page = 1; page <= MAX_MARKET_PAGES && matches.size() < limit; page++)
            {
                var result = bookstore.pageWorks(query, page, MARKET_PAGE_SIZE);
                queryHasMore = result.getTotal() > (long) page * MARKET_PAGE_SIZE;
                for (AppWorkDto row : result.getList())
                {
                    if (!matches(row, userId, tradeOnly, budget, min, max) || !seen.add(row.getWorkId()))
                    {
                        continue;
                    }
                    AppWorkDto detail = bookstore.getWork(row.getWorkId());
                    if (matches(detail, userId, tradeOnly, budget, min, max))
                    {
                        Map<String, Object> item = workItem(detail, userId, 200);
                        List<String> reasons = new ArrayList<>();
                        if (query.getTagId() != null) reasons.add("关联所选题材标签");
                        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) reasons.add("标题匹配检索关键词");
                        if (budget != null) reasons.add("参考价在预算范围内");
                        if (min != null || max != null) reasons.add("集数符合筛选条件");
                        item.put("recommendationReason", String.join("、", reasons));
                        if (budget != null) item.put("budgetMatch", true);
                        matches.add(item);
                    }
                    if (matches.size() == limit)
                    {
                        break;
                    }
                }
                if (!queryHasMore || result.getList().isEmpty())
                {
                    break;
                }
            }
            moreCandidates = moreCandidates || queryHasMore;
        }
        boolean scanLimited = moreCandidates && matches.size() < limit;
        String summary = "找到 " + matches.size() + " 部符合筛选条件的作品"
            + (scanLimited ? "，已达本次检索范围" : "");
        var payload = new LinkedHashMap<String, Object>();
        payload.put("works", matches);
        if (scanLimited)
        {
            payload.put("scanLimited", true);
        }
        payload.put("criteria", String.join(" · ", searchSummary(args)));
        return new ToolResult(payload, summary, new Card("work_list", payload));
    }

    private static String normalizeGenreKeyword(String keyword)
    {
        String trimmed = keyword == null ? "" : keyword.strip();
        String stripped = GENRE_SUFFIX.matcher(trimmed).replaceAll("").strip();
        return stripped.length() >= 2 ? stripped : trimmed;
    }

    private static boolean keywordOverlapsTag(String keyword, String tagName)
    {
        String normKw = normalizeGenreKeyword(keyword).toLowerCase();
        String normTag = tagName == null ? "" : tagName.strip().toLowerCase();
        if (normKw.isEmpty() || normTag.isEmpty())
        {
            return false;
        }
        return normKw.equals(normTag)
            || (normKw.length() >= 2 && normTag.contains(normKw))
            || (normTag.length() >= 2 && normKw.contains(normTag));
    }

    private static List<Long> findTagIdsForKeyword(String keyword, List<SysTag> enabledTags)
    {
        String norm = normalizeGenreKeyword(keyword).toLowerCase();
        if (norm.isEmpty())
        {
            return List.of();
        }
        List<Long> exact = new ArrayList<>();
        List<Long> partial = new ArrayList<>();
        for (var tag : enabledTags)
        {
            String name = tag.getTagName().strip().toLowerCase();
            if (name.isEmpty())
            {
                continue;
            }
            if (name.equals(norm))
            {
                exact.add(tag.getTagId());
            }
            else if ((norm.length() >= 2 && name.contains(norm))
                || (name.length() >= 2 && norm.contains(name)))
            {
                partial.add(tag.getTagId());
            }
        }
        Set<Long> ordered = new LinkedHashSet<>();
        ordered.addAll(exact);
        ordered.addAll(partial);
        return ordered.stream().limit(MAX_MATCHED_TAG_IDS).toList();
    }

    private static boolean matches(AppWorkDto work, long userId, boolean tradeOnly, BigDecimal budget,
            Integer min, Integer max)
    {
        return visible(work)
            && (!tradeOnly || ("1".equals(work.getTradeEnabled()) && !Objects.equals(work.getAuthorId(), userId)))
            && (budget == null || (work.getPrice() != null && work.getPrice().compareTo(budget) <= 0))
            && (min == null || (work.getEpisodeCount() != null && work.getEpisodeCount() >= min))
            && (max == null || (work.getEpisodeCount() != null && work.getEpisodeCount() <= max));
    }

    private static List<String> searchSummary(JsonNode args)
    {
        List<String> summary = new ArrayList<>();
        if (args.has("keyword")) summary.add("关键词 " + text(args, "keyword", 80, false));
        if (args.has("tagId")) summary.add("标签 " + id(args.path("tagId"), "tagId"));
        if (args.has("maxPriceYuan")) summary.add("预算不超过 ¥" + money(args, "maxPriceYuan").toPlainString());
        if (args.has("minEpisodes")) summary.add("至少 " + integer(args, "minEpisodes", 1, Integer.MAX_VALUE) + " 集");
        if (args.has("maxEpisodes")) summary.add("最多 " + integer(args, "maxEpisodes", 1, Integer.MAX_VALUE) + " 集");
        return summary;
    }

    private ToolResult preview(JsonNode args)
    {
        long workId = id(args.path("workId"), "workId");
        int limit = args.has("maxChapters") ? integer(args, "maxChapters", 1, 2) : 2;
        AppWorkDto work = work(workId);
        List<Map<String, Object>> chapters = new ArrayList<>();
        var candidates = bookstore.getPreview(work).getPreviewChapters().stream()
            .filter(chapter -> Boolean.TRUE.equals(chapter.getReadable()) && chapter.getChapterId() != null)
            .sorted(java.util.Comparator.comparing(AppChapterDto::getChapterNo,
                java.util.Comparator.nullsLast(Integer::compareTo))).toList();
        for (AppChapterDto candidate : candidates)
        {
            // 不调用带阅读埋点的章节接口，也不使用当前用户的全文授权。
            SysWorkChapter chapter = chapterMapper.selectChapterDetail(candidate.getChapterId());
            if (chapter != null && Objects.equals(chapter.getWorkId(), workId)
                && ChapterTrialAccess.isOpen(chapter, work.getPreviewEnabled(), work.getPreviewEpisodes())
                && chapter.getContent() != null)
            {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("chapterNo", chapter.getChapterNo());
                item.put("chapterTitle", clip(chapter.getChapterTitle(), 100));
                item.put("content", plain(chapter.getContent(), 1500));
                chapters.add(item);
            }
            if (chapters.size() == limit)
            {
                break;
            }
        }
        var boards = storyboards.list(null, workId).stream()
            .filter(board -> "approved".equals(board.getStatus()))
            .limit(12).map(board -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("chapterNo", board.getChapterNo());
                row.put("chapterTitle", clip(board.getChapterTitle(), 100));
                row.put("wordCount", board.getWordCount());
                return row;
            }).toList();
        return new ToolResult(Map.of("workId", workId, "chapters", chapters, "storyboards", boards),
            "已读取 " + chapters.size() + " 章公开试读和 " + boards.size() + " 条已审分镜目录", null);
    }

    private ToolResult compare(JsonNode args, long userId)
    {
        JsonNode workIds = args.path("workIds");
        if (!workIds.isArray() || workIds.size() < 2 || workIds.size() > 4)
        {
            throw new IllegalArgumentException("请选择 2 到 4 部作品对比");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (JsonNode node : workIds)
        {
            if (!ids.add(id(node, "workIds")))
            {
                throw new IllegalArgumentException("对比作品不能重复");
            }
        }
        String criteria = text(args, "criteria", 400, true);
        var budgetHint = COMPARISON_BUDGET.matcher(criteria);
        BigDecimal budget = budgetHint.find() ? new BigDecimal(budgetHint.group(1)) : null;
        List<Map<String, Object>> works = ids.stream().map(id -> {
            AppWorkDto detail = work(id);
            Map<String, Object> item = workItem(detail, userId, 350);
            if (budget != null && detail.getPrice() != null)
            {
                item.put("budgetMatch", detail.getPrice().compareTo(budget) <= 0);
            }
            item.put("commercialHighlights", item.get("summary"));
            item.put("riskNotes", Boolean.TRUE.equals(item.get("requiresLicenseType"))
                ? "授权范围和独家状态需核实" : "版权登记及授权范围需核实");
            return item;
        }).toList();
        var payload = Map.of("works", works, "criteria", criteria);
        return new ToolResult(payload, "已对比 " + works.size() + " 部作品", new Card("work_comparison", payload));
    }

    private ToolResult draft(JsonNode args)
    {
        String task = text(args, "taskType", 32, true);
        if (!TASK_TYPES.contains(task))
        {
            throw new IllegalArgumentException("任务类型无效");
        }
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("title", text(args, "title", 120, true));
        brief.put("taskType", task);
        brief.put("genreTags", textList(args, "genreTags", 8, 40));
        brief.put("totalEpisodes", integer(args, "totalEpisodes", 1, Integer.MAX_VALUE));
        brief.put("budgetYuan", money(args, "budgetYuan"));
        brief.put("durationDays", integer(args, "durationDays", 1, Integer.MAX_VALUE));
        brief.put("commercialGoal", text(args, "commercialGoal", 500, true));
        brief.put("requirement", text(args, "requirement", 2000, true));
        brief.put("deliveryChecklist", textList(args, "deliveryChecklist", 12, 200));
        return new ToolResult(brief, "已生成待确认的采购需求草案", new Card("demand_brief", brief));
    }

    private AppWorkDto work(long workId)
    {
        AppWorkDto work = bookstore.getWork(workId);
        if (!visible(work))
        {
            throw new IllegalArgumentException("作品不存在或当前未上架");
        }
        return work;
    }

    private static boolean visible(AppWorkDto work)
    {
        return work != null && work.getWorkId() != null && "on_shelf".equals(work.getStatus());
    }

    private Map<String, Object> workItem(AppWorkDto work, long userId, int summaryLimit)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        boolean tradeEnabled = "1".equals(work.getTradeEnabled());
        item.put("workId", work.getWorkId());
        item.put("title", clip(work.getTitle(), 150));
        item.put("cover", clip(work.getCover(), 1024));
        item.put("authorName", clip(work.getAuthorName(), 80));
        item.put("tags", work.getTags() == null ? List.of() : work.getTags().stream()
            .limit(10).filter(tag -> tag.getTagName() != null).map(tag -> clip(tag.getTagName(), 48)).toList());
        item.put("price", work.getPrice());
        item.put("episodeCount", work.getEpisodeCount());
        item.put("wordCount", work.getWordCount());
        item.put("workType", work.getWorkType());
        item.put("uploadType", work.getUploadType());
        item.put("lengthType", work.getLengthType());
        item.put("duration", work.getDuration());
        item.put("tradeEnabled", tradeEnabled);
        item.put("canInquire", tradeEnabled && work.getAuthorId() != null && !work.getAuthorId().equals(userId));
        item.put("tradeType", work.getTradeType());
        item.put("freeChapterCount", work.getFreeChapterCount());
        item.put("previewEpisodes", work.getPreviewEpisodes());
        item.put("qualityLevel", work.getQualityLevel());
        item.put("viewCount", work.getViewCount());
        item.put("favoriteCount", work.getFavoriteCount());
        item.put("rating", work.getRating());
        item.put("saleCount", work.getSaleCount());
        item.put("copyrightRegistrationStatus", work.getCopyrightRegistrationStatus());
        item.put("copyrightCertNo", clip(work.getCopyrightCertNo(), 100));
        item.put("requiresLicenseType", inquiries.hasCopyright(work.getWorkId()));
        item.put("exclusiveStatus", "待核实");
        item.put("summary", plain(work.getSummary(), summaryLimit));
        return item;
    }

    private JsonNode arguments(String name, String rawArgs)
    {
        Tool tool = tool(name);
        if (tool == null)
        {
            throw new IllegalArgumentException("未知工具");
        }
        try
        {
            if (rawArgs == null || rawArgs.length() > 16_000)
            {
                throw new IllegalArgumentException("工具参数长度无效");
            }
            JsonNode args = json.readTree(rawArgs);
            if (args == null || !args.isObject())
            {
                throw new IllegalArgumentException("工具参数必须为 JSON 对象");
            }
            var fields = args.fieldNames();
            while (fields.hasNext())
            {
                if (!tool.properties().containsKey(fields.next()))
                {
                    throw new IllegalArgumentException("工具参数包含未定义字段");
                }
            }
            for (String field : tool.required())
            {
                if (!args.hasNonNull(field))
                {
                    throw new IllegalArgumentException("缺少必填字段 " + field);
                }
            }
            return args;
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException e)
        {
            throw new IllegalArgumentException("工具参数不是合法的 JSON 对象");
        }
    }

    private static Tool tool(String name)
    {
        return TOOLS.stream().filter(tool -> tool.name().equals(name)).findFirst().orElse(null);
    }

    private static long id(JsonNode node, String field)
    {
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 1)
        {
            throw new IllegalArgumentException(field + " 必须为正整数 ID");
        }
        return node.longValue();
    }

    private static int integer(JsonNode args, String field, int min, int max)
    {
        JsonNode node = args.path(field);
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < min || node.intValue() > max)
        {
            throw new IllegalArgumentException(field + " 超出允许的整数范围");
        }
        return node.intValue();
    }

    private static BigDecimal money(JsonNode args, String field)
    {
        JsonNode node = args.path(field);
        if (!node.isNumber())
        {
            throw new IllegalArgumentException(field + " 必须为元金额");
        }
        BigDecimal value;
        try
        {
            value = node.decimalValue().stripTrailingZeros();
        }
        catch (ArithmeticException | NumberFormatException e)
        {
            throw new IllegalArgumentException(field + " 最多支持两位小数");
        }
        if (value.scale() > 2)
        {
            throw new IllegalArgumentException(field + " 最多支持两位小数");
        }
        if (value.signum() < 0 || value.compareTo(new BigDecimal("9999999999.99")) > 0)
        {
            throw new IllegalArgumentException(field + " 超出允许的金额范围");
        }
        return value.setScale(2);
    }

    private static String text(JsonNode args, String field, int maxLength, boolean required)
    {
        JsonNode node = args.path(field);
        if (node.isMissingNode() && !required)
        {
            return "";
        }
        return checkedText(node, field, maxLength);
    }

    private static List<String> textList(JsonNode args, String field, int limit, int maxLength)
    {
        JsonNode node = args.path(field);
        if (!node.isArray() || node.isEmpty() || node.size() > limit)
        {
            throw new IllegalArgumentException(field + " 必须包含 1 到 " + limit + " 条内容");
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : node)
        {
            items.add(checkedText(item, field, maxLength));
        }
        return List.copyOf(new LinkedHashSet<>(items));
    }

    private static String checkedText(JsonNode node, String field, int maxLength)
    {
        String value = node.isTextual() ? node.asText().strip() : "";
        if (value.isEmpty() || value.codePointCount(0, value.length()) > maxLength)
        {
            throw new IllegalArgumentException(field + " 必须为有效文本，最多 " + maxLength + " 字");
        }
        return value;
    }

    private static String plain(String value, int maxLength)
    {
        return value == null ? "" : clip(Jsoup.parseBodyFragment(clip(value, maxLength * 8)).text(), maxLength);
    }

    static String clip(String value, int maxLength)
    {
        if (value == null)
        {
            return "";
        }
        int length = value.codePointCount(0, value.length());
        return length <= maxLength ? value : value.substring(0, value.offsetByCodePoints(0, maxLength));
    }

    private record Tool(String name, String label, String description, Map<String, Object> properties,
                        List<String> required) {}

    public record Card(String type, Map<String, Object> payload) {}

    public record ToolResult(Object data, String summary, Card card) {}
}
