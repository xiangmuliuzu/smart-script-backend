package com.smartscript.platform.content.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.domain.SysWorkLedger;
import com.smartscript.platform.content.domain.WorkGenEpisode;
import com.smartscript.platform.content.domain.WorkGenJob;

/**
 * 后台队列的单集提示词组装。
 *
 * 与前端 src/utils/creatorAi.js 的 ledgerPromptBlock / stateGuardBlock / chapterTail 等保持同口径：
 * 队列在后端执行，无法再依赖前端页面里的台账副本，因此这里重新从库中取最新状态，
 * 保证「本集承接上一集真实结尾」「道具与写法沿用最新台账」两个特性不丢。
 *
 * 截断方向：道具档案与写法基线整段前置（主数据，截断会让件数与文风失锚），
 * 其余追加式流水各取尾部（最新事实在末尾）。
 *
 * @author smartscript
 */
public final class WorkGenPromptSupport
{
    /** 单集正文失控阈值：提示词要求 700-900 字，超过 1500 视为续写拼接失控 */
    public static final int EPISODE_TEXT_MAX = 1500;

    /** 上一集末尾注入字数 */
    private static final int PREV_TAIL = 500;

    /** 台账总预算 */
    private static final int LEDGER_LIMIT = 1600;

    /** 集号正则（第N集：） */
    private static final Pattern EPISODE_HEAD = Pattern.compile("^第\\s*(\\d{1,3})\\s*集[：:]", Pattern.MULTILINE);

    /** 本集出场名单 */
    private static final Pattern CAST_LINE = Pattern.compile("本集出场[：:]\\s*(.+)");

    /** 人物卡里的姓名（姓名（身份）： 或 姓名：） */
    private static final Pattern NAME_LINE = Pattern.compile("(?:^|\\n)\\s*([\\u4e00-\\u9fa5]{2,4})\\s*[（(：:]");

    private WorkGenPromptSupport()
    {
    }

    /** 组装一集的完整提示词。 */
    public static String buildEpisodePrompt(WorkGenJob job, WorkGenEpisode episode,
            SysWorkLedger ledger, List<SysWorkChapter> chapters, List<Map<String, Object>> volumes)
    {
        String outline = safe(job.getOutline());
        String title = safe(episode.getTitle());
        String card = safe(episode.getCard());
        List<String> roster = splitRoster(job.getRoster());
        List<String> cast = episodeCastFrom(card);
        if (cast.isEmpty())
        {
            cast = castFromCharacters(job.getCharacters(), card);
        }
        String ledgerText = ledgerPromptBlockByCast(ledger, cast, LEDGER_LIMIT);
        String guard = stateGuardBlock(roster,
                ledger == null ? "" : ledger.getItems(),
                ledger == null ? "" : ledger.getProps(),
                ledger == null ? "" : ledger.getTimeline(),
                ledger == null ? "" : ledger.getStyleGuide());
        String setting = safe(job.getCharacters()).isEmpty()
                ? ""
                : "【人物设定（姓名与身份固定，不得改名，不得新增未设定的人物）】\n" + tail(safe(job.getCharacters()), 900);
        String volumeText = volumeSnapshotBlock(volumes, episode.getNo());
        String prevText = chapters == null || chapters.isEmpty()
                ? ""
                : chapterTail(chapters.get(chapters.size() - 1).getContent(), PREV_TAIL);
        String summaryBlock = chapterSummaryBlock(chapters, episode.getNo());

        StringBuilder prompt = new StringBuilder();
        prompt.append("请把下面这集写成一集完整剧本：700-900 字（超过 1000 字即不合格），以动作和对白推进，对白用“角色：台词”；")
                .append("沿用台账里的写法基线（人称、句式、对白与叙述比例、标点一致），用叙述和动作补足信息，不要用全角括号补叙，不要写整段心理独白；")
                .append("开头一句自然交代地点和时间，不要写场景头、镜号、景别、运镜；结尾留一个具体钩子，且钩子必须是本集新发生的事。只写这一集。\n")
                .append("集名：").append(title).append('\n')
                .append("全剧事件卡：\n").append(outline).append('\n')
                .append("本集事件卡：\n").append(card);
        appendBlock(prompt, guard);
        appendBlock(prompt, setting);
        appendBlock(prompt, summaryBlock);
        appendBlock(prompt, volumeText);
        appendBlock(prompt, ledgerText);
        if (!prevText.isEmpty())
        {
            prompt.append("\n\n【上一章结尾（本集第一段从这里往下接着写）】\n").append(prevText)
                    .append("\n本集开场直接承接上面最后一个动作或悬念，用它的结果往下推；人物、事件、道具的状态只能通过情节自然过渡变化。")
                    .append("只有三种情况可以直接换场：上一集事件已经收束、本集开启另一个过渡事件、本集切到另一视角继续同一事件。");
        }
        return prompt.toString();
    }

    /** 续写提示词（单集触达长度上限时补一次）。 */
    public static String buildContinuation(String existing, WorkGenJob job, WorkGenEpisode episode,
            SysWorkLedger ledger)
    {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请从已有正文末尾接着写完“").append(safe(episode.getTitle()))
                .append("”这一集剧本，700-900 字，保持动作与对白推进，不要重复已有正文、不要写场景头或镜头术语。\n")
                .append("全剧事件卡：\n").append(safe(job.getOutline()))
                .append("\n本集事件卡：\n").append(safe(episode.getCard()))
                .append("\n已有正文末尾：\n").append(tail(existing, 3000));
        String guard = stateGuardBlock(splitRoster(job.getRoster()),
                ledger == null ? "" : ledger.getItems(),
                ledger == null ? "" : ledger.getProps(),
                ledger == null ? "" : ledger.getTimeline(),
                ledger == null ? "" : ledger.getStyleGuide());
        appendBlock(prompt, guard);
        appendBlock(prompt, ledgerPromptBlock(ledger, 1200));
        return prompt.toString();
    }

    /** 台账注入（按出场人物裁剪人物与关系，其余流水全量）。 */
    public static String ledgerPromptBlockByCast(SysWorkLedger ledger, List<String> cast, int limit)
    {
        if (ledger == null)
        {
            return "";
        }
        if (cast == null || cast.isEmpty())
        {
            return ledgerPromptBlock(ledger, limit);
        }
        List<String> parts = new ArrayList<>();
        String characters = hitLines(ledger.getCharacters(), cast);
        if (characters.isEmpty())
        {
            characters = tail(safe(ledger.getCharacters()), 700);
        }
        if (!characters.isEmpty())
        {
            parts.add("【本集出场人物（只写这几人，勿写未出场人物）】\n" + characters);
        }
        String relations = hitLines(ledger.getRelations(), cast);
        if (!relations.isEmpty())
        {
            parts.add("【相关关系（仅本集出场人物）】\n" + relations);
        }
        SysWorkLedger rest = new SysWorkLedger();
        rest.setForeshadow(ledger.getForeshadow());
        rest.setTimeline(ledger.getTimeline());
        rest.setItems(ledger.getItems());
        rest.setProps(ledger.getProps());
        rest.setStyleGuide(ledger.getStyleGuide());
        rest.setThreads(ledger.getThreads());
        String restText = ledgerPromptBlock(rest, Math.max(900, limit - 900));
        if (!restText.isEmpty())
        {
            parts.add(restText);
        }
        if (parts.isEmpty())
        {
            return "";
        }
        String text = String.join("\n\n", parts);
        return text.length() > limit ? text.substring(0, limit) : text;
    }

    /** 台账注入块（道具档案与写法基线整段前置，其余六类各取尾部）。 */
    public static String ledgerPromptBlock(SysWorkLedger ledger, int limit)
    {
        if (ledger == null)
        {
            return "";
        }
        String props = safe(ledger.getProps()).trim();
        String style = safe(ledger.getStyleGuide()).trim();
        List<String> parts = new ArrayList<>();
        if (!props.isEmpty())
        {
            parts.add("【重要道具档案（件数、持有人、是否已交付以它为准，不得改数）】\n" + head(props, 1400));
        }
        if (!style.isEmpty())
        {
            parts.add("【写法基线（沿用既有文风，不得中途换叙述方式）】\n" + head(style, 400));
        }
        String[][] fields = {
                { "主要人物", ledger.getCharacters() },
                { "人物关系", ledger.getRelations() },
                { "伏笔（埋在哪集/是否回收）", ledger.getForeshadow() },
                { "时间线", ledger.getTimeline() },
                { "物品位置（逐集去向）", ledger.getItems() },
                { "未解线索", ledger.getThreads() } };
        int remaining = Math.max(600, limit - props.length() - style.length());
        int perField = Math.max(120, remaining / fields.length);
        List<String> lines = new ArrayList<>();
        for (String[] field : fields)
        {
            String value = safe(field[1]).trim();
            if (value.isEmpty())
            {
                continue;
            }
            lines.add(field[0] + "：\n" + (value.length() > perField ? "（前略）" + tail(value, perField) : value));
        }
        if (!lines.isEmpty())
        {
            parts.add("【创作台账（已定事实，不得冲突）】\n" + String.join("\n\n", lines));
        }
        String text = String.join("\n\n", parts);
        return text.length() > limit + 400 ? text.substring(0, limit + 400) : text;
    }

    /**
     * 人/事/物/写法铁律（正面约束）。
     *
     * 写法先要求「承接上一集最后一个动作」，再给三种允许换场的例外，
     * 而不是只说「禁止照抄」——纯负面约束会让模型把上一集结尾当成要避开的东西。
     */
    public static String stateGuardBlock(List<String> roster, String items, String props,
            String timeline, String styleGuide)
    {
        List<String> lines = new ArrayList<>();
        lines.add("【铁律·人/事/物与写法必须连贯，违反即本集不合格】");
        if (roster != null && !roster.isEmpty())
        {
            lines.add("人物：主要角色只能写这些名字——" + String.join("、", roster)
                    + "；不得改名、不得另造主要角色、不得让不在场的人凭空到场。");
        }
        else
        {
            lines.add("人物：只能写前文已出场的人；不得改名、不得另造主要角色、不得让人凭空到场。");
        }
        lines.add("配角：允许无名群众或只在本集露面的临时角色烘托气氛（如“吴德贵带来的年轻人”“米粉店里的食客”），"
                + "但他们只能说闲话、围观、被支使，不得掌握关键线索、不得替主角做决定、不得推动主线、不得跨集复用；"
                + "凡要承担主线动作或连续多集出现的人，必须已在人物列表里。");
        lines.add("道具：件数、持有人、完好状态一律沿用下面的道具档案，不得改数、不得凭空多出或少掉；"
                + "档案里已写「已交付给某某」的道具，本集不得再交付一次，也不得在没有「退回/夺回/丢失」情节的情况下回到原主手里；"
                + "本集事件卡没写“丢失/拿走/损坏/交出”，正文不得改变它的状态。");
        lines.add("状态过渡：本集开场必须接着上一集结尾最后一个动作或悬念往下写，人物、事件、道具的状态只能通过情节自然过渡发生变化；"
                + "只有三种情况可以直接换场——上一集事件已经收束、本集开启另一个过渡事件、本集切到另一视角继续同一事件；"
                + "除这三种，不得让人物立场、伤势、在场关系或道具归属在没有过渡的情况下突变。");
        lines.add("事件：只承接下列已发生的事实；本集事件卡规定的进程不得跳过，已发生的事不得再写一遍。");
        lines.add("写法：沿用下面的写法基线（人称、句式、对白比例、标点、段落），不得中途改成括注体、整段心理独白、场景头或镜头术语；"
                + "文风一旦变化，读者会以为换了写手。");
        List<String> data = new ArrayList<>();
        String propText = head(safe(props), 1100);
        String itemText = head(safe(items), 240);
        String eventText = head(safe(timeline), 320);
        String styleText = head(safe(styleGuide), 400);
        if (!propText.isEmpty())
        {
            data.add("【重要道具档案（件数与归属以它为准）】\n" + propText);
        }
        if (!itemText.isEmpty())
        {
            data.add("【物品位置（逐集去向）】\n" + itemText);
        }
        if (!eventText.isEmpty())
        {
            data.add("【已发生事件（沿用）】\n" + eventText);
        }
        if (!styleText.isEmpty())
        {
            data.add("【写法基线（沿用）】\n" + styleText);
        }
        if (!data.isEmpty())
        {
            lines.add("");
            lines.addAll(data);
        }
        String text = String.join("\n", lines);
        return text.length() > 1600 ? text.substring(0, 1600) : text;
    }

    /** 卷级快照：本卷 + 上卷，替代整份台账全文。 */
    public static String volumeSnapshotBlock(List<Map<String, Object>> volumes, int chapterNo)
    {
        if (volumes == null || volumes.isEmpty())
        {
            return "";
        }
        int volumeNo = (int) Math.ceil(Math.max(1, chapterNo) / 10.0);
        List<String> parts = new ArrayList<>();
        String current = snapshotOf(volumes, volumeNo);
        if (!current.isEmpty())
        {
            parts.add("【本卷状态（第" + volumeNo + "卷，当前事实以此为准）】\n" + tail(current, 800));
        }
        if (volumeNo > 1)
        {
            String previous = snapshotOf(volumes, volumeNo - 1);
            if (!previous.isEmpty())
            {
                parts.add("【上卷状态（第" + (volumeNo - 1) + "卷，仅作背景，不得与本卷冲突）】\n" + tail(previous, 800));
            }
        }
        if (parts.isEmpty())
        {
            return "";
        }
        String text = String.join("\n\n", parts);
        return text.length() > 1600 ? tail(text, 1600) : text;
    }

    /** 最近 6 集已落库摘要（跨章连贯锚点）。 */
    public static String chapterSummaryBlock(List<SysWorkChapter> chapters, int currentNo)
    {
        if (chapters == null || chapters.isEmpty())
        {
            return "";
        }
        List<SysWorkChapter> list = new ArrayList<>();
        for (SysWorkChapter chapter : chapters)
        {
            if (chapter == null || chapter.getChapterNo() == null)
            {
                continue;
            }
            if (chapter.getChapterNo() < currentNo && !safe(chapter.getSummary()).trim().isEmpty())
            {
                list.add(chapter);
            }
        }
        list.sort((a, b) -> Integer.compare(a.getChapterNo(), b.getChapterNo()));
        List<SysWorkChapter> tailList = list.size() > 6 ? list.subList(list.size() - 6, list.size()) : list;
        if (tailList.isEmpty())
        {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (SysWorkChapter chapter : tailList)
        {
            lines.add("第" + chapter.getChapterNo() + "集：" + safe(chapter.getSummary()).trim());
        }
        String text = "";
        for (int i = lines.size() - 1; i >= 0; i--)
        {
            String next = text.isEmpty() ? lines.get(i) : lines.get(i) + "\n" + text;
            if (next.length() > 1800)
            {
                break;
            }
            text = next;
        }
        return text.isEmpty() ? "" : "【前情摘要（已发生事实，勿重复交代）】\n" + text;
    }

    /** 从整份大纲取某一集的事件卡（先按集号，再按集名）。 */
    public static String episodeCardFrom(String outline, String chapterTitle, int chapterNo)
    {
        String text = safe(outline);
        Matcher matcher = EPISODE_HEAD.matcher(text);
        List<int[]> heads = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        while (matcher.find())
        {
            heads.add(new int[] { matcher.start(), matcher.end() });
            numbers.add(Integer.parseInt(matcher.group(1)));
        }
        if (heads.isEmpty())
        {
            return "";
        }
        for (int i = 0; i < heads.size(); i++)
        {
            if (numbers.get(i) == chapterNo)
            {
                int end = i + 1 < heads.size() ? heads.get(i + 1)[0] : text.length();
                return text.substring(heads.get(i)[0], end).trim();
            }
        }
        String name = safe(chapterTitle).replaceFirst("^第\\s*\\S+?\\s*[集章][：:·\\s]*", "").trim();
        if (name.isEmpty())
        {
            return "";
        }
        for (int i = 0; i < heads.size(); i++)
        {
            int end = i + 1 < heads.size() ? heads.get(i + 1)[0] : text.length();
            String block = text.substring(heads.get(i)[0], end);
            if (block.contains(name))
            {
                return block.trim();
            }
        }
        return "";
    }

    /** 从本集事件卡取「本集出场：A、B」。 */
    public static List<String> episodeCastFrom(String card)
    {
        List<String> names = new ArrayList<>();
        Matcher matcher = CAST_LINE.matcher(safe(card));
        if (!matcher.find())
        {
            return names;
        }
        for (String part : matcher.group(1).split("[、，,／/；;]+"))
        {
            String name = part.trim();
            if (!name.isEmpty() && names.size() < 6)
            {
                names.add(name);
            }
        }
        return names;
    }

    /** 事件卡没有出场名单时，从人物卡里取与事件卡同名的角色。 */
    public static List<String> castFromCharacters(String characters, String card)
    {
        List<String> names = new ArrayList<>();
        Matcher matcher = NAME_LINE.matcher(safe(characters));
        String text = safe(card);
        while (matcher.find() && names.size() < 6)
        {
            String name = matcher.group(1);
            if (!names.contains(name) && text.contains(name))
            {
                names.add(name);
            }
        }
        return names;
    }

    /** 人物白名单：前端以顿号/逗号分隔提交。 */
    public static List<String> splitRoster(String roster)
    {
        List<String> names = new ArrayList<>();
        for (String part : safe(roster).split("[、，,／/；;\\s]+"))
        {
            String name = part.trim();
            if (!name.isEmpty() && names.size() < 30)
            {
                names.add(name);
            }
        }
        return names;
    }

    /** 去掉与上一集末尾重复的收尾（重合 ≥24 字才裁，避免误删自然承接）。 */
    public static String stripRepeatedTail(String text, String prevTail)
    {
        String body = safe(text);
        String prev = safe(prevTail).trim();
        if (body.trim().isEmpty() || prev.isEmpty())
        {
            return body;
        }
        int max = Math.min(Math.min(prev.length(), body.length()), 400);
        for (int len = max; len >= 24; len--)
        {
            if (body.endsWith(prev.substring(prev.length() - len)))
            {
                String kept = body.substring(0, body.length() - len);
                if (!kept.trim().isEmpty())
                {
                    return kept.replaceFirst("\\s+$", "");
                }
            }
        }
        return body;
    }

    /** 续写开头重复已有正文时去掉重复前缀。 */
    public static String stripRepeatedHead(String text, String existing)
    {
        String body = safe(text);
        String base = safe(existing).trim();
        if (body.trim().isEmpty() || base.isEmpty())
        {
            return body;
        }
        int max = Math.min(Math.min(200, body.length()), base.length());
        for (int len = max; len >= 16; len--)
        {
            if (base.endsWith(body.substring(0, len)))
            {
                return body.substring(len).replaceFirst("^[\\s，。；：]+", "");
            }
        }
        return body;
    }

    /** 超长正文按句末收到上限，不留半句。 */
    public static String clampEpisodeText(String text)
    {
        String body = safe(text).trim();
        if (body.length() <= EPISODE_TEXT_MAX)
        {
            return body;
        }
        String slice = body.substring(0, EPISODE_TEXT_MAX);
        int cut = Math.max(Math.max(slice.lastIndexOf('。'), slice.lastIndexOf('！')),
                Math.max(Math.max(slice.lastIndexOf('？'), slice.lastIndexOf('”')), slice.lastIndexOf('\n')));
        return (cut > EPISODE_TEXT_MAX / 2 ? slice.substring(0, cut + 1) : slice).trim();
    }

    private static String snapshotOf(List<Map<String, Object>> volumes, int volumeNo)
    {
        for (Map<String, Object> volume : volumes)
        {
            Object no = volume.get("volumeNo") != null ? volume.get("volumeNo") : volume.get("volume_no");
            if (no instanceof Number number && number.intValue() == volumeNo)
            {
                Object snapshot = volume.get("snapshot");
                return snapshot == null ? "" : String.valueOf(snapshot).trim();
            }
        }
        return "";
    }

    private static String hitLines(String text, List<String> names)
    {
        List<String> hits = new ArrayList<>();
        for (String line : safe(text).split("\n"))
        {
            String trimmed = line.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }
            for (String name : names)
            {
                if (!name.isEmpty() && trimmed.contains(name))
                {
                    hits.add(trimmed);
                    break;
                }
            }
        }
        return String.join("\n", hits);
    }

    public static String chapterTail(String content, int limit)
    {
        return tail(safe(content).trim(), limit);
    }

    private static void appendBlock(StringBuilder builder, String block)
    {
        if (block != null && !block.isEmpty())
        {
            builder.append("\n\n").append(block);
        }
    }

    private static String head(String text, int limit)
    {
        return text.length() > limit ? text.substring(0, limit) : text;
    }

    private static String tail(String text, int limit)
    {
        return text.length() > limit ? text.substring(text.length() - limit) : text;
    }

    private static String safe(String text)
    {
        return text == null ? "" : text;
    }
}
