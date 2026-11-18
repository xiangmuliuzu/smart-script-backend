package com.smartscript.platform.content.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartscript.platform.content.domain.SysWorkLedger;

/**
 * 状态板：记「上一次变化 + 触发」，供下一集生成前注入、验收时比对。
 *
 * 设计口径（对应本轮评审结论）：
 *   1. 状态板不是义务清单——约定可以不兑现，但「不兑现要有一句交代」；
 *   2. 只记会被后文引用的状态：人物所在、物件持有、认知边界、人物之间的约定；
 *   3. 每行必须带「第 N 集」与「因：…」，没有因的行说明状态是自己变的，正是要抓的断点；
 *   4. 一行一条，自由文本，后端只做行级合并，不解析业务语义。
 *
 * 行格式：
 *   [所在] 顾长宁｜戒律堂地库门前｜第6集｜因：被秦执事叫来搬药箱
 *   [持有] 淡绿丹丸｜姜晚照｜第2集｜因：从顾长宁掌心抠走
 *   [认知] 苏青鸾｜知顾长宁袖中有铜铃｜第8集｜因：当面验出
 *   [约定] 姜晚照→顾长宁｜次日再来换药｜第2集｜状态：待交代（不兑现须写一句理由）
 *
 * @author smartscript
 */
public final class WorkStateBoardSupport
{
    /** 状态板保留行数上限，超出按集号淘汰最旧的，避免提示词无限膨胀 */
    private static final int MAX_LINES = 40;

    /** 注入块字数上限 */
    private static final int BLOCK_LIMIT = 700;

    /** 闭集内合法分类 */
    private static final String[] KINDS = {"所在", "持有", "认知", "约定"};

    private WorkStateBoardSupport()
    {
    }

    /**
     * 生成本集提示词里的状态板块；台账为空或没有状态板时返回空串。
     */
    public static String block(SysWorkLedger ledger, int chapterNo)
    {
        String board = ledger == null ? "" : clean(ledger.getStateBoard());
        if (board.isEmpty())
        {
            return "";
        }
        StringBuilder sb = new StringBuilder("【状态板（进入第 " + chapterNo + " 集时的既成事实）】\n");
        for (String line : board.split("\n"))
        {
            if (line.isEmpty())
            {
                continue;
            }
            // 本集及以后的状态不算「既成事实」：同集重跑时这些行来自上一版正文，
            // 会把人物提前推到本集结尾的位置，正是「上一集对话没接上」的成因。
            if (episodeOf(line) >= chapterNo)
            {
                continue;
            }
            if (sb.length() + line.length() > BLOCK_LIMIT)
            {
                sb.append("- ……（其余条目从略）\n");
                break;
            }
            sb.append("- ").append(line).append('\n');
        }
        sb.append("用法：以上是上一集结束时已经成立的状态，本集不得无因地改变。"
                + "需要改变时，用一句动作、台词或外部事件写清触发（谁把东西拿走、谁传话、隔了多久）；"
                + "约定可以不兑现，但要在本集内用一句话交代为什么没去或改到何时，不要让它凭空消失。"
                + "正文里不要复述本清单。");
        return sb.toString();
    }

    /**
     * 合并本集状态增量：同一条目（分类+主体）以新值为准，保留集号与因。
     *
     * @param existing 台账已有的状态板原文
     * @param delta    本集新增或变更的行，一行一条，可带或不带「[分类]」前缀
     * @param chapterNo 本集集号，用于给缺集号的行补上
     */
    public static String merge(String existing, String delta, int chapterNo)
    {
        Map<String, String> merged = new LinkedHashMap<>();
        for (String line : clean(existing).split("\n"))
        {
            if (line.isEmpty())
            {
                continue;
            }
            merged.put(key(line), line);
        }
        int added = 0;
        for (String raw : delta == null ? new String[0] : delta.split("\n"))
        {
            String line = cleanLine(raw);
            if (line.isEmpty() || line.length() < 6)
            {
                continue;
            }
            if (!line.startsWith("["))
            {
                line = "[认知] " + line;
            }
            if (!hasEpisode(line))
            {
                line = line + "｜第" + chapterNo + "集";
            }
            String key = key(line);
            if (key.isEmpty())
            {
                continue;
            }
            merged.remove(key);
            merged.put(key, truncate(line, 160));
            added++;
        }
        if (added == 0)
        {
            return join(merged);
        }
        return join(trimOldest(merged));
    }

    /**
     * 从验收返回的 JSON 里取 state_delta，转成一行一条的文本；没有则返回空串。
     *
     * 兼容两种写法：数组里是字符串，或数组里是 {kind, subject, value, trigger, episode} 对象。
     */
    public static String deltaText(JsonNode root)
    {
        if (root == null || root.isMissingNode())
        {
            return "";
        }
        JsonNode delta = root.path("state_delta");
        if (delta.isMissingNode() || !delta.isArray() || delta.size() == 0)
        {
            String alt = root.path("stateDelta").asText("");
            return clean(alt);
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode node : delta)
        {
            String line;
            if (node.isTextual())
            {
                line = node.asText("");
            }
            else
            {
                String kind = node.path("kind").asText(node.path("type").asText(""));
                String subject = node.path("subject").asText(node.path("who").asText(""));
                String value = node.path("value").asText(node.path("state").asText(""));
                String trigger = node.path("trigger").asText(node.path("reason").asText(""));
                StringBuilder one = new StringBuilder();
                if (!kind.isEmpty())
                {
                    one.append('[').append(kind).append("] ");
                }
                one.append(subject).append('｜').append(value);
                if (!trigger.isEmpty())
                {
                    one.append("｜因：").append(trigger);
                }
                line = one.toString();
            }
            line = cleanLine(line);
            if (!line.isEmpty())
            {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    /** 是否已有集号标记。 */
    private static boolean hasEpisode(String line)
    {
        return line.matches(".*第[一二三四五六七八九十百0-9]+集.*");
    }

    /** 合并键：分类 + 主体（「｜」前两段）。 */
    private static String key(String line)
    {
        String[] parts = line.split("｜|\\|");
        if (parts.length < 2)
        {
            return "";
        }
        String kind = segmentKind(parts[0]);
        String subject = parts[1].trim();
        if (subject.isEmpty())
        {
            return "";
        }
        return kind + "|" + subject;
    }

    /** 取「[所在] 顾长宁」里的分类，非闭集内的归为「认知」。 */
    private static String segmentKind(String head)
    {
        for (String kind : KINDS)
        {
            if (head.contains(kind))
            {
                return kind;
            }
        }
        return "认知";
    }

    /** 超出上限时按「第 N 集」升序淘汰最旧的条目。 */
    private static Map<String, String> trimOldest(Map<String, String> merged)
    {
        if (merged.size() <= MAX_LINES)
        {
            return merged;
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>(merged.entrySet());
        entries.sort((a, b) -> Integer.compare(episodeOf(a.getValue()), episodeOf(b.getValue())));
        Map<String, String> kept = new LinkedHashMap<>();
        int skip = entries.size() - MAX_LINES;
        for (int i = skip; i < entries.size(); i++)
        {
            kept.put(entries.get(i).getKey(), entries.get(i).getValue());
        }
        return kept;
    }

    /** 取行里的集号，取不到按 0（最早，优先淘汰）。 */
    private static int episodeOf(String line)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("第([一二三四五六七八九十百0-9]+)集").matcher(line);
        if (!matcher.find())
        {
            return 0;
        }
        String raw = matcher.group(1);
        if (raw.matches("[0-9]+"))
        {
            try
            {
                return Integer.parseInt(raw);
            }
            catch (NumberFormatException e)
            {
                return 0;
            }
        }
        int value = 0;
        for (char c : raw.toCharArray())
        {
            if (c == '百')
            {
                value = (value == 0 ? 1 : value) * 100;
            }
            else if (c == '十')
            {
                value = (value == 0 ? 1 : value) * 10;
            }
            else
            {
                int digit = "一二三四五六七八九".indexOf(c) + 1;
                if (digit > 0)
                {
                    value += digit;
                }
            }
        }
        return value;
    }

    private static String join(Map<String, String> merged)
    {
        StringBuilder sb = new StringBuilder();
        for (String line : merged.values())
        {
            if (sb.length() > 0)
            {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString();
    }

    /** 多行规整：去空行、去重、单行限长。 */
    private static String clean(String text)
    {
        if (text == null || text.trim().isEmpty())
        {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\\n"))
        {
            String line = cleanLine(raw);
            if (!line.isEmpty() && !lines.contains(line))
            {
                lines.add(truncate(line, 160));
            }
        }
        return String.join("\n", lines);
    }

    /** 单行规整：去首尾空白、去项目符号、全角竖线归一。 */
    private static String cleanLine(String raw)
    {
        if (raw == null)
        {
            return "";
        }
        String line = raw.trim().replaceFirst("^[-*·•]+\\s*", "").replace('|', '｜');
        while (line.contains("｜｜"))
        {
            line = line.replace("｜｜", "｜");
        }
        return line;
    }

    private static String truncate(String text, int limit)
    {
        return text.length() <= limit ? text : text.substring(0, limit);
    }
}
