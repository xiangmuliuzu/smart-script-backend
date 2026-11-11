package com.smartscript.platform.content.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.content.dto.AppAiOutlineRequest;
import com.smartscript.platform.content.dto.AppAiOutlineRecordItem;
import com.smartscript.platform.content.dto.AppAiOutlineResult;
import com.smartscript.platform.content.dto.AppAiPolishRequest;
import com.smartscript.platform.content.dto.AppAiStoryboardRequest;
import com.smartscript.platform.content.dto.AppAiStoryboardResult;
import com.smartscript.platform.content.dto.AppAiWriteRecordInsert;
import com.smartscript.platform.content.dto.AppAiWriteRecordItem;
import com.smartscript.platform.content.dto.AppAiWriteRequest;
import com.smartscript.platform.content.dto.AppAiWriteResult;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.mapper.AppAiRequestMapper;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.exception.AiQuotaInsufficientException;
import com.smartscript.platform.content.exception.AiServiceUnavailableException;
import com.smartscript.platform.content.domain.AiChannel;
import com.smartscript.platform.content.domain.SysWorkLedger;
import com.smartscript.platform.content.service.AiCallResult;
import com.smartscript.platform.content.service.AiServiceClient;
import com.smartscript.platform.content.service.ChapterSummarySupport;
import com.smartscript.platform.content.service.ChapterReviewSupport;
import com.smartscript.platform.content.service.IAiChannelService;
import com.smartscript.platform.content.service.IAiWriteService;
import com.smartscript.platform.identity.IdentityProvider;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * AI 辅助创作 服务实现（App 2.9.10 ~ 2.9.13）。
 *
 * 依据：接口文档 2.9.10~2.9.13 + 云端 script_platform_dev 库
 * sys_ai_write_record 表（附件5.1 表3-22）/ sys_ai_request 表（附件5.1 表3-26）。
 *
 * 反推处理点：
 * 1. 内容来源：写作/润色/大纲一律转发到外部 AI 服务（{@link AiServiceClient}），
 *    失败即抛异常由控制器转 503，**不返回任何编造内容**。
 * 2. sys_ai_write_record 多个 NOT NULL 列契约未提供入参，落库时空缺列填空串：
 *    pov / language_style / pace 填空串，title 取输入前 50 字，tokens_used 取 AI 返回的
 *    usage.total_tokens，ai_model 取 AI 返回（缺省取配置），status='success'；
 *    write_type：写作取入参 type（缺省 write），润色固定 'polish'；input_prompt：
 *    写作取 prompt，润色取待润色原文；output_content 取 AI 返回。
 * 3. 大纲落 sys_ai_request：capability='outline'、quota_cost=1、provider 取配置、
 *    request_no 由本层唯一生成；status='success'，started_at/finished_at 显式写入。
 * 4. 【去次数化】AI 调用成功即按本次实际 token 消耗扣减积分：读取配置
 *    ai.token.per.point（1 积分 = N token，管理员可调，默认 1000），换算
 *    消耗积分 = max(1, ceil(totalTokens / N))；乐观锁扣 sys_points_account.balance，
 *    并写 sys_points_record 流水（change_type='SPEND'，source='AI'，related_id=记录ID，
 *    remark 记录 token 与积分）。校验与扣减均在本服务内用 JdbcTemplate 直写，
 *    不跨模块依赖 review 的 AiQuotaService。
 * 5. 身份不做空值兜底：四接口均未登记 App 凭证域白名单，过滤器已保证非游客，currentUserId() 不会为 null。
 *
 * @author xiangsipeng
 */
@Service
public class AiWriteServiceImpl implements IAiWriteService
{
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AiWriteServiceImpl.class);

    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页条数上限 */
    private static final int PAGE_SIZE_MAX = 50;

    /** 写作默认类型（type 为空时） */
    private static final String WRITE_TYPE_DEFAULT = "write";

    /** 无正文的续写指令不能确定需要续写的作品。 */
    private static final Pattern CONTEXT_ONLY_CONTINUATION = Pattern.compile(
            "(?iu)\\s*(?:请\\s*)?(?:继续(?:写(?:作|下去)?|生成)?|接着写|续写|continue(?:\\s+writing)?)(?:吧)?[\\s。！!，,.]*");

    /** 润色写作类型 */
    private static final String WRITE_TYPE_POLISH = "polish";

    /** 成功状态 */
    private static final String STATUS_SUCCESS = "success";

    /** 大纲能力标识 */
    private static final String CAPABILITY_OUTLINE = "outline";

    /** 分镜能力标识（E 模块：剧本→分镜一条龙） */
    private static final String CAPABILITY_STORYBOARD = "storyboard";

    /** 本章摘要回写能力标识（write_type / 积分流水 remark） */
    private static final String CAPABILITY_CHAPTER_SUMMARY = "chapter-summary";

    /** 大纲消耗额度（本批固定 1） */
    private static final int QUOTA_COST_OUTLINE = 1;

    /** 去次数化：1 积分默认对应 token 数（sys_config key=ai.token.per.point，管理员可调，默认 1000） */
    private static final int DEFAULT_TOKEN_PER_POINT = 1000;

    /** 每次 AI 调用最低扣 1 积分（防止 0 积分调用） */
    private static final int MIN_POINTS_COST = 1;

    /** 标题长度上限（sys_ai_write_record.title 为 varchar(100)，按业务约定收紧到 50） */
    private static final int TITLE_MAX_LENGTH = 50;

    /**
     * input_prompt 落库上限（字符）。列已扩为 LONGTEXT，但长连载验收/卷快照的提示词会随集数增长，
     * 这里再兜一层，避免旧库（TEXT，64KB）或异常超长输入把整条记录插入失败、连带 AI 结果丢失。
     */
    private static final int PROMPT_MAX_LENGTH = 60000;

    /** 填空串的占位值（用于 NOT NULL 且契约未提供的列） */
    private static final String BLANK = "";

    /** 模型名兜底值 */
    private static final String DEFAULT_MODEL = "smartscript-ai";

    /** 把大纲 JsonNode 转成普通 Map/List（项目响应 ObjectMapper 无法序列化 JsonNode） */
    private static final ObjectMapper OUTLINE_MAPPER = new ObjectMapper();

    @Autowired
    private AppAiWriteRecordMapper writeRecordMapper;

    @Autowired
    private AppAiRequestMapper aiRequestMapper;

    @Autowired
    private AiServiceClient aiServiceClient;

    @Autowired
    private IAiChannelService aiChannelService;

    @Autowired
    private com.smartscript.platform.content.service.IAiSkillService aiSkillService;

    @Autowired
    private IdentityProvider identityProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.smartscript.platform.content.service.IAppWorkLedgerService workLedgerService;

    @Autowired(required = false)
    private com.smartscript.platform.review.service.RiskCheckService riskCheckService;

    /** 内容风控（测试无 Bean 时跳过，生产必注入） */
    private void checkContent(Long userId, String text)
    {
        if (riskCheckService != null && text != null && !text.isBlank())
        {
            riskCheckService.checkContentOrThrow(userId, text);
        }
    }

    /** AI 模型名（AI 未返回时使用） */
    @Value("${AI_SERVICE_MODEL:smartscript-ai}")
    private String aiModel;

    /** AI 服务提供方（落 sys_ai_request.provider） */
    @Value("${AI_SERVICE_PROVIDER:smartscript-ai}")
    private String aiProvider;

    @Override
    public AppAiWriteResult write(AppAiWriteRequest request)
    {
        requireContinuationContext(request.getPrompt());
        checkContent(identityProvider.currentUserId(), request.getPrompt());
        checkPoints(identityProvider.currentUserId());
        AiChannel channel = resolveChannel(request.getModel());
        AiCallResult data = (channel == null)
                ? aiServiceClient.write(request.getPrompt(), request.getType(),
                        resolveSkillContent(request.getSkillId()))
                : aiServiceClient.write(request.getPrompt(), request.getType(),
                        resolveSkillContent(request.getSkillId()),
                        channel.getBaseUrl(), channel.getApiKey(), request.getModel());
        String content = data.getContent();
        Long recordId = saveRecord(identityProvider.currentUserId(), request.getWorkId(),
                resolveWriteType(request.getType()), request.getPrompt(), content,
                resolveModel(data), data.getTotalTokens());
        consumePoints(identityProvider.currentUserId(), "write", recordId, data.getTotalTokens());
        return new AppAiWriteResult(content, recordId, data.isTruncated());
    }

    @Override
    public AppAiWriteResult polish(AppAiPolishRequest request)
    {
        checkPoints(identityProvider.currentUserId());
        checkContent(identityProvider.currentUserId(), request.getContent());
        AiChannel channel = resolveChannel(request.getModel());
        AiCallResult data = (channel == null)
                ? aiServiceClient.polish(request.getContent(), request.getStyle(),
                        resolveSkillContent(request.getSkillId()))
                : aiServiceClient.polish(request.getContent(), request.getStyle(),
                        resolveSkillContent(request.getSkillId()),
                        channel.getBaseUrl(), channel.getApiKey(), request.getModel());
        String content = data.getContent();
        Long recordId = saveRecord(identityProvider.currentUserId(), null,
                WRITE_TYPE_POLISH, request.getContent(), content,
                resolveModel(data), data.getTotalTokens());
        consumePoints(identityProvider.currentUserId(), "polish", recordId, data.getTotalTokens());
        return new AppAiWriteResult(content, recordId, data.isTruncated());
    }

    @Override
    public AppPageResult<AppAiWriteRecordItem> pageWriteRecords(int pageNum, int pageSize)
    {
        int safePageNum = pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, PAGE_SIZE_MAX);
        Long userId = identityProvider.currentUserId();
        PageHelper.startPage(safePageNum, safePageSize);
        List<AppAiWriteRecordItem> rows = writeRecordMapper.selectWriteRecordsByUser(userId);
        long total = new PageInfo<>(rows).getTotal();
        return AppPageResult.of(total, rows);
    }

    @Override
    public int deleteWriteRecords(List<Long> recordIds)
    {
        if (recordIds == null || recordIds.isEmpty())
        {
            return 0;
        }
        // 去重并只保留合法主键，避免生成 IN () 或负值条件。
        List<Long> ids = recordIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (ids.isEmpty())
        {
            return 0;
        }
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return 0;
        }
        return writeRecordMapper.deleteWriteRecords(userId, ids);
    }

    @Override
    public AppPageResult<AppAiOutlineRecordItem> pageOutlineRecords(int pageNum, int pageSize)
    {
        int safePageNum = pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, PAGE_SIZE_MAX);
        Long userId = identityProvider.currentUserId();
        PageHelper.startPage(safePageNum, safePageSize);
        List<AppAiOutlineRecordItem> rows = aiRequestMapper.selectOutlineRecordsByUser(userId);
        long total = new PageInfo<>(rows).getTotal();
        return AppPageResult.of(total, rows);
    }

    @Override
    public AppAiWriteResult writeStream(AppAiWriteRequest request, java.util.function.Consumer<String> onDelta)
    {
        requireContinuationContext(request.getPrompt());
        checkContent(identityProvider.currentUserId(), request.getPrompt());
        checkPoints(identityProvider.currentUserId());
        AiChannel channel = resolveChannel(request.getModel());
        AiCallResult r = (channel == null)
                ? aiServiceClient.writeStream(request.getPrompt(), request.getType(),
                        resolveSkillContent(request.getSkillId()), onDelta)
                : aiServiceClient.writeStream(request.getPrompt(), request.getType(),
                        resolveSkillContent(request.getSkillId()), onDelta,
                        channel.getBaseUrl(), channel.getApiKey(), request.getModel());
        String content = r.getContent();
        Long recordId = saveRecord(identityProvider.currentUserId(), request.getWorkId(),
                resolveWriteType(request.getType()), request.getPrompt(), content,
                resolveModel(r), r.getTotalTokens());
        consumePoints(identityProvider.currentUserId(), "write", recordId, r.getTotalTokens());
        return new AppAiWriteResult(content, recordId, r.isTruncated());
    }

    @Override
    public AppAiWriteResult polishStream(AppAiPolishRequest request, java.util.function.Consumer<String> onDelta)
    {
        checkPoints(identityProvider.currentUserId());
        checkContent(identityProvider.currentUserId(), request.getContent());
        AiChannel channel = resolveChannel(request.getModel());
        AiCallResult r = (channel == null)
                ? aiServiceClient.polishStream(request.getContent(), request.getStyle(),
                        resolveSkillContent(request.getSkillId()), onDelta)
                : aiServiceClient.polishStream(request.getContent(), request.getStyle(),
                        resolveSkillContent(request.getSkillId()), onDelta,
                        channel.getBaseUrl(), channel.getApiKey(), request.getModel());
        String content = r.getContent();
        Long recordId = saveRecord(identityProvider.currentUserId(), null,
                WRITE_TYPE_POLISH, request.getContent(), content,
                resolveModel(r), r.getTotalTokens());
        consumePoints(identityProvider.currentUserId(), "polish", recordId, r.getTotalTokens());
        return new AppAiWriteResult(content, recordId, r.isTruncated());
    }

    /**
     * 大纲生成前拼创作台账上下文（只取当前用户自己的作品，避免把他人台账带进提示词）。
     * 无 workId、非本人作品或台账为空时返回 null，此时 AI 只按灵感与风格生成。
     */
    private String buildLedgerContext(Long userId, Long workId)
    {
        if (workId == null || userId == null)
        {
            return null;
        }
        Long authorId;
        try
        {
            List<Long> rows = jdbcTemplate.queryForList(
                    "SELECT author_id FROM sys_work WHERE work_id = ? AND is_deleted = 0", Long.class, workId);
            authorId = rows.isEmpty() ? null : rows.get(0);
        }
        catch (Exception e)
        {
            log.warn("读取作品归属失败 workId={}：{}", workId, e.getMessage());
            return null;
        }
        if (authorId == null || !authorId.equals(userId))
        {
            return null;
        }
        SysWorkLedger ledger = workLedgerService.getLedger(workId);
        StringBuilder sb = new StringBuilder();
        appendLedgerLine(sb, "故事简介", ledger.getStorySummary());
        appendLedgerLine(sb, "人物卡", ledger.getCharacters());
        appendLedgerLine(sb, "关系状态", ledger.getRelations());
        appendLedgerLine(sb, "伏笔（埋在哪章/计划回收章/是否回收）", ledger.getForeshadow());
        appendLedgerLine(sb, "时间线", ledger.getTimeline());
        appendLedgerLine(sb, "物品位置", ledger.getItems());
        appendLedgerLine(sb, "重要道具档案（件数、持有人、是否已交付以它为准）", truncate(ledger.getProps(), 2000));
        appendLedgerLine(sb, "写法基线（沿用既有文风，不得中途换写法）", truncate(ledger.getStyleGuide(), 800));
        appendLedgerLine(sb, "未解线索", ledger.getThreads());
        return sb.length() == 0 ? null : sb.toString();
    }

    /** 台账单类拼接（空值跳过）。 */
    private static void appendLedgerLine(StringBuilder sb, String label, String value)
    {
        if (value != null && !value.isBlank())
        {
            sb.append(label).append('：').append(value.trim()).append('\n');
        }
    }

    /**
     * 归属校验只查 sys_work.author_id，不看台账是否有内容。
     * buildLedgerContext 在台账为空时也返回 null，拿它当归属校验会把「台账空但确实是本人作品」
     * 误判成「作品不存在」，导致卷快照永远生成不了。
     */
    private void assertWorkOwner(Long userId, Long workId)
    {
        Long authorId;
        try
        {
            List<Long> rows = jdbcTemplate.queryForList(
                    "SELECT author_id FROM sys_work WHERE work_id = ? AND is_deleted = 0", Long.class, workId);
            authorId = rows.isEmpty() ? null : rows.get(0);
        }
        catch (Exception e)
        {
            log.warn("读取作品归属失败 workId={}：{}", workId, e.getMessage());
            throw new IllegalArgumentException("读取作品归属失败");
        }
        if (authorId == null || userId == null || !authorId.equals(userId))
        {
            throw new IllegalArgumentException("作品不存在或不属于当前用户");
        }
    }

    @Override
    public AppAiOutlineResult outlineStream(AppAiOutlineRequest request, java.util.function.Consumer<String> onDelta)
    {
        checkPoints(identityProvider.currentUserId());
        checkContent(identityProvider.currentUserId(),
                (request.getInspiration() == null ? "" : request.getInspiration()));
        Date startedAt = new Date();
        AiChannel channel = resolveChannel(request.getModel());
        String ledgerContext = buildLedgerContext(identityProvider.currentUserId(), request.getWorkId());
        AiCallResult r = (channel == null)
                ? aiServiceClient.outlineStream(request.getInspiration(), request.getGenre(), request.getStyle(),
                        resolveSkillContent(request.getSkillId()), ledgerContext, onDelta)
                : aiServiceClient.outlineStream(request.getInspiration(), request.getGenre(), request.getStyle(),
                        resolveSkillContent(request.getSkillId()), ledgerContext, onDelta,
                        channel.getBaseUrl(), channel.getApiKey(), request.getModel());
        Date finishedAt = new Date();
        JsonNode outlineNode = parseOutlineJson(r.getContent());
        Object outline = outlineNode.isMissingNode() || outlineNode.isNull()
                ? null : OUTLINE_MAPPER.convertValue(outlineNode, Object.class);
        String requestNo = generateRequestNo();
        aiRequestMapper.insertAiRequest(requestNo, identityProvider.currentUserId(), request.getWorkId(),
                CAPABILITY_OUTLINE, Math.max(1, r.getTotalTokens()), STATUS_SUCCESS, resolveProvider(),
                sha256(buildOutlinePrompt(request)), startedAt, finishedAt);
        Long recordId = saveRecord(identityProvider.currentUserId(), request.getWorkId(),
                CAPABILITY_OUTLINE, buildOutlineInputText(request), r.getContent(),
                resolveModel(r), r.getTotalTokens());
        consumePoints(identityProvider.currentUserId(), "outline", recordId, r.getTotalTokens());
        AppAiOutlineResult result = new AppAiOutlineResult(outline, requestNo,
                Math.max(1, r.getTotalTokens()), false);
        result.setContent(r.getContent());
        return result;
    }

    /** 把 AI 流式返回的完整大纲文本解析为 JsonNode（与同步路径共用 parseJson 语义）。 */
    private JsonNode parseOutlineJson(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            return OUTLINE_MAPPER.createObjectNode();
        }
        String s = raw.trim();
        if (s.startsWith("```"))
        {
            int newline = s.indexOf('\n');
            if (newline >= 0)
            {
                s = s.substring(newline + 1);
            }
            if (s.endsWith("```"))
            {
                s = s.substring(0, s.length() - 3);
            }
            s = s.trim();
        }
        try
        {
            return OUTLINE_MAPPER.readTree(s);
        }
        catch (Exception e)
        {
            // 兜底：整段文本放入 text 字段，避免编造结构
            ObjectNode fallback = OUTLINE_MAPPER.createObjectNode();
            fallback.put("text", raw);
            return fallback;
        }
    }

    @Override
    public AppAiOutlineResult outline(AppAiOutlineRequest request)
    {
        checkPoints(identityProvider.currentUserId());
        checkContent(identityProvider.currentUserId(),
                (request.getInspiration() == null ? "" : request.getInspiration()));
        Date startedAt = new Date();
        AiChannel channel = resolveChannel(request.getModel());
        AiCallResult r = (channel == null)
                ? aiServiceClient.outline(request.getInspiration(), request.getGenre(), request.getStyle(),
                        resolveSkillContent(request.getSkillId()))
                : aiServiceClient.outline(request.getInspiration(), request.getGenre(), request.getStyle(),
                        resolveSkillContent(request.getSkillId()),
                        channel.getBaseUrl(), channel.getApiKey(), request.getModel());
        Date finishedAt = new Date();
        JsonNode outlineNode = parseOutlineJson(r.getContent()).path("outline");
        Object outline = outlineNode.isMissingNode() || outlineNode.isNull()
                ? null : OUTLINE_MAPPER.convertValue(outlineNode, Object.class);
        String requestNo = generateRequestNo();
        aiRequestMapper.insertAiRequest(requestNo, identityProvider.currentUserId(), request.getWorkId(),
                CAPABILITY_OUTLINE, Math.max(1, r.getTotalTokens()), STATUS_SUCCESS, resolveProvider(),
                sha256(buildOutlinePrompt(request)), startedAt, finishedAt);
        // 同步写一条可回看记录（write_type='outline'）：输入（灵感/类型/风格）与大纲结果
        // 完整落 sys_ai_write_record，供 AI 记录页按写作/润色同款「输入/输出」样式展示。
        Long recordId = saveRecord(identityProvider.currentUserId(), request.getWorkId(),
                CAPABILITY_OUTLINE, buildOutlineInputText(request), outlineToText(outline),
                resolveModel(r), r.getTotalTokens());
        consumePoints(identityProvider.currentUserId(), "outline", recordId, r.getTotalTokens());
        return new AppAiOutlineResult(outline, requestNo, Math.max(1, r.getTotalTokens()), false);
    }

    private static void requireContinuationContext(String prompt)
    {
        // 当前接口不隐式读取历史记录；无正文时不让模型猜测需要续写的作品。
        if (prompt != null && CONTEXT_ONLY_CONTINUATION.matcher(prompt).matches())
        {
            throw new IllegalArgumentException("请提供需要续写的正文，或在作品编辑器中基于当前正文续写");
        }
    }

    /**
     * 【去次数化】校验用户积分余额：读取 sys_points_account.balance，
     * 无账户或不足 1 积分即抛 {@link AiQuotaInsufficientException}（消息为积分文案），由控制器转 400。
     * 本方法只校验；实际扣减在 AI 调用成功后由 {@link #consumePoints} 完成。
     */
    private void checkPoints(Long userId)
    {
        Integer balance;
        try
        {
            balance = jdbcTemplate.queryForObject(
                    "SELECT balance FROM sys_points_account WHERE user_id = ?",
                    Integer.class, userId);
        }
        catch (org.springframework.dao.EmptyResultDataAccessException e)
        {
            balance = null;
        }
        int b = balance == null ? 0 : balance;
        if (b < MIN_POINTS_COST)
        {
            throw new AiQuotaInsufficientException("积分不足，当前余额: " + b + " 积分，请先完成任务或观看广告获取积分");
        }
    }

    /**
     * 【去次数化】AI 调用成功后按本次实际 token 消耗扣减积分并写积分流水。
     *
     * 换算：读取配置 ai.token.per.point（1 积分 = N token，默认 1000），
     * 消耗积分 = max(1, ceil(totalTokens / N))；响应无 usage 时按最低 1 积分计。
     * 乐观锁：UPDATE 带 balance >= 需扣积分条件，行数为 0 时不写流水直接返回（并发兜底）。
     * 流水：写 sys_points_record（change_type='SPEND'，source='AI'，related_id=本次记录ID，
     * remark 记录 token 与积分），同一次调用 recordId 唯一，天然幂等。
     */
    private void consumePoints(Long userId, String capability, Long businessId, int totalTokens)
    {
        int tokenPerPoint = readTokenPerPoint();
        int points = totalTokens <= 0 ? MIN_POINTS_COST
                : Math.max(MIN_POINTS_COST, (totalTokens + tokenPerPoint - 1) / tokenPerPoint);
        int rows = jdbcTemplate.update(
                "UPDATE sys_points_account SET balance = balance - ?, last_update_date = NOW() "
                        + "WHERE user_id = ? AND balance >= ?",
                points, userId, points);
        if (rows == 0)
        {
            return; // checkPoints 已前置拦截，此处并发兜底
        }
        Integer after;
        try
        {
            after = jdbcTemplate.queryForObject(
                    "SELECT balance FROM sys_points_account WHERE user_id = ?",
                    Integer.class, userId);
        }
        catch (org.springframework.dao.EmptyResultDataAccessException e)
        {
            after = null;
        }
        int before = (after == null ? points : after) + points;
        try
        {
            jdbcTemplate.update(
                    "INSERT INTO sys_points_record (user_id, change_type, points, balance_after, "
                            + "source, related_id, remark, created_at) "
                            + "VALUES (?, 'SPEND', ?, ?, 'AI', ?, ?, NOW())",
                    userId, points, after == null ? before : after, businessId,
                    "AI创作消耗(" + capability + "): " + totalTokens + " token = " + points + " 积分");
        }
        catch (Exception e)
        {
            // 流水失败不回滚积分（主业务已完成）；日志留痕便于对账
            log.error("写积分流水失败 userId={} businessId={}: {}", userId, businessId, e.getMessage());
        }
    }

    /** 读取「1 积分 = N token」配置（sys_config key=ai.token.per.point）；缺失/非法回退默认 1000。 */
    private int readTokenPerPoint()
    {
        try
        {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT config_value FROM sys_config WHERE config_key = 'ai.token.per.point' LIMIT 1");
            if (!rows.isEmpty() && rows.get(0).get("config_value") != null)
            {
                int v = Integer.parseInt(String.valueOf(rows.get(0).get("config_value")).trim());
                if (v >= 1)
                {
                    return v;
                }
            }
        }
        catch (Exception e)
        {
            log.warn("读取 ai.token.per.point 配置失败，使用默认值: {}", e.getMessage());
        }
        return DEFAULT_TOKEN_PER_POINT;
    }

    /**
     * 落一条 AI 写作记录并回填记录ID。
     *
     * @param tokens 本次调用实际消耗 token 数（usage.total_tokens，落 tokens_used）
     */
    private Long saveRecord(Long userId, Long workId, String writeType, String inputPrompt, String outputContent, String model, int tokens)
    {
        AppAiWriteRecordInsert row = new AppAiWriteRecordInsert();
        row.setUserId(userId);
        row.setWorkId(workId);
        row.setTitle(truncate(inputPrompt, TITLE_MAX_LENGTH));
        row.setWriteType(writeType);
        row.setPov(BLANK);
        row.setLanguageStyle(BLANK);
        row.setPace(BLANK);
        row.setInputPrompt(inputPrompt == null ? BLANK : truncate(inputPrompt, PROMPT_MAX_LENGTH));
        row.setOutputContent(outputContent == null ? BLANK : outputContent);
        row.setWordCount(outputContent == null ? 0 : outputContent.length());
        row.setAiModel(model);
        row.setTokensUsed(tokens);
        row.setStatus(STATUS_SUCCESS);
        writeRecordMapper.insertWriteRecord(row);
        return row.getRecordId();
    }

    /** 写作类型：入参为空回退默认 write */
    private String resolveWriteType(String type)
    {
        return type == null || type.isBlank() ? WRITE_TYPE_DEFAULT : type;
    }

    /** 模型名：优先 AI 返回，其次配置，最后兜底 */
    private String resolveModel(AiCallResult r)
    {
        if (r.getModel() != null && !r.getModel().isBlank())
        {
            return r.getModel();
        }
        return aiModel == null || aiModel.isBlank() ? DEFAULT_MODEL : aiModel;
    }

    /** 服务提供方：配置为空时兜底 */
    private String resolveProvider()
    {
        return aiProvider == null || aiProvider.isBlank() ? DEFAULT_MODEL : aiProvider;
    }

    /** 生成大纲提示词摘要原文（用于 prompt_hash，不含敏感信息） */
    private static String buildOutlinePrompt(AppAiOutlineRequest request)
    {
        return "inspiration=" + safe(request.getInspiration())
                + ";genre=" + safe(request.getGenre())
                + ";style=" + safe(request.getStyle());
    }

    /** 大纲输入的展示文本（落 sys_ai_write_record.input_prompt，用户可见） */
    private static String buildOutlineInputText(AppAiOutlineRequest request)
    {
        return "灵感：" + safe(request.getInspiration())
                + "\n类型：" + safe(request.getGenre())
                + "\n风格：" + safe(request.getStyle());
    }

    /** 大纲结果转可读文本（落 output_content，用户可见，非 JSON） */
    private static String outlineToText(Object outline)
    {
        if (outline instanceof Map<?, ?> map && map.size() == 1 && map.get("text") instanceof String text)
        {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        appendOutline(sb, outline, 0, null);
        String text = sb.toString().trim();
        return text.isEmpty() ? BLANK : text;
    }

    /** 递归把大纲对象渲染成「字段：值」的多行文本（parentKey 用于区分 name 在不同上下文的含义） */
    private static void appendOutline(StringBuilder sb, Object node, int depth, String parentKey)
    {
        if (node == null)
        {
            return;
        }
        if (node instanceof Map)
        {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet())
            {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();
                String label = outlineLabel(key, parentKey);
                if (value instanceof List)
                {
                    List<?> list = (List<?>) value;
                    boolean scalar = list.stream()
                            .allMatch(x -> !(x instanceof Map) && !(x instanceof List));
                    appendLine(sb, label, depth);
                    if (list.isEmpty())
                    {
                        continue;
                    }
                    if (scalar && isStorySequence(key))
                    {
                        // 剧情/章节/步骤类：每项一行
                        for (Object item : list)
                        {
                            appendLine(sb, "  " + item, depth);
                        }
                    }
                    else if (scalar)
                    {
                        // 角色/标签类：顿号连接
                        sb.append(list.stream().map(String::valueOf)
                                .collect(Collectors.joining("、")));
                    }
                    else
                    {
                        appendOutline(sb, list, depth + 1, key);
                    }
                }
                else if (value instanceof Map)
                {
                    appendLine(sb, label, depth);
                    appendOutline(sb, value, depth + 1, key);
                }
                else
                {
                    appendLine(sb, label + "：" + value, depth);
                }
            }
        }
        else if (node instanceof List)
        {
            for (Object item : (List<?>) node)
            {
                appendOutline(sb, item, depth + 1, parentKey);
            }
        }
        else
        {
            appendLine(sb, String.valueOf(node), depth);
        }
    }

    /** 剧情/章节/步骤类 key：数组每项单独一行 */
    private static boolean isStorySequence(String key)
    {
        return key.equals("acts") || key.equals("plot") || key.equals("chapters")
                || key.equals("scenes") || key.equals("steps") || key.equals("outline")
                || key.equals("development");
    }

    /** 常见大纲字段 → 中文标签（未知 key 原样保留；name 按父上下文区分含义） */
    private static String outlineLabel(String key, String parentKey)
    {
        switch (key)
        {
            case "title": return "书名";
            case "logline": return "一句话简介";
            case "summary": return "简介";
            case "characters": return "角色";
            case "roles": return "角色";
            case "acts": return "剧情";
            case "plot": return "剧情";
            case "chapters": return "章节";
            case "setting": return "背景设定";
            case "worldview": return "世界观";
            case "ending": return "结局";
            case "theme": return "主题";
            case "role": return "身份";
            case "beats": return "关键情节";
            case "intro": return "简介";
            case "content": return "内容";
            case "genre": return "题材";
            case "style": return "风格";
            case "name":
                if ("characters".equals(parentKey)) return "角色名";
                if ("acts".equals(parentKey)) return "幕名";
                return "名称";
            default: return key;
        }
    }

    private static void appendLine(StringBuilder sb, String text, int depth)
    {
        if (sb.length() > 0)
        {
            sb.append('\n');
        }
        for (int i = 0; i < depth; i++)
        {
            sb.append("  ");
        }
        sb.append(text);
    }

    private static String safe(String value)
    {
        return value == null ? BLANK : value;
    }

    /** 截断到指定长度（按字符计） */
    private static String truncate(String value, int maxLength)
    {
        if (value == null || value.isEmpty())
        {
            return BLANK;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** 生成唯一请求编号：AIR + 时间戳(毫秒) + 6 位随机十六进制，长度远小于 varchar(64) */
    private static String generateRequestNo()
    {
        return generateRecordNo("AIR");
    }

    /** 生成唯一流水号：{prefix} + 时间戳(毫秒) + 6 位随机十六进制 */
    private static String generateRecordNo(String prefix)
    {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return prefix + timestamp + random.toUpperCase();
    }

    /** sha256 十六进制摘要（sys_ai_request.prompt_hash 为 varchar(128)，64 位十六进制足够） */
    private static String sha256(String value)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes)
            {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            // SHA-256 是 JDK 必备算法，正常不会走到；返回 null 允许 prompt_hash 可空
            return null;
        }
    }

    /** 导演(skill) 轻量列表（E 模块，供端上「导演(skill)选择」下拉） */
    @Override
    public List<Map<String, Object>> listEnabledSkills()
    {
        return aiSkillService.selectEnabledSkillBrief().stream().map(skill -> {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", skill.getId());
            item.put("skillName", skill.getSkillName());
            item.put("skillType", skill.getSkillType() == null || skill.getSkillType().isBlank() ? "write" : skill.getSkillType());
            item.put("fileName", skill.getFileName());
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 导演(skill) 内容解析：skillId 为空/不存在/停用/内容为空 → null（不注入）；
     * 正常则返回 skill 文件内容（trim），由 {@link AiServiceClient#appendSkill} 注入 system prompt。
     */
    @Override
    public List<Map<String, Object>> listEnabledModels()
    {
        return aiChannelService.listEnabledModels();
    }

    /**
     * 本章摘要 + 台账回写（方案 2）：正文定稿后单独一次非流式调用，
     * 读本集正文产出「本章摘要 + 七类台账更新后的完整文本」，
     * 由后端写 summary 并合并台账；正文完全不动。
     */
    @Override
    public Map<String, Object> summarizeChapter(Long workId, Long chapterId, String content, String model)
    {
        Long userId = identityProvider.currentUserId();
        if (workId == null || chapterId == null)
        {
            throw new IllegalArgumentException("缺少作品或章节");
        }
        List<Map<String, Object>> rows;
        try
        {
            rows = jdbcTemplate.queryForList(
                    "SELECT w.author_id, c.chapter_no, c.chapter_title, c.content "
                            + "FROM sys_work w JOIN sys_work_chapter c ON c.work_id = w.work_id "
                            + "WHERE w.work_id = ? AND c.chapter_id = ? AND w.is_deleted = 0",
                    workId, chapterId);
        }
        catch (Exception e)
        {
            log.warn("读取章节失败 workId={} chapterId={}：{}", workId, chapterId, e.getMessage());
            throw new IllegalArgumentException("章节不存在或不属于该作品");
        }
        if (rows.isEmpty())
        {
            throw new IllegalArgumentException("章节不存在或不属于该作品");
        }
        Map<String, Object> row = rows.get(0);
        Object authorObj = row.get("author_id");
        Long authorId = authorObj instanceof Number number ? number.longValue() : null;
        if (authorId == null || !authorId.equals(userId))
        {
            throw new IllegalArgumentException("无权操作该作品");
        }
        String chapterNo = String.valueOf(row.get("chapter_no"));
        String chapterTitle = row.get("chapter_title") == null ? BLANK : String.valueOf(row.get("chapter_title"));
        // 章节标题通常已是「第 N 集」，直接用；为空才用集号兜底，避免「第5集 第 5 集」这类重复。
        String displayTitle = chapterTitle.isBlank() ? ("第" + chapterNo + "集") : chapterTitle;
        String dbContent = row.get("content") == null ? BLANK : String.valueOf(row.get("content"));
        // 前端刚把 AI 续写插入正文、尚未保存时会传 content；为空则用库中正文。
        String body = (content == null || content.isBlank()) ? dbContent : content;
        body = body == null ? BLANK : body.trim();
        if (body.length() < ChapterSummarySupport.MIN_BODY)
        {
            throw new IllegalArgumentException("本章正文过短（不足 " + ChapterSummarySupport.MIN_BODY
                    + " 字），先写完再生成摘要");
        }
        checkPoints(userId);
        checkContent(userId, body);
        String ledgerText = buildLedgerContext(userId, workId);
        String userPrompt = "本集：" + displayTitle + "\n\n"
                + "【已有创作台账】\n"
                + (ledgerText == null ? "（暂无，请按本集正文新建七类台账）" : ledgerText)
                + "【本集正文】\n" + ChapterSummarySupport.clipBody(body, ChapterSummarySupport.BODY_CLIP);
        AiChannel channel = resolveChannel(model);
        AiCallResult r = (channel == null)
                ? aiServiceClient.chatText(buildSummarySystemPrompt(), userPrompt)
                : aiServiceClient.chatText(buildSummarySystemPrompt(), userPrompt,
                        channel.getBaseUrl(), channel.getApiKey(), model);
        JsonNode root = ChapterSummarySupport.parseJson(r.getContent());
        String summary = ChapterSummarySupport.summaryOf(root);
        if (summary.isBlank())
        {
            throw new AiServiceUnavailableException("AI 未返回可用的本章摘要，请重试");
        }
        jdbcTemplate.update(
                "UPDATE sys_work_chapter SET summary = ?, update_time = NOW() WHERE chapter_id = ? AND work_id = ?",
                summary, chapterId, workId);
        SysWorkLedger ledger = workLedgerService.getLedger(workId);
        boolean ledgerUpdated = ChapterSummarySupport.mergeLedger(ledger,
                ChapterSummarySupport.ledgerPatch(root));
        if (ledgerUpdated)
        {
            workLedgerService.saveLedger(workId, ledger);
        }
        Long recordId = saveRecord(userId, workId, CAPABILITY_CHAPTER_SUMMARY,
                displayTitle, summary, resolveModel(r), r.getTotalTokens());
        consumePoints(userId, CAPABILITY_CHAPTER_SUMMARY, recordId, r.getTotalTokens());
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("summary", summary);
        result.put("ledgerUpdated", ledgerUpdated);
        result.put("recordId", recordId);
        result.put("tokens", Math.max(1, r.getTotalTokens()));
        return result;
    }

    /** 卷级快照 / 每集验收的能力标识（积分流水 remark 用） */
    private static final String CAPABILITY_CHAPTER_REVIEW = "chapter-review";

    private static final String CAPABILITY_VOLUME_SNAPSHOT = "volume-snapshot";

    /** 一卷的集数（50 集量级固定 10 集一卷） */
    private static final int EPISODES_PER_VOLUME = 10;

    @Override
    public Map<String, Object> reviewChapter(Long workId, Long chapterId, String content, String model)
    {
        Long userId = identityProvider.currentUserId();
        if (workId == null || chapterId == null)
        {
            throw new IllegalArgumentException("缺少作品或章节");
        }
        List<Map<String, Object>> rows;
        try
        {
            rows = jdbcTemplate.queryForList(
                    "SELECT w.author_id, c.chapter_no, c.chapter_title, c.content "
                            + "FROM sys_work w JOIN sys_work_chapter c ON c.work_id = w.work_id "
                            + "WHERE w.work_id = ? AND c.chapter_id = ? AND w.is_deleted = 0",
                    workId, chapterId);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("章节不存在或不属于该作品");
        }
        if (rows.isEmpty())
        {
            throw new IllegalArgumentException("章节不存在或不属于该作品");
        }
        Map<String, Object> row = rows.get(0);
        Object authorObj = row.get("author_id");
        Long authorId = authorObj instanceof Number number ? number.longValue() : null;
        if (authorId == null || !authorId.equals(userId))
        {
            throw new IllegalArgumentException("无权操作该作品");
        }
        String chapterNo = String.valueOf(row.get("chapter_no"));
        String chapterTitle = row.get("chapter_title") == null ? BLANK : String.valueOf(row.get("chapter_title"));
        String displayTitle = chapterTitle.isBlank() ? ("第" + chapterNo + "集") : chapterTitle;
        String body = (content == null || content.isBlank())
                ? (row.get("content") == null ? BLANK : String.valueOf(row.get("content")))
                : content;
        body = body.trim();
        if (body.length() < ChapterSummarySupport.MIN_BODY)
        {
            throw new IllegalArgumentException("本章正文过短（不足 " + ChapterSummarySupport.MIN_BODY + " 字），无需验收");
        }
        checkPoints(userId);
        checkContent(userId, body);
        // 上一集摘要：作为「因果衔接」的判定依据（本集与上集钩子是否接上）。
        String prevSummary = BLANK;
        try
        {
            List<Map<String, Object>> prev = jdbcTemplate.queryForList(
                    "SELECT summary FROM sys_work_chapter WHERE work_id = ? AND chapter_no = ? AND summary IS NOT NULL",
                    workId, (Integer.parseInt(chapterNo) - 1));
            if (!prev.isEmpty() && prev.get(0).get("summary") != null)
            {
                prevSummary = String.valueOf(prev.get(0).get("summary"));
            }
        }
        catch (Exception e)
        {
            log.debug("读取上一集摘要失败 workId={} chapterNo={}：{}", workId, chapterNo, e.getMessage());
        }
        String ledgerText = buildLedgerContext(userId, workId);
        String settingText = buildSettingContext(userId, workId);
        AiChannel channel = resolveChannel(model);
        String system = ChapterReviewSupport.reviewSystemPrompt();
        String user = ChapterReviewSupport.reviewUserPrompt(displayTitle, prevSummary, settingText, ledgerText,
                ChapterSummarySupport.clipBody(body, ChapterSummarySupport.BODY_CLIP));
        AiCallResult r = (channel == null)
                ? aiServiceClient.chatText(system, user)
                : aiServiceClient.chatText(system, user, channel.getBaseUrl(), channel.getApiKey(), model);
        JsonNode root = ChapterReviewSupport.parse(r.getContent());
        int score = ChapterReviewSupport.passScore(root);
        boolean pass = ChapterReviewSupport.pass(root);
        String issues = ChapterReviewSupport.issuesText(root);
        try
        {
            jdbcTemplate.update(
                    "INSERT INTO sys_chapter_review (chapter_id, work_id, episode_no, pass_flag, score, issues) "
                            + "VALUES (?, ?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE work_id = VALUES(work_id), episode_no = VALUES(episode_no), "
                            + "pass_flag = VALUES(pass_flag), score = VALUES(score), issues = VALUES(issues), update_time = NOW()",
                    chapterId, workId, parseIntSafe(chapterNo), pass ? 1 : 0, score, issues);
        }
        catch (Exception e)
        {
            log.warn("写入验收结果失败 workId={} chapterId={}：{}", workId, chapterId, e.getMessage());
        }
        Long recordId = saveRecord(userId, workId, CAPABILITY_CHAPTER_REVIEW, system + "\n\n" + user,
                issues.isBlank() ? "通过 " + score + "/5" : issues, resolveModel(r), r.getTotalTokens());
        consumePoints(userId, CAPABILITY_CHAPTER_REVIEW, recordId, r.getTotalTokens());
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("pass", pass);
        result.put("score", score);
        result.put("issues", issues);
        result.put("recordId", recordId);
        result.put("tokens", Math.max(1, r.getTotalTokens()));
        return result;
    }

    @Override
    public Map<String, Object> buildVolumeSnapshot(Long workId, Integer volumeNo, String model)
    {
        Long userId = identityProvider.currentUserId();
        if (workId == null)
        {
            throw new IllegalArgumentException("缺少作品标识");
        }
        int volume = (volumeNo == null || volumeNo <= 0) ? 1 : volumeNo;
        assertWorkOwner(userId, workId);
        String ledgerText = buildLedgerContext(userId, workId);
        checkPoints(userId);
        int start = (volume - 1) * EPISODES_PER_VOLUME + 1;
        int end = volume * EPISODES_PER_VOLUME;
        List<Map<String, Object>> chapters;
        try
        {
            chapters = jdbcTemplate.queryForList(
                    "SELECT chapter_no, chapter_title, summary FROM sys_work_chapter "
                            + "WHERE work_id = ? AND chapter_no BETWEEN ? AND ? ORDER BY chapter_no",
                    workId, start, end);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("读取本卷章节失败");
        }
        StringBuilder summaries = new StringBuilder();
        for (Map<String, Object> chapter : chapters)
        {
            Object summaryObj = chapter.get("summary");
            if (summaryObj == null || String.valueOf(summaryObj).isBlank())
            {
                continue;
            }
            summaries.append("第").append(chapter.get("chapter_no")).append("集：")
                    .append(String.valueOf(summaryObj).trim()).append('\n');
        }
        if (summaries.length() == 0)
        {
            throw new IllegalArgumentException("本卷还没有可用的章节摘要，先逐集生成摘要再建卷快照");
        }
        String system = ChapterReviewSupport.snapshotSystemPrompt(volume, start, end);
        String user = ChapterReviewSupport.snapshotUserPrompt(ledgerText, summaries.toString());
        checkContent(userId, summaries.toString());
        AiChannel channel = resolveChannel(model);
        AiCallResult r = (channel == null)
                ? aiServiceClient.chatText(system, user)
                : aiServiceClient.chatText(system, user, channel.getBaseUrl(), channel.getApiKey(), model);
        String snapshot = ChapterReviewSupport.snapshotOf(ChapterReviewSupport.parse(r.getContent()));
        if (snapshot.isBlank())
        {
            throw new AiServiceUnavailableException("AI 未返回可用的卷快照，请重试");
        }
        try
        {
            jdbcTemplate.update(
                    "INSERT INTO sys_work_volume (work_id, volume_no, episode_start, episode_end, snapshot) "
                            + "VALUES (?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE episode_start = VALUES(episode_start), "
                            + "episode_end = VALUES(episode_end), snapshot = VALUES(snapshot), update_time = NOW()",
                    workId, volume, start, end, snapshot);
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("保存卷快照失败：" + e.getMessage());
        }
        Long recordId = saveRecord(userId, workId, CAPABILITY_VOLUME_SNAPSHOT, system + "\n\n" + user,
                snapshot, resolveModel(r), r.getTotalTokens());
        consumePoints(userId, CAPABILITY_VOLUME_SNAPSHOT, recordId, r.getTotalTokens());
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("volumeNo", volume);
        result.put("episodeStart", start);
        result.put("episodeEnd", end);
        result.put("snapshot", snapshot);
        result.put("recordId", recordId);
        result.put("tokens", Math.max(1, r.getTotalTokens()));
        return result;
    }

    @Override
    public List<Map<String, Object>> listVolumes(Long workId)
    {
        Long userId = identityProvider.currentUserId();
        if (workId == null)
        {
            return java.util.Collections.emptyList();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT v.volume_no, v.episode_start, v.episode_end, v.snapshot, v.update_time "
                        + "FROM sys_work_volume v JOIN sys_work w ON w.work_id = v.work_id "
                        + "WHERE v.work_id = ? AND w.author_id = ? AND w.is_deleted = 0 "
                        + "ORDER BY v.volume_no",
                workId, userId);
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("volumeNo", row.get("volume_no"));
            item.put("episodeStart", row.get("episode_start"));
            item.put("episodeEnd", row.get("episode_end"));
            item.put("snapshot", row.get("snapshot"));
            item.put("updateTime", row.get("update_time"));
            list.add(item);
        }
        return list;
    }

    @Override
    public boolean saveVolume(Long workId, Integer volumeNo, String snapshot)
    {
        if (workId == null || volumeNo == null || volumeNo <= 0)
        {
            throw new IllegalArgumentException("缺少作品或卷号");
        }
        Long userId = identityProvider.currentUserId();
        assertWorkOwner(userId, workId);
        int start = (volumeNo - 1) * EPISODES_PER_VOLUME + 1;
        int end = volumeNo * EPISODES_PER_VOLUME;
        String text = snapshot == null ? BLANK : snapshot.trim();
        if (text.length() > ChapterReviewSupport.SNAPSHOT_MAX)
        {
            text = text.substring(0, ChapterReviewSupport.SNAPSHOT_MAX);
        }
        jdbcTemplate.update(
                "INSERT INTO sys_work_volume (work_id, volume_no, episode_start, episode_end, snapshot) "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE episode_start = VALUES(episode_start), "
                        + "episode_end = VALUES(episode_end), snapshot = VALUES(snapshot), update_time = NOW()",
                workId, volumeNo, start, end, text);
        return true;
    }

    /** 设定口径（写正文/验收判定用）：只取故事简介、人物卡、关系状态三类。 */
    private String buildSettingContext(Long userId, Long workId)
    {
        SysWorkLedger ledger = workLedgerService.getLedger(workId);
        if (ledger == null)
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        appendLedgerLine(sb, "故事简介", ledger.getStorySummary());
        appendLedgerLine(sb, "人物卡", ledger.getCharacters());
        appendLedgerLine(sb, "关系状态", ledger.getRelations());
        appendLedgerLine(sb, "重要道具档案（件数、持有人、是否已交付以它为准）", truncate(ledger.getProps(), 2000));
        appendLedgerLine(sb, "写法基线（验收文风是否漂移的依据）", truncate(ledger.getStyleGuide(), 800));
        return sb.length() == 0 ? null : sb.toString();
    }

    private static int parseIntSafe(String value)
    {
        try
        {
            return Integer.parseInt(value.trim());
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    /** 摘要/台账抽取的系统提示（固定输出 JSON，禁止改写正文）。 */
    private static String buildSummarySystemPrompt()
    {
        return "你是剧本统筹，负责把一集正文整理成「本章摘要」和「创作台账更新」，供下一集续写参考。\n"
                + "要求：\n"
                + "1. 只依据给出的正文与既有台账，不编造未出现的人物、设定或情节。\n"
                + "2. summary：本集摘要，120~200 字，写清本集发生的事件、冲突推进、人物状态变化与结尾钩子；不写评价或推荐语。\n"
                + "3. ledger：只写本集新增或需要修改的条目，不要复述既有台账原文；"
                + "但 props 例外——每次都要输出完整道具档案。"
                + "每类给出若干行短句，一行一条（全书主线、人物身份状态、关系变化、伏笔、"
                + "本集的时间地点、关键道具去向、重要道具档案、写法基线、新出现的悬念）。本集没有涉及的类别填空字符串 \"\"。\n"
                + "    台账是全书唯一事实源：正文与台账冲突时以台账为准，不得顺着正文改数字。\n"
                + "    九个键的含义：\n"
                + "    storySummary 故事简介：主线推进到哪一步；\n"
                + "    characters 人物卡：出场人物的身份、性格、当前状态；\n"
                + "    relations 关系状态：人物关系及其变化；\n"
                + "    foreshadow 伏笔：埋在哪一集、计划或是否回收；\n"
                + "    timeline 时间线：按集记录时间与地点；\n"
                + "    items 物品位置：本集关键道具在谁手里、在哪（逐集流水）；件数不写在这里，也不得改动件数，件数以 props 为准；\n"
                + "    props 重要道具档案：一行一件，格式「名称｜件数｜当前持有人｜状态（在手/已交付/已损毁/下落不明）｜外观或特殊能力」；"
                + "每次输出完整档案（含本集没变化的道具），同名道具只保留一行；件数只能沿用旧值，"
                + "正文没有明确写增减时不得改数字；已交付过的道具状态保持「已交付给某某」，"
                + "不得无过渡地退回「在手」，除非正文明确写被退回；\n"
                + "    styleGuide 写法基线：叙述人称、每集字数区间、对白与叙述比例、标点与段落规则、禁用写法（括注体/心理独白/场景头/镜头术语等）；"
                + "首次生成时从正文提取并固定，之后只在正文确实改了写法时才更新，与基线一致就填空串；\n"
                + "    threads 未解线索：尚未解释的悬念。\n"
                + "4. summary 必须放在 JSON 最前面、写完整；严格输出一个 JSON 对象，结构如下（值替换为实际内容）：\n"
                + ChapterSummarySupport.emptyLedgerJson() + "\n"
                + "不要 Markdown 代码块，不要任何解释文字。";
    }

    private String resolveSkillContent(Long skillId)
    {
        return aiSkillService.resolveSkillContent(skillId);
    }

    /**
     * 按用户选用的模型名解析调用渠道。
     *
     * model 非空 → 在启用渠道中按模型名匹配；model 为空 → 默认渠道（sort 最小）。
     * 渠道存在但 apiKey 为空时视为未配置，返回 null（调用方回退默认环境配置），
     * 避免用空密钥请求导致 401。
     */
    private AiChannel resolveChannel(String model)
    {
        AiChannel channel = (model == null || model.isBlank())
                ? aiChannelService.resolveDefaultChannel()
                : aiChannelService.resolveChannelByModel(model);
        if (channel == null)
        {
            return null;
        }
        String key = channel.getApiKey();
        return (key == null || key.isBlank()) ? null : channel;
    }

    /** 推理模型：思维链与正文共用 max_tokens，长 skill 包场景会把额度吃满导致正文为空。 */
    private static final java.util.Set<String> REASONING_MODELS = java.util.Set.of(
            "deepseek-flash", "deepseek-reasoner", "deepseek-r1");

    /** 推理模型场景优先换用的非推理模型 */
    private static final String FALLBACK_NON_REASONING_MODEL = "deepseek-chat";

    private static boolean isReasoningModel(String model)
    {
        if (model == null || model.isBlank())
        {
            return false;
        }
        String m = model.trim().toLowerCase();
        return REASONING_MODELS.contains(m) || m.contains("reasoner") || m.contains("thinking")
                || m.endsWith("-r1") || m.contains(":r1");
    }

    /**
     * 分镜模型归一：分镜会带整包 skill（prompt 上万 token），推理模型的 reasoning 会吃满 max_tokens，
     * 返回 content 为空（finish_reason=length）。用户选了推理模型或没选模型时，换成渠道内的非推理模型；
     * 提高 max_tokens 无效（reasoning 同步增长），所以只改模型不改额度。
     */
    private String resolveStoryboardModel(String requested, AiChannel channel)
    {
        String model = requested == null ? "" : requested.trim();
        if (!model.isEmpty() && !isReasoningModel(model))
        {
            return model;
        }
        String picked = pickNonReasoningModel(channel == null ? null : channel.getModels());
        if (picked != null)
        {
            log.warn("分镜场景自动切非推理模型：{} -> {}", model.isEmpty() ? "(未指定)" : model, picked);
            return picked;
        }
        if (!model.isEmpty())
        {
            log.warn("分镜场景自动切非推理模型：{} -> {}", model, FALLBACK_NON_REASONING_MODEL);
            return FALLBACK_NON_REASONING_MODEL;
        }
        return null;
    }

    /** 在渠道模型列表里挑非推理模型：优先 deepseek-chat，否则第一个非推理模型；都没有返回 null。 */
    private static String pickNonReasoningModel(String modelsCsv)
    {
        if (modelsCsv == null || modelsCsv.isBlank())
        {
            return null;
        }
        List<String> models = java.util.Arrays.stream(modelsCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        for (String m : models)
        {
            if (FALLBACK_NON_REASONING_MODEL.equalsIgnoreCase(m))
            {
                return m;
            }
        }
        for (String m : models)
        {
            if (!isReasoningModel(m))
            {
                return m;
            }
        }
        return null;
    }

    // ================================================================
    // AI 分镜（E 模块）：剧本 → 分镜 一条龙，产出可直接出售的分镜剧本
    // ================================================================

    @Override
    public AppAiStoryboardResult storyboard(AppAiStoryboardRequest request)
    {
        checkPoints(identityProvider.currentUserId());
        checkContent(identityProvider.currentUserId(),
                (request.getScript() == null ? "" : request.getScript()));
        Date startedAt = new Date();
        AiChannel channel = resolveChannel(request.getModel());
        String model = resolveStoryboardModel(request.getModel(), channel);
        AiCallResult r = aiServiceClient.storyboard(request.getScript(), request.getStyle(),
                resolveSkillContent(request.getSkillId()),
                channel == null ? null : channel.getBaseUrl(),
                channel == null ? null : channel.getApiKey(),
                model);
        Date finishedAt = new Date();
        String content = r.getContent();
        String requestNo = generateRequestNo();
        aiRequestMapper.insertAiRequest(requestNo, identityProvider.currentUserId(), request.getWorkId(),
                CAPABILITY_STORYBOARD, Math.max(1, r.getTotalTokens()), STATUS_SUCCESS, resolveProvider(),
                sha256(buildStoryboardPrompt(request)), startedAt, finishedAt);
        Long recordId = saveRecord(identityProvider.currentUserId(), request.getWorkId(),
                CAPABILITY_STORYBOARD, buildStoryboardInputText(request), content,
                resolveModel(r), r.getTotalTokens());
        consumePoints(identityProvider.currentUserId(), "storyboard", recordId, r.getTotalTokens());
        return new AppAiStoryboardResult(content, requestNo, Math.max(1, r.getTotalTokens()), false);
    }

    @Override
    public AppAiStoryboardResult storyboardStream(AppAiStoryboardRequest request,
            java.util.function.Consumer<String> onDelta)
    {
        checkPoints(identityProvider.currentUserId());
        checkContent(identityProvider.currentUserId(),
                (request.getScript() == null ? "" : request.getScript()));
        Date startedAt = new Date();
        AiChannel channel = resolveChannel(request.getModel());
        String model = resolveStoryboardModel(request.getModel(), channel);
        AiCallResult r = aiServiceClient.storyboardStream(request.getScript(), request.getStyle(),
                resolveSkillContent(request.getSkillId()), onDelta,
                channel == null ? null : channel.getBaseUrl(),
                channel == null ? null : channel.getApiKey(),
                model);
        Date finishedAt = new Date();
        String content = r.getContent();
        String requestNo = generateRequestNo();
        aiRequestMapper.insertAiRequest(requestNo, identityProvider.currentUserId(), request.getWorkId(),
                CAPABILITY_STORYBOARD, Math.max(1, r.getTotalTokens()), STATUS_SUCCESS, resolveProvider(),
                sha256(buildStoryboardPrompt(request)), startedAt, finishedAt);
        Long recordId = saveRecord(identityProvider.currentUserId(), request.getWorkId(),
                CAPABILITY_STORYBOARD, buildStoryboardInputText(request), content,
                resolveModel(r), r.getTotalTokens());
        consumePoints(identityProvider.currentUserId(), "storyboard", recordId, r.getTotalTokens());
        return new AppAiStoryboardResult(content, requestNo, Math.max(1, r.getTotalTokens()), false);
    }

    /** 分镜提示词摘要原文（用于 prompt_hash，不含敏感信息） */
    private static String buildStoryboardPrompt(AppAiStoryboardRequest request)
    {
        return "scriptLen=" + (request.getScript() == null ? 0 : request.getScript().length())
                + ";style=" + safe(request.getStyle())
                + ";skillId=" + (request.getSkillId() == null ? "" : request.getSkillId());
    }

    /** 分镜输入的展示文本（落 sys_ai_write_record.input_prompt，用户可见） */
    private static String buildStoryboardInputText(AppAiStoryboardRequest request)
    {
        String styleLine = safe(request.getStyle()).isEmpty() ? "" : "\n风格：" + request.getStyle();
        return "剧本文本：" + safe(request.getScript()) + styleLine;
    }
}
