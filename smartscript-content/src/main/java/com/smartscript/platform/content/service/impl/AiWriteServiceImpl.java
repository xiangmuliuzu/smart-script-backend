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
import com.smartscript.platform.content.dto.AppAiWriteRecordInsert;
import com.smartscript.platform.content.dto.AppAiWriteRecordItem;
import com.smartscript.platform.content.dto.AppAiWriteRequest;
import com.smartscript.platform.content.dto.AppAiWriteResult;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.mapper.AppAiRequestMapper;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.exception.AiQuotaInsufficientException;
import com.smartscript.platform.content.service.AiServiceClient;
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
 *    pov / language_style / pace 填空串，title 取输入前 50 字，tokens_used=0，
 *    ai_model 取 AI 返回（缺省取配置），status='success'；write_type：写作取入参 type（缺省 write），
 *    润色固定 'polish'；input_prompt：写作取 prompt，润色取待润色原文；output_content 取 AI 返回。
 * 3. 大纲落 sys_ai_request：capability='outline'、quota_cost=1、provider 取配置、
 *    request_no 由本层唯一生成；status='success'，started_at/finished_at 显式写入。
 * 4. AI 调用成功即扣减 1 次：写 sys_ai_quota_account 扣减（乐观锁）与
 *    sys_ai_quota_record 流水（change_type='consume'，幂等键 ai-consume-{capability}-{businessId}）；
 *    校验与扣减均在本服务内用 JdbcTemplate 直写，不跨模块依赖 review 的 AiQuotaService。
 * 5. 身份不做空值兜底：四接口均未登记 App 凭证域白名单，过滤器已保证非游客，currentUserId() 不会为 null。
 *
 * @author xiangsipeng
 */
@Service
public class AiWriteServiceImpl implements IAiWriteService
{
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

    /** 大纲消耗额度（本批固定 1） */
    private static final int QUOTA_COST_OUTLINE = 1;

    /** 标题长度上限（sys_ai_write_record.title 为 varchar(100)，按业务约定收紧到 50） */
    private static final int TITLE_MAX_LENGTH = 50;

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
    private IdentityProvider identityProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private com.smartscript.platform.review.service.RiskCheckService riskCheckService;

    /** 内容风控（测试无 Bean 时跳过，生产必注入） */
    private void checkContent(Long userId, String text)
    {
        if (riskCheckService != null && text != null && !text.isBlank())
        {
            checkContent(userId, text);
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
        checkQuota(identityProvider.currentUserId());
        JsonNode data = aiServiceClient.write(request.getPrompt(), request.getType());
        String content = text(data, "content");
        Long recordId = saveRecord(identityProvider.currentUserId(), request.getWorkId(),
                resolveWriteType(request.getType()), request.getPrompt(), content, resolveModel(data));
        consumeQuota(identityProvider.currentUserId(), "write", recordId);
        return new AppAiWriteResult(content, recordId);
    }

    @Override
    public AppAiWriteResult polish(AppAiPolishRequest request)
    {
        checkQuota(identityProvider.currentUserId());
        checkContent(identityProvider.currentUserId(), request.getContent());
        JsonNode data = aiServiceClient.polish(request.getContent(), request.getStyle());
        String content = text(data, "content");
        Long recordId = saveRecord(identityProvider.currentUserId(), null,
                WRITE_TYPE_POLISH, request.getContent(), content, resolveModel(data));
        consumeQuota(identityProvider.currentUserId(), "polish", recordId);
        return new AppAiWriteResult(content, recordId);
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
        checkQuota(identityProvider.currentUserId());
        String content = aiServiceClient.writeStream(request.getPrompt(), request.getType(), onDelta);
        Long recordId = saveRecord(identityProvider.currentUserId(), request.getWorkId(),
                resolveWriteType(request.getType()), request.getPrompt(), content, aiModel);
        consumeQuota(identityProvider.currentUserId(), "write", recordId);
        return new AppAiWriteResult(content, recordId);
    }

    @Override
    public AppAiWriteResult polishStream(AppAiPolishRequest request, java.util.function.Consumer<String> onDelta)
    {
        checkQuota(identityProvider.currentUserId());
        checkContent(identityProvider.currentUserId(), request.getContent());
        String content = aiServiceClient.polishStream(request.getContent(), request.getStyle(), onDelta);
        Long recordId = saveRecord(identityProvider.currentUserId(), null,
                WRITE_TYPE_POLISH, request.getContent(), content, aiModel);
        consumeQuota(identityProvider.currentUserId(), "polish", recordId);
        return new AppAiWriteResult(content, recordId);
    }

    @Override
    public AppAiOutlineResult outlineStream(AppAiOutlineRequest request, java.util.function.Consumer<String> onDelta)
    {
        checkQuota(identityProvider.currentUserId());
        checkContent(identityProvider.currentUserId(),
                (request.getInspiration() == null ? "" : request.getInspiration()));
        Date startedAt = new Date();
        String raw = aiServiceClient.outlineStream(request.getInspiration(), request.getGenre(), request.getStyle(), onDelta);
        Date finishedAt = new Date();
        JsonNode outlineNode = parseOutlineJson(raw);
        Object outline = outlineNode.isMissingNode() || outlineNode.isNull()
                ? null : OUTLINE_MAPPER.convertValue(outlineNode, Object.class);
        String requestNo = generateRequestNo();
        aiRequestMapper.insertAiRequest(requestNo, identityProvider.currentUserId(), request.getWorkId(),
                CAPABILITY_OUTLINE, QUOTA_COST_OUTLINE, STATUS_SUCCESS, resolveProvider(),
                sha256(buildOutlinePrompt(request)), startedAt, finishedAt);
        Long recordId = saveRecord(identityProvider.currentUserId(), request.getWorkId(),
                CAPABILITY_OUTLINE, buildOutlineInputText(request), outlineToText(outline), resolveModel(outlineNode));
        consumeQuota(identityProvider.currentUserId(), "outline", recordId);
        return new AppAiOutlineResult(outline, requestNo, QUOTA_COST_OUTLINE);
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
        checkQuota(identityProvider.currentUserId());
        checkContent(identityProvider.currentUserId(),
                (request.getInspiration() == null ? "" : request.getInspiration()));
        Date startedAt = new Date();
        JsonNode data = aiServiceClient.outline(request.getInspiration(), request.getGenre(), request.getStyle());
        Date finishedAt = new Date();
        JsonNode outlineNode = data.path("outline");
        Object outline = outlineNode.isMissingNode() || outlineNode.isNull()
                ? null : OUTLINE_MAPPER.convertValue(outlineNode, Object.class);
        String requestNo = generateRequestNo();
        aiRequestMapper.insertAiRequest(requestNo, identityProvider.currentUserId(), request.getWorkId(),
                CAPABILITY_OUTLINE, QUOTA_COST_OUTLINE, STATUS_SUCCESS, resolveProvider(),
                sha256(buildOutlinePrompt(request)), startedAt, finishedAt);
        // 同步写一条可回看记录（write_type='outline'）：输入（灵感/类型/风格）与大纲结果
        // 完整落 sys_ai_write_record，供 AI 记录页按写作/润色同款「输入/输出」样式展示。
        Long recordId = saveRecord(identityProvider.currentUserId(), request.getWorkId(),
                CAPABILITY_OUTLINE, buildOutlineInputText(request), outlineToText(outline), resolveModel(data));
        consumeQuota(identityProvider.currentUserId(), "outline", recordId);
        return new AppAiOutlineResult(outline, requestNo, QUOTA_COST_OUTLINE);
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
     * 校验用户 AI 可用次数：读取 sys_ai_quota_account.available_quota，
     * 无账户或不足 1 次即抛 {@link AiQuotaInsufficientException}，由控制器转 400。
     * 本方法只校验；实际扣减在 AI 调用成功后由 {@link #consumeQuota} 完成。
     */
    private void checkQuota(Long userId)
    {
        Integer available;
        try
        {
            available = jdbcTemplate.queryForObject(
                    "SELECT available_quota FROM sys_ai_quota_account WHERE user_id = ?",
                    Integer.class, userId);
        }
        catch (org.springframework.dao.EmptyResultDataAccessException e)
        {
            available = null;
        }
        int balance = available == null ? 0 : available;
        if (balance < 1)
        {
            throw new AiQuotaInsufficientException("AI次数不足，当前可用: " + balance + " 次");
        }
    }

    /**
     * AI 调用成功后扣减 1 次可用额度并写额度流水（App / E 模块）。
     *
     * 幂等键 = ai-consume-{capability}-{businessId}（businessId 为该次调用的记录ID，
     * 每次调用唯一），重复请求因 uk_idempotency_key 唯一约束被忽略，不重复扣减。
     * 并发兜底：扣减 UPDATE 带 available_quota >= 1 条件，行数为 0 时不写流水直接返回。
     */
    private void consumeQuota(Long userId, String capability, Long businessId)
    {
        Integer before;
        try
        {
            before = jdbcTemplate.queryForObject(
                    "SELECT available_quota FROM sys_ai_quota_account WHERE user_id = ?",
                    Integer.class, userId);
        }
        catch (org.springframework.dao.EmptyResultDataAccessException e)
        {
            before = null;
        }
        if (before == null || before < 1)
        {
            return; // checkQuota 已前置拦截，此处兜底
        }
        int rows = jdbcTemplate.update(
                "UPDATE sys_ai_quota_account SET available_quota = available_quota - 1, "
                        + "total_consumed = total_consumed + 1, version = version + 1 "
                        + "WHERE user_id = ? AND available_quota >= 1",
                userId);
        if (rows == 0)
        {
            return; // 并发兜底：余额已被扣光，不落流水
        }
        String idemKey = "ai-consume-" + capability + "-" + businessId;
        try
        {
            jdbcTemplate.update(
                    "INSERT INTO sys_ai_quota_record (record_no, user_id, change_type, change_amount, "
                            + "before_balance, after_balance, business_type, business_id, "
                            + "idempotency_key, remark, created_at) "
                            + "VALUES (?, ?, 'consume', 1, ?, ?, ?, ?, ?, ?, NOW())",
                    generateRecordNo("QR"), userId, before, before - 1, capability,
                    businessId, idemKey, "AI调用扣减: " + capability);
        }
        catch (org.springframework.dao.DuplicateKeyException e)
        {
            // 幂等：同一次调用不重复扣减
        }
    }

    /**
     * 落一条 AI 写作记录并回填记录ID。
     */
    private Long saveRecord(Long userId, Long workId, String writeType, String inputPrompt, String outputContent, String model)
    {
        AppAiWriteRecordInsert row = new AppAiWriteRecordInsert();
        row.setUserId(userId);
        row.setWorkId(workId);
        row.setTitle(truncate(inputPrompt, TITLE_MAX_LENGTH));
        row.setWriteType(writeType);
        row.setPov(BLANK);
        row.setLanguageStyle(BLANK);
        row.setPace(BLANK);
        row.setInputPrompt(inputPrompt == null ? BLANK : inputPrompt);
        row.setOutputContent(outputContent == null ? BLANK : outputContent);
        row.setWordCount(outputContent == null ? 0 : outputContent.length());
        row.setAiModel(model);
        row.setTokensUsed(0);
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
    private String resolveModel(JsonNode data)
    {
        String fromAi = text(data, "model");
        if (fromAi != null && !fromAi.isBlank())
        {
            return fromAi;
        }
        return aiModel == null || aiModel.isBlank() ? DEFAULT_MODEL : aiModel;
    }

    /** 服务提供方：配置为空时兜底 */
    private String resolveProvider()
    {
        return aiProvider == null || aiProvider.isBlank() ? DEFAULT_MODEL : aiProvider;
    }

    /** 取 JSON 文本字段；缺失返回 null */
    private static String text(JsonNode node, String field)
    {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
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
}
