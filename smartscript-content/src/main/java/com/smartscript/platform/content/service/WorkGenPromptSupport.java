package com.smartscript.platform.content.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
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
    /** 单集正文失控阈值：提示词只给上限 1500 字（重要的集允许略超），超过 1800 视为续写拼接失控 */
    public static final int EPISODE_TEXT_MAX = 1500;

    /** 上一集末尾注入字数 */
    private static final int PREV_TAIL = 500;

    /** 台账总预算 */
    private static final int LEDGER_LIMIT = 1600;

    /** 集号正则（第N集：） */
    private static final Pattern EPISODE_HEAD = Pattern.compile("^第\\s*(\\d{1,3})\\s*集[：:]", Pattern.MULTILINE);

    /** 本集出场名单 */
    private static final Pattern CAST_LINE = Pattern.compile("本集出场[：:]\\s*(.+)");

    /** 剧本行（「角色：台词」形态，用来判定已落库正文是不是剧本体） */
    private static final Pattern SCRIPT_LINE = Pattern.compile("^[\\u4e00-\\u9fa5]{2,4}[：:]");

    /** 中文引号对白 */
    private static final Pattern QUOTED = Pattern.compile("“([^”]*)”");

    /** 大纲事件卡的固定字段行（不是角色台词行，清洗时跳过） */
    private static final Pattern OUTLINE_FIELD = Pattern.compile(
            "^\\s*(第\\s*\\d+\\s*集|开场承接|本集事件|冲突与推进|结果或反转|结尾钩子|本集出场|"
            + "故事简介|人物增补|分集事件卡|主要人物|人物关系|关键道具|重要道具档案|写法基线)[：:]");

    /** 大纲里的角色台词行：角色名（可带括号注解）：台词 */
    private static final Pattern OUTLINE_ROLE_LINE = Pattern.compile(
            "^\\s*([\\u4e00-\\u9fa5·]{2,5}(?:（[^）]{1,12}）)?)[：:]\\s*(.+)$");

    /** 人物卡里的姓名（姓名（身份）： 或 姓名：） */
    private static final Pattern NAME_LINE = Pattern.compile("(?:^|\\n)\\s*([\\u4e00-\\u9fa5]{2,4})\\s*[（(：:]");

    private WorkGenPromptSupport()
    {
    }

    /**
     * 台账基线起草提示词（一键生成专用）。
     *
     * 用户只给题材与要求，不填台账；这里用同一组输入与同一模型起草三件套，
     * 保证「道具件数」「写法基线」「人物表」从第 1 集起就有唯一口径。
     */
    public static String buildBootstrapPrompt(WorkGenJob job)
    {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是这部长篇剧集的创作统筹。正式写第 1 集之前，先按用户设定起草这部作品的创作台账基线，之后每一集都沿用这份基线。\n")
                .append("【题材】").append(safe(job.getGenre())).append('\n')
                .append("【计划集数】").append(job.getTotal() == null ? "" : job.getTotal()).append('\n')
                .append("【用户要求】").append(safe(job.getUserPrompt())).append('\n');
        String outline = safe(job.getOutline());
        if (!outline.isEmpty())
        {
            prompt.append("【剧本大纲】\n").append(head(outline, 3000)).append('\n');
        }
        String characters = safe(job.getCharacters());
        if (!characters.isEmpty())
        {
            prompt.append("【已有的人物设定（合并，不要改名）】\n").append(tail(characters, 900)).append('\n');
        }
        prompt.append("起草规则：\n")
                .append("1. props：一行一件全剧重要道具，格式「名称｜件数｜持有人｜当前状态｜外观与辨识特征」。")
                .append("一件道具只能占一行，禁止在一行里用分号再列第二件；件数只能有一个口径，")
                .append("大纲没写清就写「待定」，绝不自行编出第二套件数（例如不要同时出现十二面与十三面）。\n")
                .append("2. styleGuide：写成可直接执行的写法规则——叙述人称、句子长短、对白与叙述比例、禁用写法；")
                .append("每集字数只设上限「不超过 1500 字，不设下限」（这是本系统正文生成口径，不得写成三千字或长篇字数）；")
                .append("不要写「文风细腻」这类形容词，整段 80-200 字。\n")
                .append("3. characters：一人一行，格式「姓名｜身份｜性格｜与其他人物关系」，只写题材、大纲和要求里会出现的人。\n")
                .append("4. roster：全部人物姓名，用顿号分隔。\n")
                .append("只输出 JSON，不要任何解释：")
                .append("{\"props\":\"\",\"styleGuide\":\"\",\"characters\":\"\",\"roster\":\"\"}");
        return prompt.toString();
    }

    /** 解析台账基线起草结果；缺项返回空串，交给调用方决定是否跳过。 */
    public static Map<String, String> parseBootstrap(String raw)
    {
        Map<String, String> result = new LinkedHashMap<>();
        JsonNode root = ChapterSummarySupport.parseJson(raw);
        for (String key : new String[] {"props", "styleGuide", "characters", "roster"})
        {
            String value = root == null ? "" : root.path(key).asText("").trim();
            if ("props".equals(key))
            {
                value = normalizePropLines(value);
            }
            result.put(key, value);
        }
        return result;
    }

    /** 一行一件：模型把两件道具挤在一行时（「；名称｜」），按行拆开，保住「件数」唯一口径。 */
    private static String normalizePropLines(String raw)
    {
        String text = safe(raw).trim();
        return text.replaceAll("；\\s*(?=[^；\\n｜]{1,20}\\s*｜)", "\n");
    }

    /**
     * 取「上一集」：本集之前、集号最大且有正文的那一章。
     *
     * 队列会先把 6-30 集的空骨架全部建出来，若直接取列表最后一章，
     * 拿到的是最后一集空章，上一章结尾永远为空，续写就接不上前面 AI 创作的内容。
     */
    public static SysWorkChapter previousChapter(List<SysWorkChapter> chapters, int currentNo)
    {
        if (chapters == null || chapters.isEmpty())
        {
            return null;
        }
        SysWorkChapter best = null;
        for (SysWorkChapter chapter : chapters)
        {
            if (chapter == null || chapter.getChapterNo() == null || chapter.getChapterNo() >= currentNo)
            {
                continue;
            }
            if (safe(chapter.getContent()).trim().isEmpty())
            {
                continue;
            }
            if (best == null || chapter.getChapterNo() > best.getChapterNo())
            {
                best = chapter;
            }
        }
        return best;
    }

    /**
     * 从已落库正文判定文体（小说体 / 剧本体），返回一句话描述，可直接写进台账与提示词。
     *
     * 依据最近 3 集正文实测：剧本体看「角色：台词」行占比，小说体看中文引号内的对白字数占比。
     * 没有可依据的正文时返回空串，调用方不得强行指定文体。
     */
    public static String detectStyle(List<SysWorkChapter> chapters, int beforeNo)
    {
        if (chapters == null || chapters.isEmpty())
        {
            return "";
        }
        List<SysWorkChapter> prior = new ArrayList<>();
        for (SysWorkChapter chapter : chapters)
        {
            if (chapter == null || chapter.getChapterNo() == null || chapter.getChapterNo() >= beforeNo)
            {
                continue;
            }
            if (!safe(chapter.getContent()).trim().isEmpty())
            {
                prior.add(chapter);
            }
        }
        if (prior.isEmpty())
        {
            return "";
        }
        prior.sort((a, b) -> Integer.compare(a.getChapterNo(), b.getChapterNo()));
        StringBuilder sample = new StringBuilder();
        for (int i = Math.max(0, prior.size() - 3); i < prior.size(); i++)
        {
            sample.append(prior.get(i).getContent()).append('\n');
        }
        String text = sample.toString();
        int chars = text.replaceAll("\\s", "").length();
        if (chars == 0)
        {
            return "";
        }
        int scriptLines = 0;
        int totalLines = 0;
        for (String line : text.split("\\n"))
        {
            String trimmed = line.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }
            totalLines++;
            if (SCRIPT_LINE.matcher(trimmed).find())
            {
                scriptLines++;
            }
        }
        int dialogueChars = 0;
        Matcher quotes = QUOTED.matcher(text);
        while (quotes.find())
        {
            dialogueChars += quotes.group(1).length();
        }
        int dialoguePct = (int) Math.round(dialogueChars * 100.0 / chars);
        boolean script = totalLines > 0 && scriptLines * 100.0 / totalLines >= 20;
        String shape = script
                ? "剧本体：逐行「角色：台词」，几乎没有叙述段"
                : "小说体：散文叙述＋中文引号对白，对白另起段，不出现「角色：」前缀";
        return shape + "；对白约占 " + dialoguePct + "%";
    }

    /** 把文体判定作为硬性约束注入本集提示词。 */
    public static String styleLockBlock(String profile)
    {
        String text = safe(profile).trim();
        if (text.isEmpty())
        {
            return "";
        }
        return "【文体锁定（依据上一集正文实测，硬性要求）】\n" + text
                + "\n本集必须与上一章文体完全一致：人称、句式、对白与叙述比例沿用上一章；"
                + "判定为小说体时，禁止改成剧本体，禁止出现「角色：」前缀，禁止把对白排成逐行列表；"
                + "判定为剧本体时，保持逐行对白体，不得改写成大段叙述。\n";
    }

    /** 组装一集的完整提示词。 */
    public static String buildEpisodePrompt(WorkGenJob job, WorkGenEpisode episode,
            SysWorkLedger ledger, List<SysWorkChapter> chapters, List<Map<String, Object>> volumes)
    {
        return buildEpisodePrompt(job, episode, ledger, chapters, volumes, "");
    }

    /**
     * 组装一集的完整提示词（带时间线上下文）。
     *
     * @param timelineBlock 时间线注入块（最近事件 + 未兑现约定/伏笔），可为空
     */
    public static String buildEpisodePrompt(WorkGenJob job, WorkGenEpisode episode,
            SysWorkLedger ledger, List<SysWorkChapter> chapters, List<Map<String, Object>> volumes,
            String timelineBlock)
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
        SysWorkChapter prevChapter = previousChapter(chapters, episode.getNo());
        String prevText = prevChapter == null ? "" : chapterTail(prevChapter.getContent(), PREV_TAIL);
        String summaryBlock = chapterSummaryBlock(chapters, episode.getNo());
        String styleProfile = detectStyle(chapters, episode.getNo());

        StringBuilder prompt = new StringBuilder();
        prompt.append("请把下面这集写成一集正文（小说体，不是剧本格式；对白用中文引号，不写“角色：”前缀）。只写本集，不复述上一集已经发生的情节。\n")
                .append("写法：以动作和对白推进，沿用台账里的写法基线（人称、句式、对白与叙述比例、标点一致）；")
                .append("开头一句自然交代地点和时间，不要写场景头、镜号、景别、运镜，不要写整段心理独白。\n")
                .append(styleLockBlock(styleProfile))
                .append("集名：").append(title).append('\n')
                .append("本集事件卡：\n").append(card).append('\n')
                .append("必须把本集事件卡的五个节点（开场承接、本集事件、冲突与推进、结果或反转、结尾钩子）全部写进正文，不能只写开头就停；")
                .append("每个节点至少落成一段具体动作或对白，不许用一个短句概括后直接跳到下一节点，也不许写到第四个节点就收尾。\n")
                .append("开场承接是硬要求：正文前两段必须交代清三件事——上一集最后停在哪个动作或画面、本集从哪个动作接起、中间隔了多久；")
                .append("这三件事只能用人物动作、对白或场景细节写出来，禁止旁白式说明：不许出现「上一刻」「上集结尾」「这一段紧接上集」「同一个场景，中间没有隔开时间」这类叙述者口吻的交代。")
                .append("确需换场时，用一句话说明属于「上集已收束／开启新的过渡事件／切到另一视角」，同样要落在人物动作里。")
                .append("若上一集结尾停在对话或动作中途（话没答完、动作没做完、场面被打断在半句上），本集正文前 100 字内必须给出回应：由被问的一方开口，正面答复、明确拒绝、反问或被迫打断，都要落成具体的一句对白；")
                .append("回应之后才允许推进事件卡的后续节点。不许让上一集开口的人在没得到任何答复的情况下离场或直接换场，")
                .append("更不许上一集还在对话、本集一开头人就到了别处办别的事，这种写法判为不合格，会被退回重写。")
                .append("不许同一人物不提任何时间与起因就直接出现在新场景做新的事，这种写法判为不合格，会被退回重写。")
                .append("结尾钩子必须是本集新发生的事。\n")
                .append("篇幅：正文不超过 1500 字（含标点），不设下限，写到把事件卡五个节点写透即可；不得为凑字铺陈，也不得超出 1500 字。\n")
                .append("一致性：数字、日期、人物生死、道具件数与下面的硬事实清单一律不得冲突；若要改变某项状态，必须写出触发它的过渡事件，不能无因突变。\n");
        appendBlock(prompt, guard);
        appendBlock(prompt, WorkGenConsistencySupport.hardFactsBlock(ledger, episode.getNo()));
        appendBlock(prompt, WorkStateBoardSupport.block(ledger, episode.getNo()));
        appendBlock(prompt, setting);
        appendBlock(prompt, summaryBlock);
        appendBlock(prompt, volumeText);
        appendBlock(prompt, ledgerText);
        prompt.append(threadScheduleBlock(ledger == null ? "" : ledger.getThreads(), episode.getNo()));
        if (timelineBlock != null && !timelineBlock.isBlank())
        {
            prompt.append("\n\n").append(timelineBlock);
        }
        if (!prevText.isEmpty())
        {
            prompt.append("\n\n【上一章结尾（只说清承接关系，不要重写这一段）】\n").append(prevText)
                    .append("\n本集前两段必须交代：上一集最后停在哪个动作或画面、本集从哪个动作接起、中间隔了多久。")
                    .append("若人物、地点、时间有变化，必须写出原因或过渡（上集已收束／开启新的过渡事件／切到另一视角），")
                    .append("不能什么都没有就直接换到另一个场景做另一件事；缺这项会被验收判不合格并退回重写。")
                    .append("上一章结尾若停在对话或动作中途，本集必须先在同一场景把它收束：上一章最后一句是「")
                    .append(lastMeaningfulLine(prevText))
                    .append("」，本集前 100 字内就要有人正面接这句话（答复、拒绝或打断），不许提问的人没得到回应就走或直接换场。")
                    .append("不要重复上一章的原文，不要把上一章已经完成的事件再演一遍。");
        }
        return prompt.toString();
    }

    /**
     * 悬念排期：把未解线索按「计划兑现点」分成两类。
     *
     * 到期项（兑现点 <= 本集）要求本集给出进展或结果；
     * 未到期项只作背景，明确禁止提前解决——长线悬念（如三年之约）不能因为写到了下一集就被迫收线。
     * 台账里的兑现点统一写成「第N集」，取行内最后出现的集号（兼容「第3集埋下、第8集兑现」）。
     */
    public static String threadScheduleBlock(String threads, int currentNo)
    {
        String text = safe(threads).trim();
        if (text.isEmpty())
        {
            return "";
        }
        List<String> due = new ArrayList<>();
        List<String> later = new ArrayList<>();
        for (String raw : text.split("\\n"))
        {
            String line = raw.trim();
            if (line.isEmpty())
            {
                continue;
            }
            line = line.replaceFirst("^(未解线索|线索)[：:]", "").trim();
            if (line.contains("已结") || line.contains("已回收") || line.contains("已兑现"))
            {
                continue;
            }
            int dueNo = lastEpisodeNo(line);
            if (dueNo > 0 && dueNo <= currentNo)
            {
                due.add(line);
            }
            else
            {
                later.add(line);
            }
        }
        StringBuilder block = new StringBuilder();
        if (!due.isEmpty())
        {
            block.append("\n\n【本集必须处理（已到期未结的悬念/约定）】\n")
                    .append(clipLines(due, 600))
                    .append("\n这些项在本集必须给出进展或结果，不能整集不提；若本集事件卡没安排，就用一到两句交代推进到哪一步。");
        }
        if (!later.isEmpty())
        {
            block.append("\n\n【背景悬念（未到期，不得提前解决）】\n")
                    .append(clipLines(later, 600))
                    .append("\n这些项只需保持存在感（可提一句、可作动机），不得在本集提前收线或给出答案。");
        }
        return block.toString();
    }

    /** 取一段文本里最后出现的「第N集」集号，取不到返回 0。 */
    private static int lastEpisodeNo(String line)
    {
        Matcher matcher = Pattern.compile("第\\s*(\\d{1,3})\\s*集").matcher(safe(line));
        int no = 0;
        while (matcher.find())
        {
            no = Integer.parseInt(matcher.group(1));
        }
        return no;
    }

    /** 按行拼接到字数上限，超出即停（不截半行）。 */
    private static String clipLines(List<String> lines, int limit)
    {
        StringBuilder sb = new StringBuilder();
        for (String line : lines)
        {
            if (sb.length() + line.length() + 1 > limit)
            {
                break;
            }
            if (sb.length() > 0)
            {
                sb.append('\n');
            }
            sb.append("- ").append(line);
        }
        return sb.toString();
    }

    /** 续写提示词（单集触达长度上限时补一次）。 */
    public static String buildContinuation(String existing, WorkGenJob job, WorkGenEpisode episode,
            SysWorkLedger ledger)
    {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请从已有正文末尾接着写完“").append(safe(episode.getTitle()))
                .append("”这一集剧本，总长度不超过 1500 字，保持动作与对白推进，不要重复已有正文、不要写场景头或镜头术语。\n")
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
        lines.add("状态过渡：本集开场必须回应上一集结尾最后一个动作或悬念（给出结果、推进到下一步，或用一句话交代它为何被搁置），不能当作没发生过；"
                + "允许该悬念跨集延续，不要求在本集收尾；人物、事件、道具的状态只能通过情节自然过渡发生变化；"
                + "只有三种情况可以直接换场——上一集事件已经收束、本集开启另一个过渡事件、本集切到另一视角继续同一事件；"
                + "除这三种，不得让人物立场、伤势、在场关系或道具归属在没有过渡的情况下突变。");
        lines.add("事件：只承接下列已发生的事实；本集事件卡规定的进程不得跳过，已发生的事不得再写一遍。");
        lines.add("写法：沿用下面的写法基线（人称、句式、对白比例、标点、段落），不得中途改成括注体、整段心理独白、场景头或镜头术语；"
                + "文风一旦变化，读者会以为换了写手。");
        List<String> data = new ArrayList<>();
        String propText = head(safe(props), 1100);
        String itemText = tail(safe(items), 240);
        String eventText = tail(safe(timeline), 320);
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

    /** 剥掉模型夹带在正文里的零宽/方向控制字符（U+200B–U+200F、U+202A–U+202E、U+2060–U+2064、U+FEFF、U+00AD）。 */
    public static String stripInvisible(String text)
    {
        String body = safe(text);
        if (body.isEmpty())
        {
            return body;
        }
        StringBuilder out = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++)
        {
            char c = body.charAt(i);
            boolean drop = (c >= '\u200B' && c <= '\u200F') || (c >= '\u202A' && c <= '\u202E')
                    || (c >= '\u2060' && c <= '\u2064') || c == '\uFEFF' || c == '\u00AD'
                    || Character.getType(c) == Character.FORMAT;
            if (!drop)
            {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** 去掉与上一集结尾重复的开头（模型常把上一集结尾整段抄成本集开场，逐轮剥净）。 */
    public static String stripRepeatedHead(String text, String existing)
    {
        String body = safe(text);
        String base = safe(existing).trim();
        if (body.trim().isEmpty() || base.isEmpty())
        {
            return body;
        }
        for (int round = 0; round < 4; round++)
        {
            String trimmed = body.trim();
            int max = Math.min(600, Math.min(trimmed.length(), base.length()));
            boolean cut = false;
            for (int len = max; len >= 16; len--)
            {
                if (base.endsWith(trimmed.substring(0, len)))
                {
                    body = trimmed.substring(len).replaceFirst("^[\\s，。；：]+", "");
                    cut = true;
                    break;
                }
            }
            if (!cut)
            {
                break;
            }
        }
        return body;
    }

    /** 判断正文是否停在半句上：末尾不是句末标点（。！？…），就当成没写完。 */
    public static boolean endsMidway(String text)
    {
        String t = safe(text).trim();
        if (t.isEmpty())
        {
            return true;
        }
        t = t.replaceAll("[\\s”』」）】]+$", "");
        if (t.isEmpty())
        {
            return true;
        }
        char c = t.charAt(t.length() - 1);
        return !(c == '。' || c == '！' || c == '？' || c == '…' || c == '.' || c == '!' || c == '?');
    }

    /** 末尾若是「某人：」这种空台词行，直接删掉，避免下一集从一句没说的话接。 */
    public static String dropDanglingSpeaker(String text)
    {
        String body = safe(text).trim();
        String[] lines = body.split("\\n");
        if (lines.length == 0)
        {
            return body;
        }
        String last = lines[lines.length - 1].trim();
        if (last.matches("[^：:]{1,8}[：:]\\s*") || last.matches("[^：:]{1,8}[：:]\\s*“\\s*"))
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length - 1; i++)
            {
                if (!lines[i].trim().isEmpty())
                {
                    sb.append(lines[i].trim()).append('\n');
                }
            }
            return sb.toString().trim();
        }
        return body;
    }

    /** 上一集停在没接完的对话/动作上，而本集开头没接上——判为承接断点。 */
    public static boolean dropsUnfinishedDialogue(String prevTail, String text)
    {
        String prev = safe(prevTail).trim();
        String body = safe(text).trim();
        if (prev.isEmpty() || body.isEmpty())
        {
            return false;
        }
        String[] lines = prev.split("\\n");
        String last = "";
        for (int i = lines.length - 1; i >= 0; i--)
        {
            if (!lines[i].trim().isEmpty())
            {
                last = lines[i].trim();
                break;
            }
        }
        String speaker = speakerOf(last);
        boolean ask = last.endsWith("？") || last.endsWith("?");
        boolean openTurn = ask || (speaker != null && last.matches(".*[：:].*"));
        if (!openTurn)
        {
            return false;
        }
        // 开口 150 字里必须有对白；上一集是问句时，还必须是「不是提问那个人」接的话。
        String head = body.substring(0, Math.min(150, body.length()));
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[^\\s，。！？：:“”\\n]{1,8}[：:]").matcher(head);
        while (m.find())
        {
            String who = m.group().substring(0, m.group().length() - 1);
            if (!ask)
            {
                return false;
            }
            if (speaker == null || !who.equals(speaker))
            {
                return false;
            }
        }
        return true;
    }

    /** 补不回来的半句：退到最后一个完整句，宁可少一句也不留残句。 */
    public static String trimToLastSentence(String text)
    {
        String t = safe(text).trim();
        int idx = -1;
        for (int i = t.length() - 1; i >= 0; i--)
        {
            char c = t.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '…')
            {
                idx = i;
                break;
            }
        }
        return idx >= 0 ? t.substring(0, idx + 1) : t;
    }

    /** 取正文最后一行非空内容，用于在提示词里点名「上一集最后一句」。 */
    public static String lastMeaningfulLine(String text)
    {
        String[] lines = safe(text).split("\\n");
        for (int i = lines.length - 1; i >= 0; i--)
        {
            String line = lines[i].trim();
            if (!line.isEmpty())
            {
                return line.length() > 80 ? line.substring(line.length() - 80) : line;
            }
        }
        return "";
    }

    /** 取一行对白的说话人；不是对白行返回 null。 */
    private static String speakerOf(String line)
    {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^([^：:\\s，。！？“”]{1,8})[：:]").matcher(line);
        return m.find() ? m.group(1) : null;
    }

    /** 把中文正文里的半角标点统一成全角（英文单词、数字之间的不动）；引号按段重新配对。 */
    public static String toFullWidthPunct(String text)
    {
        String body = safe(text);
        if (body.isEmpty())
        {
            return body;
        }
        StringBuilder out = new StringBuilder(body.length());
        boolean openQuote = true;
        for (int i = 0; i < body.length(); i++)
        {
            char c = body.charAt(i);
            char prev = i > 0 ? body.charAt(i - 1) : ' ';
            char next = i + 1 < body.length() ? body.charAt(i + 1) : ' ';
            boolean cjkNear = isCjk(prev) || isCjk(next);
            switch (c)
            {
                case '\n':
                    out.append(c);
                    openQuote = true;
                    break;
                case '"':
                case '“':
                case '”':
                    // 模型常把全角/半角引号混着写，逐段按开合顺序重排，保证成对。
                    out.append(openQuote ? '“' : '”');
                    openQuote = !openQuote;
                    break;
                case ',':
                    out.append(cjkNear ? '，' : c);
                    break;
                case ';':
                    out.append(cjkNear ? '；' : c);
                    break;
                case '?':
                    out.append(cjkNear ? '？' : c);
                    break;
                case '!':
                    out.append(cjkNear ? '！' : c);
                    break;
                case ':':
                    out.append(cjkNear ? '：' : c);
                    break;
                case '(':
                    out.append(cjkNear ? '（' : c);
                    break;
                case ')':
                    out.append(cjkNear ? '）' : c);
                    break;
                case '\'':
                    out.append(cjkNear ? '’' : c);
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isCjk(char c)
    {
        return (c >= '\u4E00' && c <= '\u9FFF') || (c >= '\u3000' && c <= '\u303F') || Character.isLetterOrDigit(c) && c > 127;
    }

    /**
     * 大纲清洗：把「角色名：台词」的剧本体改成叙事句。
     *
     * 续写大纲偶尔会把事件卡写成一段剧本稿（周长老：……／江野：……），
     * 后台再按这份大纲写正文时会把整段对白照抄，同一场景于是出现散文版和剧本版两套。
     * 只有整份大纲里命中 3 行以上角色台词才判定为剧本稿并改写，避免误伤普通字段行。
     * 改写保留信息，只换形式：{@code 周长老：快走！} → {@code 周长老说：“快走！”}。
     */
    public static String normalizeOutlineScript(String outline)
    {
        String text = safe(outline);
        if (text.isEmpty())
        {
            return text;
        }
        String[] lines = text.split("\\r?\\n", -1);
        int roleLines = 0;
        for (String line : lines)
        {
            if (isOutlineRoleLine(line))
            {
                roleLines++;
            }
        }
        if (roleLines < 3)
        {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < lines.length; i++)
        {
            String line = lines[i];
            Matcher m = OUTLINE_ROLE_LINE.matcher(line);
            if (m.matches() && !OUTLINE_FIELD.matcher(line).find())
            {
                String speech = m.group(2).trim();
                if (speech.startsWith("“") && speech.endsWith("”") && speech.length() >= 2)
                {
                    speech = speech.substring(1, speech.length() - 1);
                }
                line = m.group(1) + "说：“" + speech + "”";
            }
            out.append(line);
            if (i < lines.length - 1)
            {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private static boolean isOutlineRoleLine(String line)
    {
        return OUTLINE_ROLE_LINE.matcher(line).matches() && !OUTLINE_FIELD.matcher(line).find();
    }

    /** 超长正文按句末收到上限，不留半句。 */
    public static String clampEpisodeText(String text)    {
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
