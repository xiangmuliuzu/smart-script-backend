package com.smartscript.platform.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 「每集验收 + 卷级快照」的提示词组装与结果解析。
 *
 * 与 AI 调用解耦，便于单测：
 *   - 验收固定 7 项（人物/因果/连续/道具/写法/伏笔/结构），≥5 项无 fail 才通过；
 *   - 快照只存当前值，长度上限 800 字（超长直接截断，不重试）。
 */
public final class ChapterReviewSupport
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 卷快照落库上限（字符） */
    public static final int SNAPSHOT_MAX = 800;

    /** 通过阈值：7 项中至少 5 项无 fail */
    public static final int PASS_MIN = 5;

    /** 七项检查项的顺序与中文名 */
    public static final String[][] ITEMS = {
            {"identity", "人物"},
            {"causality", "因果"},
            {"continuity", "连续性"},
            {"props", "道具"},
            {"style", "写法"},
            {"foreshadow", "伏笔"},
            {"structure", "结构"}
    };

    private ChapterReviewSupport()
    {
    }

    /** 验收系统提示：只判定、不改写、输出固定 JSON。 */
    public static String reviewSystemPrompt()
    {
        return "你是短剧剧本连贯性审校，只做判定，不改写正文，不评价文采。\n"
                + "逐项检查下面 7 条，每条最多给出一条 fail 或 ok；另有 note 级别只作提示，不计分：\n"
                + "1. identity 人物：是否出现设定外的新人物、改名、年龄/身份/性格突变；\n"
                + "2. causality 因果：唯一判据是「读者能否只靠本集中出现过的一句动作、台词或外部事件，完成上一集到本集的过渡」。"
                + "能——哪怕跳过一夜、换了场景——判 ok；需要读者自己补一段不存在的情节才能接上，判 fail。"
                + "以下情况只写 note，不判 fail：没写明谁派的、谁给的、叫什么名字这类来历；"
                + "没交代人物全名、官职、辈分；读者靠常识能补齐一句的背景缺口。"
                + "注意：本集开头把「上集结尾…同一场景继续」这类写作说明写进正文，属于创作者视角泄漏，判 fail。\n"
                + "3. continuity 连续性：对照【状态板】逐条看人物的所在、持有、认知。"
                + "发生变化时必须有一句可观察的触发（谁把东西拿走、谁传的话、隔了多久、什么声响把人引过去）；"
                + "没有触发就直接出现在新地点、东西换了主人、知道了本不该知道的事，判 fail。"
                + "另查上一集结尾：若停在对白或动作中途（话没答完、动作没做完），本集开头是否在同一场景把它收束掉"
                + "（有人答复、拒绝、被打断、起身离开的具体动作）；对话还没接上人就到了别处办别的事，判 fail。\n"
                + "4. props 道具：件数、持有人、完好状态是否与「重要道具档案」一致；"
                + "档案里已标记「已交付给某某」的道具，本集是否又被重新交付一次，"
                + "或未经「退回/夺回/丢失」的明确情节就从对方手里回到原主手里；\n"
                + "5. style 写法：是否沿用「写法基线」的人称、句式、对白比例与标点；"
                + "有没有中途改成括注体、整段心理独白、场景头或镜头术语；正文标点是否被换成半角（对白与叙述一律用全角）；\n"
                + "6. foreshadow 伏笔：是否只新增或推进，未到回收点的伏笔是否被提前揭晓；"
                + "状态板或台账里已经点明的线索，本集是否被整条丢掉、后文也不再提（丢线索要写进 note，不算 fail）；\n"
                + "7. structure 结构：是否有完整的冲突推进与结尾钩子，不是流水账或纯对话堆叠；"
                + "字数只查上限——正文超过 1500 字且靠铺陈灌水时写一条 note，不判 fail（不设字数下限，写短但事件卡五个节点齐全就判 ok）。\n"
                + "关于【约定】：状态板里 [约定] 类条目不要求必须兑现。取消、改期、不去了都可以，"
                + "只要本集有一句台词或一个动作交代了原因或改到何时，就判 ok；"
                + "整集对它不闻不问、后文也再不提起，才在 continuity 项记为 fail（事件卡本就与此无关时除外）。\n"
                + "输出一个 JSON 对象，结构如下：\n"
                + "{\"score\":6,\"issues\":[{\"item\":\"causality\",\"level\":\"fail\",\"detail\":\"一句话指出问题所在\","
                + "\"fix\":\"一句话给出可直接照着改的写法，例如：在本集第 1 段后补一句，交代上集结束当夜过去一夜、林昭趁晒药之机摸到丹房外墙\"}],"
                + "\"state_delta\":[\"[所在] 顾长宁｜戒律堂地库门前｜因：被秦执事叫来搬药箱\"]}\n"
                + "score 为未出现 fail 的项数（0-7）；note 不计入 score；每项最多一条 issues，ok 的项不要输出；"
                + "fix 必须具体到人物、时间与衔接方式，不要写「加强过渡」这类空话。\n"
                + "state_delta 只写本集结束时相对状态板新增或改变的状态，一行一条，格式「[所在/持有/认知/约定] 主体｜当前值｜因：触发事件」；"
                + "没有变化就输出空数组 []，不要复述没变的条目。\n"
                + "不要 Markdown 代码块，不要任何解释文字。";
    }

    /** 验收用户提示：只带本集正文、上一集摘要、设定、台账与状态板。 */
    public static String reviewUserPrompt(String title, String prevSummary, String setting, String ledger, String body)
    {
        return reviewUserPrompt(title, prevSummary, setting, ledger, "", body);
    }

    /** 验收用户提示（带状态板）：状态板是「上一集结束时已经成立的状态」，是连续性的第一判据。 */
    public static String reviewUserPrompt(String title, String prevSummary, String setting, String ledger,
            String stateBoard, String body)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("本集：").append(title == null ? "" : title).append("\n\n");
        if (prevSummary != null && !prevSummary.isBlank())
        {
            sb.append("【上一集摘要】\n").append(prevSummary.trim()).append("\n\n");
        }
        if (stateBoard != null && !stateBoard.isBlank())
        {
            sb.append("【状态板（进入本集前的既成事实：所在/持有/认知/约定）】\n").append(stateBoard.trim()).append("\n\n");
        }
        if (setting != null && !setting.isBlank())
        {
            sb.append("【设定（人物与关系口径）】\n").append(setting.trim()).append("\n\n");
        }
        if (ledger != null && !ledger.isBlank())
        {
            sb.append("【创作台账（已定事实）】\n").append(ledger.trim()).append("\n\n");
        }
        sb.append("【本集正文】\n").append(body == null ? "" : body.trim());
        return sb.toString();
    }

    /** 卷快照系统提示：压缩成覆盖式当前状态。 */
    public static String snapshotSystemPrompt(int volumeNo, int start, int end)
    {
        return "你是剧本统筹。把给出的台账与本卷（第" + volumeNo + "卷，第" + start + "–" + end
                + "集）各集摘要，压缩成一份「卷级当前状态快照」，不超过 " + SNAPSHOT_MAX + " 字。\n"
                + "只写当前值，不写演变过程、不写评价：\n"
                + "1. 主要人物当前态：处境、伤病、已知与未知的秘密、与主角的关系强度；\n"
                + "2. 未回收伏笔：每条带起始集号，未到回收点不要写结果；\n"
                + "3. 关键道具与地点：当前在谁手上、在哪；\n"
                + "4. 卷末悬念：本卷结束时的未解问题。\n"
                + "输出一个 JSON 对象：{\"snapshot\":\"……\"}\n"
                + "不要 Markdown 代码块，不要任何解释文字。";
    }

    /** 卷快照用户提示。 */
    public static String snapshotUserPrompt(String ledger, String summaries)
    {
        return "【创作台账】\n" + (ledger == null ? "（暂无）" : ledger)
                + "\n\n【本卷各集摘要】\n" + (summaries == null ? "（暂无）" : summaries)
                + "\n\n请输出卷级当前状态快照。";
    }

    /** 解析验收 JSON（容忍代码块包裹与说明文字）。 */
    public static JsonNode parse(String raw)
    {
        return ChapterSummarySupport.parseJson(raw);
    }

    /** 未通过项数（issues 里 level=fail 的条数，按项去重）。 */
    public static int failCount(JsonNode root)
    {
        if (root == null || root.isMissingNode()) return 0;
        java.util.Set<String> items = new java.util.HashSet<>();
        JsonNode issues = root.path("issues");
        if (issues.isArray())
        {
            for (JsonNode issue : issues)
            {
                if ("fail".equalsIgnoreCase(issue.path("level").asText("")) || "fail".equalsIgnoreCase(issue.path("result").asText("")))
                {
                    String item = issue.path("item").asText("").trim();
                    if (!item.isEmpty())
                    {
                        items.add(item);
                    }
                }
            }
        }
        return items.size();
    }

    /** 通过项数（7 - 未通过项数，下限 0）。 */
    public static int passScore(JsonNode root)
    {
        JsonNode score = root.path("score");
        if (score.isInt() || score.isNumber())
        {
            return Math.max(0, Math.min(ITEMS.length, score.asInt()));
        }
        return Math.max(0, ITEMS.length - failCount(root));
    }

    /** 是否通过：未通过项 ≤ (5 - PASS_MIN)，即至少 4 项 ok。 */
    public static boolean pass(JsonNode root)
    {
        return ITEMS.length - failCount(root) >= PASS_MIN;
    }

    /** 未通过明细，一行一项：中文名（key）：说明。 */
    public static String issuesText(JsonNode root)
    {
        StringBuilder sb = new StringBuilder();
        JsonNode issues = root.path("issues");
        if (!issues.isArray())
        {
            return "";
        }
        for (JsonNode issue : issues)
        {
            boolean fail = "fail".equalsIgnoreCase(issue.path("level").asText(""))
                    || "fail".equalsIgnoreCase(issue.path("result").asText(""));
            if (!fail)
            {
                continue;
            }
            String item = issue.path("item").asText("").trim();
            String detail = issue.path("detail").asText("").trim();
            String fix = issue.path("fix").asText("").trim();
            if (item.isEmpty() && detail.isEmpty())
            {
                continue;
            }
            if (sb.length() > 0)
            {
                sb.append('\n');
            }
            sb.append(labelOf(item)).append("：").append(detail.isEmpty() ? "未通过" : detail);
            if (!fix.isEmpty())
            {
                sb.append("（改法：").append(fix).append("）");
            }
        }
        return sb.toString();
    }

    /** 取卷快照正文（snapshot 字段），按上限截断。 */
    public static String snapshotOf(JsonNode root)
    {
        if (root == null || root.isMissingNode())
        {
            return "";
        }
        String text = root.path("snapshot").asText("").trim();
        if (text.isEmpty())
        {
            text = root.path("summary").asText("").trim();
        }
        return text.length() <= SNAPSHOT_MAX ? text : text.substring(0, SNAPSHOT_MAX);
    }

    /** 空 JSON 骨架（供提示词示例与测试复用）。 */
    public static String emptyReviewJson()
    {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("score", ITEMS.length);
        node.putArray("issues");
        return node.toString();
    }

    private static String labelOf(String item)
    {
        for (String[] pair : ITEMS)
        {
            if (pair[0].equalsIgnoreCase(item))
            {
                return pair[1];
            }
        }
        return item;
    }
}
