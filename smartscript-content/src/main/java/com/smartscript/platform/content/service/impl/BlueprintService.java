package com.smartscript.platform.content.service.impl;

import static com.smartscript.platform.content.service.impl.StructuredWritingJson.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartscript.platform.content.dto.AppWorkCreateRequest;
import com.smartscript.platform.content.exception.AiServiceUnavailableException;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper.BlueprintRecord;
import com.smartscript.platform.content.service.AiCallResult;
import com.smartscript.platform.content.service.IAppWorkLedgerService;
import com.smartscript.platform.content.service.IAppWorkManageService;
import com.smartscript.platform.identity.IdentityProvider;

@Service
public class BlueprintService
{
    static final String TYPE = "blueprint";
    private static final String DRAFT = "draft";
    private static final String CONFIRMED = "confirmed";
    private static final List<String> SETTING = List.of("storyCore", "world", "outline");
    private static final List<String> ROSTER = List.of("characters", "items", "relations");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiWriteServiceImpl ai;
    private final AppAiWriteRecordMapper records;
    private final IAppWorkManageService works;
    private final IAppWorkLedgerService ledgers;
    private final IdentityProvider identity;
    private final TransactionTemplate transaction;
    private final Set<Long> generatingSessions = ConcurrentHashMap.newKeySet();

    public BlueprintService(AiWriteServiceImpl ai, AppAiWriteRecordMapper records, IAppWorkManageService works,
            IAppWorkLedgerService ledgers, IdentityProvider identity, PlatformTransactionManager manager)
    {
        this.ai = ai;
        this.records = records;
        this.works = works;
        this.ledgers = ledgers;
        this.identity = identity;
        this.transaction = new TransactionTemplate(manager);
    }

    public record Request(String title, String inspiration, String genre, Integer episodes, BigDecimal minutes,
            String prompt, Long skillId, String model, Long sessionId) { }

    public record RegenerateRequest(Long sessionId, String section, String key, String instruction,
            String model, Long skillId) { }

    public ObjectNode generate(Request request, Consumer<String> stage, Consumer<String> delta)
    {
        ObjectNode setup = setup(request);
        Long userId = identity.currentUserId();
        ai.checkContent(userId, text(setup.path("title")) + "\n" + text(setup.path("inspiration"))
                + "\n" + text(setup.path("genre")) + "\n" + text(setup.path("prompt")));
        if (request.sessionId() != null)
        {
            return generateForSession(userId, request.sessionId(), existing -> {
                if (CONFIRMED.equals(existing.status()))
                {
                    throw new IllegalArgumentException("该会话已确认，请使用资料重生成接口");
                }
                ai.checkPoints(userId);
                Generated generated = generateBoth(setup.toString(), parse(existing.outputContent()),
                        request.model(), request.skillId(), stage, delta);
                setPlan(generated.blueprint(), setup);
                return saveGenerated(userId, existing, setup, generated);
            });
        }
        ai.checkPoints(userId);
        Generated generated = generateBoth(setup.toString(), object(), request.model(), request.skillId(), stage, delta);
        setPlan(generated.blueprint(), setup);
        return transaction.execute(status -> persist(userId, null, setup, generated));
    }

    public Map<String, Object> get(Long sessionId)
    {
        return response(session(identity.currentUserId(), sessionId, false));
    }

    public Map<String, Object> getForWork(Long workId)
    {
        BlueprintRecord row = records.selectBlueprintByWork(identity.currentUserId(), workId);
        return row == null ? null : response(row);
    }

    private static Map<String, Object> response(BlueprintRecord row)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", row.recordId());
        data.put("status", row.status());
        data.put("title", text(parse(row.inputPrompt()).path("title")));
        data.put("updatedAt", row.updatedAt());
        data.put("blueprint", asMap(parse(row.outputContent())));
        if (row.workId() != null)
        {
            data.put("workId", row.workId());
        }
        return data;
    }

    public ObjectNode regenerate(RegenerateRequest request, Consumer<String> stage, Consumer<String> delta)
    {
        if (request == null || request.section() == null || (!"all".equals(request.section())
                && !SETTING.contains(request.section()) && !ROSTER.contains(request.section())))
        {
            throw new IllegalArgumentException("资料类型无效");
        }
        String section = request.section();
        String key = optional(request.key());
        if (key != null && !List.of("characters", "items").contains(section))
        {
            throw new IllegalArgumentException("只有人物和物品支持按 key 重生成");
        }
        String instruction = optional(request.instruction());
        validateLength(instruction, 10000, "重生成说明");
        Long userId = identity.currentUserId();
        ai.checkContent(userId, instruction);
        return generateForSession(userId, request.sessionId(), row -> {
            ObjectNode setup = parse(row.inputPrompt());
            ObjectNode current = parse(row.outputContent());
            ObjectNode context = object().set("setup", setup);
            context.set("current", current);
            context.put("instruction", instruction);
            if (key != null)
            {
                context.set("target", findEntry(current.path(section), key));
            }
            String model = request.model() == null ? optional(text(setup.path("model"))) : request.model();
            Long skillId = request.skillId();
            if (skillId == null && setup.path("skillId").isIntegralNumber())
            {
                skillId = setup.path("skillId").longValue();
            }
            ai.checkPoints(userId);
            Generated generated;
            if ("all".equals(section))
            {
                generated = generateBoth(context.toString(), current, model, skillId, stage, delta);
                setPlan(generated.blueprint(), setup);
            }
            else
            {
                stage.accept(stageName(section));
                AiCallResult result = ai.structuredStream(prompt(List.of(section)) + (key == null ? ""
                        : "\n只重写 target 对应的条目，返回一个单元素数组，保留其 key。"),
                        context.toString(), model, skillId, delta);
                JsonNode value = modelObject(result).path(section);
                BlueprintStructure.merge(current, section, key, value);
                generated = new Generated(current, result.getTotalTokens(), ai.resolveModel(result), result.isTruncated());
            }
            return saveGenerated(userId, row, setup, generated);
        });
    }

    private ObjectNode generateForSession(Long userId, Long sessionId, Function<BlueprintRecord, ObjectNode> generate)
    {
        BlueprintRecord row = session(userId, sessionId, false);
        if (!generatingSessions.add(sessionId))
        {
            throw new ConcurrentModificationException("该作品资料正在生成，请稍后再试");
        }
        try
        {
            return generate.apply(row);
        }
        finally
        {
            generatingSessions.remove(sessionId);
        }
    }

    private ObjectNode saveGenerated(Long userId, BlueprintRecord before, ObjectNode setup, Generated generated)
    {
        return transaction.execute(status -> {
            BlueprintRecord current = session(userId, before.recordId(), true);
            // 跨实例生成或确认改变了快照时，拒绝过期结果并且不扣费。
            if (!before.equals(current))
            {
                throw new ConcurrentModificationException("作品资料已更新，请刷新后重试");
            }
            if (CONFIRMED.equals(current.status())
                    && !ledgers.saveLedger(current.workId(), BlueprintStructure.ledger(generated.blueprint())))
            {
                throw new IllegalStateException("同步作品台账失败");
            }
            return persist(userId, current, setup, generated);
        });
    }

    public Long confirm(Long sessionId)
    {
        Long userId = identity.currentUserId();
        return transaction.execute(status -> {
            BlueprintRecord row = session(userId, sessionId, true);
            if (CONFIRMED.equals(row.status()))
            {
                return row.workId();
            }
            ObjectNode setup = parse(row.inputPrompt());
            ObjectNode blueprint = parse(row.outputContent());
            AppWorkCreateRequest request = new AppWorkCreateRequest();
            request.setTitle(text(setup.path("title")));
            request.setDescription(BlueprintStructure.summary(blueprint));
            int duration = setup.path("minutes").decimalValue().multiply(BigDecimal.valueOf(60)).intValueExact();
            request.setDuration(duration);
            Long workId = works.createWork(request);
            if (workId == null || !ledgers.saveLedger(workId, BlueprintStructure.ledger(blueprint)))
            {
                throw new IllegalStateException("创建 Blueprint 作品失败");
            }
            blueprint.put("status", CONFIRMED).put("updatedAt", now());
            if (records.confirmBlueprint(userId, sessionId, workId, blueprint.toString()) != 1)
            {
                throw new IllegalStateException("确认 Blueprint 会话失败");
            }
            return workId;
        });
    }

    private Generated generateBoth(String input, ObjectNode previous, String model, Long skillId, Consumer<String> stage,
            Consumer<String> delta)
    {
        stage.accept("story");
        AiCallResult first = ai.structuredStream(prompt(SETTING), input, model, skillId, delta);
        ObjectNode result = BlueprintStructure.normalize(modelObject(first), SETTING);
        stage.accept("world");
        stage.accept("characters");
        AiCallResult second = ai.structuredStream(prompt(ROSTER), input + "\n已生成的故事、世界和大纲：\n" + result,
                model, skillId, delta);
        BlueprintStructure.mergeRoster(result, modelObject(second), previous);
        stage.accept("items");
        stage.accept("outline");
        return new Generated(result, Math.addExact(first.getTotalTokens(), second.getTotalTokens()),
                ai.resolveModel(second), first.isTruncated() || second.isTruncated());
    }

    static ObjectNode modelObject(AiCallResult result)
    {
        try
        {
            return parse(result.getContent());
        }
        catch (AiServiceUnavailableException e)
        {
            if (result.isTruncated())
            {
                throw new AiServiceUnavailableException("AI 输出被截断，请精简要求后重试");
            }
            throw e;
        }
    }

    private ObjectNode persist(Long userId, BlueprintRecord row, ObjectNode setup, Generated generated)
    {
        String status = row == null ? DRAFT : row.status();
        ObjectNode blueprint = generated.blueprint().put("status", status).put("updatedAt", now());
        String title = "[起稿]《" + limit(text(setup.path("title")), 94) + "》";
        Long id;
        if (row == null)
        {
            id = ai.saveRecord(userId, null, TYPE, title, setup.toString(), blueprint.toString(),
                    generated.model(), generated.tokens(), status);
        }
        else
        {
            id = row.recordId();
            if (records.updateBlueprint(userId, id, title, setup.toString(), blueprint.toString(),
                    generated.model(), generated.tokens()) != 1)
            {
                throw new IllegalStateException("保存 Blueprint 会话失败");
            }
        }
        if (id == null)
        {
            throw new IllegalStateException("缺少 Blueprint 会话编号");
        }
        int points = ai.consumeStructuredPoints(userId, TYPE, id, generated.tokens());
        return object().put("done", true).put("sessionId", id).put("quotaUsed", points)
                .put("truncated", generated.truncated()).set("blueprint", blueprint);
    }

    private BlueprintRecord session(Long userId, Long id, boolean lock)
    {
        BlueprintRecord row = id == null || id <= 0 ? null : records.selectBlueprint(userId, id, lock);
        if (row == null)
        {
            throw new java.util.NoSuchElementException("Blueprint 会话不存在");
        }
        return row;
    }

    private static ObjectNode setup(Request request)
    {
        if (request == null || optional(request.title()) == null)
        {
            throw new IllegalArgumentException("请填写作品标题");
        }
        String title = request.title().strip();
        validateLength(title, 100, "作品标题");
        validateLength(request.inspiration(), 10000, "作品灵感");
        validateLength(request.genre(), 100, "题材");
        validateLength(request.prompt(), 10000, "起稿要求");
        int episodes = request.episodes() == null ? 12 : request.episodes();
        BigDecimal minutes = request.minutes() == null ? BigDecimal.valueOf(2) : request.minutes();
        if (episodes < 1 || episodes > 60 || minutes.signum() <= 0 || minutes.compareTo(BigDecimal.valueOf(60)) > 0
                || minutes.multiply(BigDecimal.valueOf(60)).stripTrailingZeros().scale() > 0)
        {
            throw new IllegalArgumentException("请填写 1–60 集及可换算为整秒的单集时长（最多 60 分钟）");
        }
        ObjectNode setup = object().put("title", title).put("inspiration", optional(request.inspiration()))
                .put("genre", optional(request.genre())).put("episodes", episodes).put("minutes", minutes)
                .put("prompt", optional(request.prompt())).put("model", optional(request.model()));
        if (request.skillId() != null)
        {
            setup.put("skillId", request.skillId());
        }
        return setup;
    }

    private static void setPlan(ObjectNode blueprint, ObjectNode setup)
    {
        blueprint.set("planEpisodes", setup.path("episodes"));
        blueprint.set("minutesPerEpisode", setup.path("minutes"));
    }

    static JsonNode findEntry(JsonNode entries, String key)
    {
        for (JsonNode entry : entries)
        {
            if (key.equals(text(entry.path("key"))))
            {
                return entry;
            }
        }
        throw new IllegalArgumentException("指定的人物或物品不存在");
    }

    static String optional(String text)
    {
        return text == null || text.isBlank() ? null : text.strip();
    }

    static void validateLength(String text, int max, String field)
    {
        if (text != null && text.codePointCount(0, text.length()) > max)
        {
            throw new IllegalArgumentException(field + "过长");
        }
    }

    private static String now()
    {
        return LocalDateTime.now().format(DATE);
    }

    private static String stageName(String section)
    {
        return "storyCore".equals(section) ? "story" : section;
    }

    private static String prompt(List<String> sections)
    {
        return "根据起稿设定和作者说明制作短剧作品资料，返回且仅返回下列字段。"
                + "大纲仅写三幕推进，不生成章节或分集事件卡。引用已有对象时使用准确姓名或 key。\n"
                + String.join("\n", sections.stream().map(BlueprintStructure::schema).toList());
    }

    private record Generated(ObjectNode blueprint, int tokens, String model, boolean truncated) { }
}
