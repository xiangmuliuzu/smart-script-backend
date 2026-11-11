package com.smartscript.platform.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 「每集验收 + 卷级快照」的提示词组装与结果解析。
 *
 * 与 AI 调用解耦，便于单测：
 *   - 验收固定 5 项（人物/因果/连续/伏笔/结构），≥4 项无 fail 才通过；
 *   - 快照只存当前值，长度上限 800 字（超长直接截断，不重试）。
 */
public final class ChapterReviewSupport
{
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 卷快照落库上限（字符） */
    public static final int SNAPSHOT_MAX = 800;

    /** 通过阈值：5 项中至少 4 项无 fail */
    public static final int PASS_MIN = 4;

    /** 五项检查项的顺序与中文名 */
    public static final String[][] ITEMS = {
            {"identity", "人物"},
            {"causality", "因果"},
            {"continuity", "连续性"},
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
                + "逐项检查下面 5 条，每条给出 fail 或 ok：\n"
                + "1. identity 人物：是否出现设定外的新人物、改名、年龄/身份/性格突变；\n"
                + "2. causality 因果：是否接住上一集结尾的钩子，本集开场是否由上一集结果推动；\n"
                + "3. continuity 连续性：时间、地点、关键道具归属、伤病与衣着状态是否与台账一致；\n"
                + "4. foreshadow 伏笔：是否只新增或推进，未到回收点的伏笔是否被提前揭晓；\n"
                + "5. structure 结构：是否有完整的冲突推进与结尾钩子，不是流水账或纯对话堆叠。\n"
                + "输出一个 JSON 对象，结构如下：\n"
                + "{\"score\":4,\"issues\":[{\"item\":\"identity\",\"level\":\"fail\",\"detail\":\"一句话指出问题所在\"}]}\n"
                + "score 为未出现 fail 的项数（0-5）；每项最多写一条 issues，ok 的项不要输出。\n"
                + "不要 Markdown 代码块，不要任何解释文字。";
    }

    /** 验收用户提示：只带本集正文、上一集摘要、设定与台账。 */
    public static String reviewUserPrompt(String title, String prevSummary, String setting, String ledger, String body)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("本集：").append(title == null ? "" : title).append("\n\n");
        if (prevSummary != null && !prevSummary.isBlank())
        {
            sb.append("【上一集摘要】\n").append(prevSummary.trim()).append("\n\n");
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

    /** 通过项数（5 - 未通过项数，下限 0）。 */
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
            if (item.isEmpty() && detail.isEmpty())
            {
                continue;
            }
            if (sb.length() > 0)
            {
                sb.append('\n');
            }
            sb.append(labelOf(item)).append("：").append(detail.isEmpty() ? "未通过" : detail);
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
        node.put("score", 5);
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
