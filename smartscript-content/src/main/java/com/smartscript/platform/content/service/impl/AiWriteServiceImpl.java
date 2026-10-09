package com.smartscript.platform.content.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.content.dto.AppAiOutlineRequest;
import com.smartscript.platform.content.dto.AppAiOutlineResult;
import com.smartscript.platform.content.dto.AppAiPolishRequest;
import com.smartscript.platform.content.dto.AppAiWriteRecordInsert;
import com.smartscript.platform.content.dto.AppAiWriteRecordItem;
import com.smartscript.platform.content.dto.AppAiWriteRequest;
import com.smartscript.platform.content.dto.AppAiWriteResult;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.mapper.AppAiRequestMapper;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.service.AiServiceClient;
import com.smartscript.platform.content.service.IAiWriteService;
import com.smartscript.platform.identity.IdentityProvider;

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
 * 4. 本批不接额度扣减：quota_cost 仅作记录，不写额度流水，也不校验额度余额（跨模块，见接口说明）。
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

    @Autowired
    private AppAiWriteRecordMapper writeRecordMapper;

    @Autowired
    private AppAiRequestMapper aiRequestMapper;

    @Autowired
    private AiServiceClient aiServiceClient;

    @Autowired
    private IdentityProvider identityProvider;

    /** AI 模型名（AI 未返回时使用） */
    @Value("${AI_SERVICE_MODEL:smartscript-ai}")
    private String aiModel;

    /** AI 服务提供方（落 sys_ai_request.provider） */
    @Value("${AI_SERVICE_PROVIDER:smartscript-ai}")
    private String aiProvider;

    @Override
    public AppAiWriteResult write(AppAiWriteRequest request)
    {
        JsonNode data = aiServiceClient.write(request.getPrompt(), request.getType());
        String content = text(data, "content");
        Long recordId = saveRecord(identityProvider.currentUserId(), request.getWorkId(),
                resolveWriteType(request.getType()), request.getPrompt(), content, resolveModel(data));
        return new AppAiWriteResult(content, recordId);
    }

    @Override
    public AppAiWriteResult polish(AppAiPolishRequest request)
    {
        JsonNode data = aiServiceClient.polish(request.getContent(), request.getStyle());
        String content = text(data, "content");
        Long recordId = saveRecord(identityProvider.currentUserId(), null,
                WRITE_TYPE_POLISH, request.getContent(), content, resolveModel(data));
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
    public AppAiOutlineResult outline(AppAiOutlineRequest request)
    {
        Date startedAt = new Date();
        JsonNode data = aiServiceClient.outline(request.getInspiration(), request.getGenre(), request.getStyle());
        Date finishedAt = new Date();
        JsonNode outlineNode = data.path("outline");
        Object outline = outlineNode.isMissingNode() || outlineNode.isNull() ? null : outlineNode;
        String requestNo = generateRequestNo();
        aiRequestMapper.insertAiRequest(requestNo, identityProvider.currentUserId(), request.getWorkId(),
                CAPABILITY_OUTLINE, QUOTA_COST_OUTLINE, STATUS_SUCCESS, resolveProvider(),
                sha256(buildOutlinePrompt(request)), startedAt, finishedAt);
        return new AppAiOutlineResult(outline, requestNo, QUOTA_COST_OUTLINE);
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
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return "AIR" + timestamp + random.toUpperCase();
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