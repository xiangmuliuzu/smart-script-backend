package com.smartscript.platform.content.service.impl;

import static com.smartscript.platform.content.service.impl.StructuredWritingJson.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.domain.SysWorkLedger;
import com.smartscript.platform.content.mapper.AppAiWriteRecordMapper;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.mapper.SysWorkChapterMapper;
import com.smartscript.platform.content.mapper.SysWorkLedgerMapper;
import com.smartscript.platform.content.service.AiCallResult;
import com.smartscript.platform.identity.IdentityProvider;

@Service
public class ChapterDraftService
{
    private static final String DRAFT_TYPE = "chapter-draft";
    private static final String META_TYPE = "chapter-meta";
    private static final String META_SCHEMA = "cast 和 items 为 [{key,name,role,change}]，edges 为 [{a,b,label,dash}]。"
            + "关系两端必须出现在本章 cast 中。仅列本章实际出现的成员，缺资料时返回空数组。";
    private static final Pattern CHARACTER_ROW = Pattern.compile(
            "^[#>*·•\\s-]*([\\p{L}][\\p{L}\\p{N}· ]{0,23})\\s*(?:[（(][^）)]{0,40}[）)])?\\s*[：:]\\s*(.*)$");
    private static final Set<String> ROSTER_HEADERS = Set.of("姓名", "身份", "性格", "动机", "目标", "状态", "背景", "当前状态",
            "主要人物", "人物", "角色", "人物关系", "人物便签", "人物增补", "新增人物", "故事简介", "人物卡", "本章出场",
            "本章人物", "重要道具档案", "写法基线", "世界观", "故事主线", "结尾悬念");

    private final AiWriteServiceImpl ai;
    private final AppAiWriteRecordMapper records;
    private final SysContentWorkMapper works;
    private final SysWorkChapterMapper chapters;
    private final SysWorkLedgerMapper ledgers;
    private final IdentityProvider identity;
    private final TransactionTemplate transaction;

    public ChapterDraftService(AiWriteServiceImpl ai, AppAiWriteRecordMapper records, SysContentWorkMapper works,
            SysWorkChapterMapper chapters, SysWorkLedgerMapper ledgers, IdentityProvider identity,
            PlatformTransactionManager manager)
    {
        this.ai = ai;
        this.records = records;
        this.works = works;
        this.chapters = chapters;
        this.ledgers = ledgers;
        this.identity = identity;
        this.transaction = new TransactionTemplate(manager);
    }

    public record Request(Long workId, Integer chapterNo, String instruction, String model, Long skillId) { }

    public ObjectNode draft(Request request, Consumer<String> delta)
    {
        int chapterNo = chapterNo(request);
        Long userId = identity.currentUserId();
        SysWork work = ownedWork(userId, request.workId());
        BlueprintService.validateLength(request.instruction(), 10000, "章节要求");
        ai.checkContent(userId, request.instruction());
        ai.checkPoints(userId);
        ObjectNode context = context(work, chapterNo);
        context.put("instruction", request.instruction());
        List<SysWorkChapter> prior = chapters.selectPreviousChapters(work.getWorkId(), chapterNo);
        ArrayNode summaries = context.putArray("previousSummaries");
        for (int index = prior.size() - 1; index >= 0; index--)
        {
            SysWorkChapter chapter = prior.get(index);
            summaries.addObject().put("chapterNo", chapter.getChapterNo()).put("title", chapter.getChapterTitle())
                    .put("summary", chapter.getSummary());
        }
        if (!prior.isEmpty())
        {
            SysWorkChapter previous = chapters.selectChapterDetail(prior.get(0).getChapterId());
            if (previous != null)
            {
                String content = value(previous.getContent()).strip();
                int count = content.codePointCount(0, content.length());
                context.put("previousChapterTail", count <= 800 ? content
                        : content.substring(content.offsetByCodePoints(0, count - 800)));
            }
        }
        String system = "根据作品资料、三幕节拍及前情，为指定章节拟定标题和不超过 500 字的概要，写清承接动作、推进、结果和结尾。"
                + "只写本章资料。title、summary 和 metadata 的人物只能来自 roster，禁止新增任何有名人物。"
                + "标题和概要提到的有名人物均须列入 cast；物品来自 itemRoster，关系两端使用人物 key。"
                + "输出 {title,summary,cast,items,edges}。" + META_SCHEMA;
        AiCallResult result = ai.structuredStream(system, context.toString(), request.model(), request.skillId(), delta);
        ObjectNode output = BlueprintService.modelObject(result);
        String title = limit(text(output.path("title")), 100);
        String summary = limit(text(output.path("summary")), 500);
        if (title.isEmpty() || summary.isEmpty())
        {
            throw invalidResult();
        }
        ObjectNode draft = aiMeta(output, context, "").put("title", title).put("summary", summary);
        rejectUnknownMentions(output.path("cast"), context.path("roster"), title + "\n" + summary);
        return transaction.execute(status -> {
            Long id = record(userId, work, DRAFT_TYPE, context, draft, result);
            draft.put("tokens", result.getTotalTokens()).put("recordId", id);
            return object().put("done", true).set("draft", draft);
        });
    }

    public Map<String, Object> getMeta(Long workId)
    {
        ownedWork(identity.currentUserId(), workId);
        SysWorkLedger ledger = ledgers.selectChapterMeta(workId);
        if (ledger == null || ledger.getChapterMeta() == null)
        {
            return Map.of();
        }
        ObjectNode stored = parse(ledger.getChapterMeta());
        ObjectNode result = object();
        SysWorkChapter query = new SysWorkChapter();
        query.setWorkId(workId);
        for (SysWorkChapter chapter : chapters.selectChapterListByWorkId(query))
        {
            String number = String.valueOf(chapter.getChapterNo());
            if (stored.path(number) instanceof ObjectNode entry
                    && (!entry.has("chapterId") || entry.path("chapterId").asLong() == chapter.getChapterId()))
            {
                entry.remove("chapterId");
                result.set(number, entry);
            }
        }
        return asMap(result);
    }

    public Map<String, Object> saveMeta(Long workId, int chapterNo, Map<String, Object> request)
    {
        requireChapterNo(chapterNo);
        if (request == null)
        {
            throw new IllegalArgumentException("章节资料不能为空");
        }
        ownedWork(identity.currentUserId(), workId);
        SysWorkChapter chapter = requireChapter(workId, chapterNo);
        ObjectNode input = fromMap(request);
        if (input.toString().length() > 60000)
        {
            throw new IllegalArgumentException("章节资料过长");
        }
        String source = text(input.path("source"));
        if (!source.isEmpty() && !"ai".equals(source) && !"manual".equals(source))
        {
            throw new IllegalArgumentException("资料来源必须为 ai 或 manual");
        }
        ObjectNode meta = object();
        meta.set("cast", manualEntries(input.path("cast"), "key", "name", "role", "change"));
        meta.set("items", manualEntries(input.path("items"), "key", "name", "role", "change"));
        meta.set("edges", manualEntries(input.path("edges"), "a", "b", "label", "color"));
        meta.put("aiSummary", limit(text(input.path("aiSummary")), 500));
        meta.put("source", source.isEmpty() ? "manual" : source);
        save(chapter, meta);
        return asMap(meta);
    }

    public Map<String, Object> generateMeta(Request request)
    {
        int chapterNo = chapterNo(request);
        Long userId = identity.currentUserId();
        SysWork work = ownedWork(userId, request.workId());
        SysWorkChapter chapter = requireChapter(work.getWorkId(), chapterNo);
        if (value(chapter.getSummary()).isBlank() && value(chapter.getContent()).isBlank())
        {
            throw new IllegalArgumentException("请先填写本章概要或正文");
        }
        BlueprintService.validateLength(request.instruction(), 10000, "整理要求");
        ai.checkContent(userId, request.instruction());
        ai.checkPoints(userId);
        ObjectNode context = context(work, chapterNo);
        context.put("instruction", request.instruction());
        context.put("chapterSummary", chapter.getSummary());
        context.put("chapterContent", limit(value(chapter.getContent()), 30000));
        String system = "只从本章已有概要和正文整理人物、物品、关系及 aiSummary（最多 500 字）。"
                + "已在名单中的条目使用原 key。正文明确出现但名单缺失的条目填写原文名字，key 留空，关系两端可用名字。"
                + "不要续写或补写出处。输出 {cast,items,edges,aiSummary}。" + META_SCHEMA;
        AiCallResult result = ai.structuredStream(system, context.toString(), request.model(), request.skillId(), ignored -> { });
        ObjectNode output = BlueprintService.modelObject(result);
        String evidence = value(chapter.getSummary()) + "\n" + text(context.path("chapterContent"));
        ObjectNode meta = aiMeta(output, context, evidence).put("aiSummary", limit(text(output.path("aiSummary")), 500))
                .put("source", "ai").put("generatedAt", Instant.now().toString());
        return transaction.execute(status -> {
            save(chapter, meta);
            record(userId, work, META_TYPE, context, meta, result);
            return asMap(meta);
        });
    }

    private Long record(Long userId, SysWork work, String type, ObjectNode context, ObjectNode output, AiCallResult result)
    {
        String title = "《" + limit(work.getTitle(), 65) + "》第" + context.path("chapterNo").asInt() + "章";
        Long id = ai.saveRecord(userId, work.getWorkId(), type, title, context.toString(), output.toString(),
                ai.resolveModel(result), result.getTotalTokens(), "success");
        if (id == null)
        {
            throw new IllegalStateException("缺少章节 AI 记录编号");
        }
        ai.consumeStructuredPoints(userId, type, id, result.getTotalTokens());
        return id;
    }

    private void save(SysWorkChapter chapter, ObjectNode meta)
    {
        ObjectNode stored = meta.deepCopy().put("chapterId", chapter.getChapterId());
        if (ledgers.upsertChapterMeta(chapter.getWorkId(), chapter.getChapterId(), chapter.getChapterNo(), stored.toString()) == 0)
        {
            SysWorkChapter current = chapters.selectChapterByNo(chapter.getWorkId(), chapter.getChapterNo());
            if (current == null || !chapter.getChapterId().equals(current.getChapterId()))
            {
                throw new NoSuchElementException("章节不存在");
            }
        }
    }

    private ObjectNode context(SysWork work, int chapterNo)
    {
        SysWorkLedger ledger = ledgers.selectByWorkId(work.getWorkId());
        String saved = records.selectConfirmedBlueprint(work.getAuthorId(), work.getWorkId());
        ObjectNode blueprint = saved == null ? object() : parse(saved);
        ObjectNode data = object().put("workTitle", work.getTitle()).put("chapterNo", chapterNo);
        if (!blueprint.isEmpty())
        {
            data.set("storyCore", blueprint.path("storyCore"));
            data.set("world", blueprint.path("world"));
            data.set("threeActOutline", blueprint.path("outline"));
        }
        ArrayNode roster = data.putArray("roster");
        ArrayNode items = data.putArray("itemRoster");
        blueprint.path("characters").forEach(roster::add);
        blueprint.path("items").forEach(items::add);
        data.put("workSummary", work.getSummary());
        if (ledger != null)
        {
            ObjectNode local = data.putObject("ledger");
            local.put("storySummary", ledger.getStorySummary());
            local.put("outline", limit(value(ledger.getOutline()), 14000));
            local.put("timeline", limit(value(ledger.getTimeline()), 6000));
            local.put("foreshadow", limit(value(ledger.getForeshadow()), 6000));
            local.put("styleGuide", limit(value(ledger.getStyleGuide()), 6000));
            local.put("characters", limit(value(ledger.getCharacters()), 6000));
            local.put("relations", limit(value(ledger.getRelations()), 6000));
            local.put("items", limit(value(ledger.getItems()), 6000));
            local.put("props", limit(value(ledger.getProps()), 6000));
            local.put("stateBoard", limit(value(ledger.getStateBoard()), 6000));
            if (blueprint.isEmpty())
            {
                legacyRoster(roster, items, ledger);
            }
        }
        return data;
    }

    private static void legacyRoster(ArrayNode roster, ArrayNode items, SysWorkLedger ledger)
    {
        Set<String> names = new HashSet<>();
        for (String row : value(ledger.getCharacters()).split("\\R"))
        {
            Matcher match = CHARACTER_ROW.matcher(row.strip());
            if (match.matches())
            {
                String name = match.group(1).strip();
                if (!ROSTER_HEADERS.contains(name) && names.add(name))
                {
                    String role = match.group(2).split("[｜|]", 2)[0].strip();
                    roster.addObject().put("key", nameKey("c", name)).put("name", name).put("role", role);
                }
            }
        }
        names.clear();
        for (String row : value(ledger.getProps()).split("\\R"))
        {
            String[] cells = row.split("[｜|]", -1);
            String name = cells[0].strip();
            if (!name.isEmpty() && !name.startsWith("【") && names.add(name))
            {
                items.addObject().put("key", nameKey("i", name)).put("name", name)
                        .put("state", cells.length > 3 ? cells[3].strip() : "");
            }
        }
        for (String row : value(ledger.getItems()).split("\\R"))
        {
            String[] cells = row.split("[：:]", 2);
            String name = cells[0].strip();
            if (cells.length == 2 && !name.isEmpty() && names.add(name))
            {
                items.addObject().put("key", nameKey("i", name)).put("name", name).put("state", cells[1].strip());
            }
        }
    }

    static ObjectNode aiMeta(ObjectNode output, ObjectNode context, String evidence)
    {
        ObjectNode meta = object();
        ArrayNode cast = rosterEntries(output.path("cast"), context.path("roster"), evidence, "c");
        meta.set("cast", cast);
        meta.set("items", rosterEntries(output.path("items"), context.path("itemRoster"), evidence, "i"));
        meta.set("edges", BlueprintStructure.edges(output.path("edges"), cast));
        return meta;
    }

    private static ArrayNode rosterEntries(JsonNode source, JsonNode roster, String evidence, String prefix)
    {
        Map<String, JsonNode> byKey = BlueprintStructure.index(roster, "key");
        Map<String, JsonNode> byName = BlueprintStructure.index(roster, "name");
        Set<String> seen = new HashSet<>();
        ArrayNode result = object().arrayNode();
        for (JsonNode entry : BlueprintStructure.array(source))
        {
            String name = entry.isTextual() ? text(entry) : text(entry.path("name"));
            String key = text(entry.path("key"));
            JsonNode canonical = key.isEmpty() ? byName.getOrDefault(name, byKey.get(name)) : byKey.get(key);
            if (canonical != null && (key.isEmpty() || name.isEmpty() || name.equals(text(canonical.path("name")))))
            {
                key = text(canonical.path("key"));
                name = text(canonical.path("name"));
            }
            else if (canonical == null && !name.isEmpty() && name.length() <= 32 && evidence.contains(name))
            {
                key = nameKey(prefix, name);
            }
            else
            {
                continue;
            }
            if (seen.add(key))
            {
                result.addObject().put("key", key).put("name", name)
                        .put("role", text(entry.path("role"))).put("change", text(entry.path("change")));
            }
        }
        return result;
    }

    private static void rejectUnknownMentions(JsonNode cast, JsonNode roster, String draft)
    {
        Set<String> known = BlueprintStructure.index(roster, "name").keySet();
        for (JsonNode line : cast)
        {
            String name = line.isTextual() ? text(line) : text(line.path("name"));
            if (!name.isEmpty() && !known.contains(name) && draft.contains(name))
            {
                throw invalidResult();
            }
        }
    }

    private static ArrayNode manualEntries(JsonNode source, String... fields)
    {
        ArrayNode result = object().arrayNode();
        if (!source.isMissingNode() && !source.isNull() && (!source.isArray() || source.size() > 100))
        {
            throw new IllegalArgumentException("人物、物品和关系必须为数组，每类最多 100 项");
        }
        for (JsonNode entry : source)
        {
            if (!entry.isObject())
            {
                throw new IllegalArgumentException("章节资料条目必须为对象");
            }
            ObjectNode fieldsOnly = textFields(entry, fields);
            if (entry.path("dash").isBoolean())
            {
                fieldsOnly.put("dash", entry.path("dash").asBoolean());
            }
            result.add(fieldsOnly);
        }
        return result;
    }

    private SysWork ownedWork(Long userId, Long workId)
    {
        SysWork work = workId == null ? null : works.selectWorkById(workId);
        if (work == null)
        {
            throw new NoSuchElementException("作品不存在");
        }
        if (userId == null || !Objects.equals(work.getAuthorId(), userId))
        {
            throw new SecurityException("无权操作该作品");
        }
        return work;
    }

    private SysWorkChapter requireChapter(Long workId, int chapterNo)
    {
        SysWorkChapter chapter = chapters.selectChapterByNo(workId, chapterNo);
        if (chapter == null)
        {
            throw new NoSuchElementException("章节不存在");
        }
        return chapter;
    }

    private static int chapterNo(Request request)
    {
        if (request == null || request.workId() == null || request.workId() <= 0)
        {
            throw new IllegalArgumentException("请提供有效的作品编号");
        }
        return requireChapterNo(request.chapterNo());
    }

    private static int requireChapterNo(Integer number)
    {
        if (number == null || number <= 0)
        {
            throw new IllegalArgumentException("章号必须从 1 开始");
        }
        return number;
    }

    private static String value(String text)
    {
        return text == null ? "" : text;
    }
}
