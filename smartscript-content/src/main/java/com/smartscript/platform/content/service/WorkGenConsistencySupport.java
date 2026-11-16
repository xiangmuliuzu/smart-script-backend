package com.smartscript.platform.content.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.smartscript.platform.content.domain.SysWorkLedger;

/**
 * 单集一致性守卫：从台账抽取「会被后文反复引用的量」，再在生成后做矛盾检测。
 *
 * 设计口径（对应《丹炉血》复测结论）：
 *   1. 只锁读者能算出来的账（件数、总人数、已作废日期、强状态），不管戏怎么演；
 *   2. 清单内容由台账自己长出来，字段不写死——抽到什么就校验什么，抽不到就跳过；
 *   3. 状态变了但有过渡事件（拖走/卸链/抬走/挣开…）一律放行，查因不查值；
 *   4. 自由区（情绪、语气、当场决定、人物误会）完全不校验。
 *
 * 纯静态、无外部依赖，便于单测。
 *
 * @author smartscript
 */
public final class WorkGenConsistencySupport
{
    /** 单集最多回报的冲突条数，避免刷屏与无谓重写 */
    private static final int MAX_ISSUES = 6;

    /** 硬事实注入块字数上限 */
    private static final int BLOCK_LIMIT = 900;

    /** 日期词：初八 / 廿八 / 二十八 / 第三天 */
    private static final Pattern DATE_WORD = Pattern.compile(
            "初[一二三四五六七八九十]|廿[一二三四五六七八九十]|[一二三]十[一二三四五六七八九]?|第[一二三四五六七八九十]+[天日]");

    /** 改期标记 */
    private static final Pattern CHANGE_MARK = Pattern.compile("改为|改作|改成|提前|推迟|延后|变更");

    /** 数量 + 量词 */
    private static final Pattern NUM_UNIT = Pattern.compile(
            "([0-9]+|[一二三四五六七八九十百两]+)\\s*(人|名|位|枚|件|面|本|只|口)");

    /** 总数前缀：只有带这些词的行才当作总账，避免把「每炉七人」误当总数 */
    private static final Pattern TOTAL_MARK = Pattern.compile("共|总|合计|一共|全书|总数");

    /** 台账道具行：名称｜件数｜… */
    private static final Pattern PROP_LINE = Pattern.compile("([^｜|\\n]{1,10})[｜|]\\s*([0-9]+|[一二三四五六七八九十百两]+)");

    /** 强状态词：只锁这些，情绪类状态不锁 */
    private static final Pattern STATUS_MARK = Pattern.compile("被压|压住|被锁|锁着|已死|身亡|死了|重伤|昏迷");

    /** 过渡词：同句出现即视为变更有因，放行 */
    private static final Pattern TRANSITION = Pattern.compile(
            "被拖|拖走|抬走|被抬|挣开|挣脱|卸链|换锁|爬出|撑起|扶起|救出|放出|被提|押走");

    /** 主动位移：与强状态冲突时才报 */
    private static final Pattern ACTIVE_MOVE = Pattern.compile("站起|起身|出门|上前|迈|跨|跑|走向|提刀|提剑");

    /** 句末切分 */
    private static final Pattern SENTENCE = Pattern.compile("[。！？\\n]");

    /** 表格类标题，抽姓名时跳过 */
    private static final Pattern HEADER_LINE = Pattern.compile("^【|^[\\u4e00-\\u9fa5]{0,6}(人物|关系|台账|道具|物品|时间线|伏笔|线索)[：:]");

    private WorkGenConsistencySupport()
    {
    }

    /**
     * 本集硬事实注入块：只列抽得到的条目，抽不到返回空串。
     *
     * 与 stateGuardBlock 的区别：那里是通用铁律，这里是这部戏自己的账。
     */
    public static String hardFactsBlock(SysWorkLedger ledger, int chapterNo)
    {
        Facts facts = extract(ledger);
        List<String> lines = new ArrayList<>();
        if (!facts.props.isEmpty())
        {
            StringBuilder sb = new StringBuilder("道具件数：");
            for (Prop prop : facts.props)
            {
                if (sb.length() > 6)
                {
                    sb.append("；");
                }
                sb.append(prop.name).append(' ').append(prop.count).append(" 件");
            }
            lines.add(sb.toString());
        }
        if (!facts.retiredDates.isEmpty())
        {
            StringBuilder sb = new StringBuilder("已作废日期：");
            for (Map.Entry<String, String> entry : facts.retiredDates.entrySet())
            {
                if (sb.length() > 6)
                {
                    sb.append("；");
                }
                sb.append(entry.getKey()).append("（已改为").append(entry.getValue()).append("，不得再用）");
            }
            lines.add(sb.toString());
        }
        if (!facts.statuses.isEmpty())
        {
            StringBuilder sb = new StringBuilder("强状态：");
            for (Status status : facts.statuses)
            {
                if (sb.length() > 4)
                {
                    sb.append("；");
                }
                sb.append(status.name).append('—').append(status.state);
            }
            lines.add(sb.toString());
        }
        if (facts.totalPeople > 0)
        {
            lines.add("全书总人数：" + facts.totalPeople + " 人（任何集提到总数必须与它一致）");
        }
        if (lines.isEmpty())
        {
            return "";
        }
        StringBuilder block = new StringBuilder("【硬事实清单（本集必须自洽；由台账自动抽取，非固定字段）】\n");
        for (String line : lines)
        {
            if (block.length() + line.length() > BLOCK_LIMIT)
            {
                break;
            }
            block.append("- ").append(line).append('\n');
        }
        block.append("以上任一项与正文冲突即判本集不合格；需要改变时，必须写清触发它的过渡事件（谁拖走、谁卸链、谁宣布改期）。");
        return block.toString();
    }

    /**
     * 生成后一致性检查：返回冲突描述，空列表表示通过。
     *
     * 只查会冲突的组合，不做全量比对，避免误杀正常发挥。
     */
    public static List<String> check(String text, SysWorkLedger ledger, int chapterNo)
    {
        List<String> issues = new ArrayList<>();
        if (text == null || text.trim().isEmpty())
        {
            return issues;
        }
        Facts facts = extract(ledger);
        checkRetiredDates(text, facts, issues);
        checkProps(text, facts, issues);
        checkStatuses(text, facts, issues);
        checkTotal(text, facts, issues);
        return issues.size() > MAX_ISSUES ? new ArrayList<>(issues.subList(0, MAX_ISSUES)) : issues;
    }

    // ------------------------------------------------------------------ 抽取

    private static Facts extract(SysWorkLedger ledger)
    {
        Facts facts = new Facts();
        if (ledger == null)
        {
            return facts;
        }
        collectProps(facts, ledger.getProps());
        collectDates(facts, ledger.getTimeline());
        collectDates(facts, ledger.getItems());
        collectStatuses(facts, ledger.getCharacters());
        collectStatuses(facts, ledger.getItems());
        collectTotal(facts, ledger.getProps());
        collectTotal(facts, ledger.getTimeline());
        return facts;
    }

    private static void collectProps(Facts facts, String props)
    {
        for (String raw : lines(props))
        {
            Matcher matcher = PROP_LINE.matcher(raw);
            if (!matcher.find())
            {
                continue;
            }
            String name = matcher.group(1).trim().replaceFirst("^[-*·]+", "");
            int count = number(matcher.group(2));
            if (name.isEmpty() || count <= 0 || count > 99 || facts.props.size() >= 8)
            {
                continue;
            }
            if (name.contains("名称") || name.contains("道具"))
            {
                continue;
            }
            // 台账常写「丹母（残）」，正文只写「丹母」；去括注后按基名合并计数
            String baseName = name.replaceAll("（[^）]*）|\\([^)]*\\)", "").trim();
            if (baseName.isEmpty())
            {
                baseName = name;
            }
            Prop same = null;
            for (Prop prop : facts.props)
            {
                if (prop.name.equals(baseName))
                {
                    same = prop;
                    break;
                }
            }
            if (same != null)
            {
                same.count += count;
            }
            else
            {
                facts.props.add(new Prop(baseName, count));
            }
        }
    }

    private static void collectDates(Facts facts, String text)
    {
        for (String raw : lines(text))
        {
            if (!CHANGE_MARK.matcher(raw).find())
            {
                continue;
            }
            List<String> dates = new ArrayList<>();
            Matcher matcher = DATE_WORD.matcher(raw);
            while (matcher.find() && dates.size() < 4)
            {
                if (!dates.contains(matcher.group()))
                {
                    dates.add(matcher.group());
                }
            }
            if (dates.size() >= 2 && facts.retiredDates.size() < 4)
            {
                facts.retiredDates.put(dates.get(0), dates.get(dates.size() - 1));
            }
        }
    }

    private static void collectStatuses(Facts facts, String text)
    {
        for (String raw : lines(text))
        {
            if (HEADER_LINE.matcher(raw).find())
            {
                continue;
            }
            Matcher stateMatcher = STATUS_MARK.matcher(raw);
            if (!stateMatcher.find())
            {
                continue;
            }
            String name = leadingName(raw);
            if (name.isEmpty() || facts.statuses.size() >= 4)
            {
                continue;
            }
            String state = raw.startsWith(name)
                    ? raw.substring(name.length()).replaceFirst("^[｜|：:\\s]+", "")
                    : raw;
            if (state.length() > 30)
            {
                state = state.substring(0, 30);
            }
            facts.statuses.add(new Status(name, state));
        }
    }

    private static void collectTotal(Facts facts, String text)
    {
        for (String raw : lines(text))
        {
            if (!TOTAL_MARK.matcher(raw).find())
            {
                continue;
            }
            Matcher matcher = NUM_UNIT.matcher(raw);
            while (matcher.find())
            {
                String unit = matcher.group(2);
                if (!"人".equals(unit) && !"名".equals(unit) && !"位".equals(unit))
                {
                    continue;
                }
                int value = number(matcher.group(1));
                if (value > facts.totalPeople && value <= 200)
                {
                    facts.totalPeople = value;
                }
            }
        }
    }

    /** 行首姓名：取开头 2-4 个汉字，跳过标题行。 */
    private static String leadingName(String raw)
    {
        String text = raw.trim().replaceFirst("^[-*·]+", "");
        int end = 0;
        while (end < text.length() && end < 4 && isChinese(text.charAt(end)))
        {
            end++;
        }
        if (end < 2)
        {
            return "";
        }
        String name = text.substring(0, end);
        // 「林砚在丹房……」这类行首是名字，但「十年前的封炉匠」不是，靠长度与常见前导词兜底
        for (String bad : new String[] {"主要", "人物", "道具", "物品", "时间", "伏笔", "线索", "关系", "其中"})
        {
            if (name.startsWith(bad))
            {
                return "";
            }
        }
        return name;
    }

    // ------------------------------------------------------------------ 校验

    private static void checkRetiredDates(String text, Facts facts, List<String> issues)
    {
        for (Map.Entry<String, String> entry : facts.retiredDates.entrySet())
        {
            for (String sentence : sentences(text))
            {
                if (!sentence.contains(entry.getKey()))
                {
                    continue;
                }
                if (sentence.contains("改") || sentence.contains("原定") || sentence.contains("本来")
                        || sentence.contains("作废") || sentence.contains("旧"))
                {
                    continue;
                }
                issues.add("日期：仍在用已作废的「" + entry.getKey() + "」，台账已改为「" + entry.getValue() + "」");
                break;
            }
        }
    }

    private static void checkProps(String text, Facts facts, List<String> issues)
    {
        for (Prop prop : facts.props)
        {
            // 两种语序都要覆盖：「丹母……两枚」与「两枚丹母」
            Pattern before = Pattern.compile(Pattern.quote(prop.name)
                    + "[^。！？\\n]{0,10}?([0-9]+|[一二三四五六七八九十两]+)\\s*(枚|件|面|本|只|口)");
            Pattern after = Pattern.compile("([0-9]+|[一二三四五六七八九十两]+)\\s*(枚|件|面|本|只|口)"
                    + "[^。！？\\n]{0,6}?" + Pattern.quote(prop.name));
            for (Pattern pattern : new Pattern[] { before, after })
            {
                Matcher matcher = pattern.matcher(text);
                while (matcher.find())
                {
                    int value = number(matcher.group(1));
                    if (value >= 2 && value != prop.count)
                    {
                        issues.add("道具：" + prop.name + "正文写「" + matcher.group(1) + matcher.group(2)
                                + "」，台账件数为 " + prop.count);
                        break;
                    }
                }
                if (!issues.isEmpty() && issues.get(issues.size() - 1).startsWith("道具：" + prop.name))
                {
                    break;
                }
            }
        }
    }

    private static void checkStatuses(String text, Facts facts, List<String> issues)
    {
        for (Status status : facts.statuses)
        {
            for (String sentence : sentences(text))
            {
                if (!sentence.contains(status.name))
                {
                    continue;
                }
                if (!ACTIVE_MOVE.matcher(sentence).find())
                {
                    continue;
                }
                if (TRANSITION.matcher(sentence).find())
                {
                    continue;
                }
                issues.add("人物状态：" + status.name + "台账记为「" + status.state
                        + "」，正文却让其自行行动且无过渡事件");
                break;
            }
        }
    }

    private static void checkTotal(String text, Facts facts, List<String> issues)
    {
        if (facts.totalPeople <= 0)
        {
            return;
        }
        for (String sentence : sentences(text))
        {
            if (!TOTAL_MARK.matcher(sentence).find() && !sentence.contains("还差"))
            {
                continue;
            }
            Matcher matcher = NUM_UNIT.matcher(sentence);
            while (matcher.find())
            {
                String unit = matcher.group(2);
                if (!"人".equals(unit) && !"名".equals(unit) && !"位".equals(unit))
                {
                    continue;
                }
                int value = number(matcher.group(1));
                if (value > 0 && value != facts.totalPeople && Math.abs(value - facts.totalPeople) > 2)
                {
                    issues.add("人数：正文出现「" + matcher.group(1) + unit + "」，与全书总账 "
                            + facts.totalPeople + " 人对不上");
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ 工具

    private static List<String> lines(String text)
    {
        List<String> result = new ArrayList<>();
        if (text == null)
        {
            return result;
        }
        for (String raw : text.split("\\n"))
        {
            String line = raw.trim();
            if (!line.isEmpty())
            {
                result.add(line);
            }
        }
        return result;
    }

    private static List<String> sentences(String text)
    {
        List<String> result = new ArrayList<>();
        for (String raw : SENTENCE.split(text))
        {
            String line = raw.trim();
            if (!line.isEmpty())
            {
                result.add(line);
            }
        }
        return result;
    }

    /** 中文或阿拉伯数字转 int；失败返回 -1。 */
    private static int number(String raw)
    {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty())
        {
            return -1;
        }
        if (text.matches("[0-9]+"))
        {
            try
            {
                return Integer.parseInt(text);
            }
            catch (NumberFormatException e)
            {
                return -1;
            }
        }
        int value = 0;
        for (char c : text.toCharArray())
        {
            if (c == '十')
            {
                value = (value == 0 ? 1 : value) * 10;
                continue;
            }
            int digit = chineseDigit(c);
            if (digit < 0)
            {
                return -1;
            }
            value += digit;
        }
        return value <= 0 ? -1 : value;
    }

    private static int chineseDigit(char c)
    {
        switch (c)
        {
            case '一':
                return 1;
            case '二':
                return 2;
            case '两':
                return 2;
            case '三':
                return 3;
            case '四':
                return 4;
            case '五':
                return 5;
            case '六':
                return 6;
            case '七':
                return 7;
            case '八':
                return 8;
            case '九':
                return 9;
            default:
                return -1;
        }
    }

    private static boolean isChinese(char c)
    {
        return c >= '\u4e00' && c <= '\u9fa5';
    }

    /** 抽取结果。 */
    private static final class Facts
    {
        private final List<Prop> props = new ArrayList<>();

        private final Map<String, String> retiredDates = new LinkedHashMap<>();

        private final List<Status> statuses = new ArrayList<>();

        private int totalPeople;
    }

    private static final class Prop
    {
        private final String name;

        private int count;

        private Prop(String name, int count)
        {
            this.name = name;
            this.count = count;
        }
    }

    private static final class Status
    {
        private final String name;

        private final String state;

        private Status(String name, String state)
        {
            this.name = name;
            this.state = state;
        }
    }
}
