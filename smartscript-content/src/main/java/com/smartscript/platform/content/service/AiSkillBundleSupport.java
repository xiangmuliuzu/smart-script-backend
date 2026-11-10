package com.smartscript.platform.content.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 多文件 skill 包（整目录上传）的存储与注入支持。
 *
 * <p>存储格式：整目录文件按顺序拼成一段文本，每个文件以
 * {@code @@@FILE:<相对路径>@@@} 独占一行开头，下一行起为该文件原文。
 * 该格式可直接在 DB 里阅读，无需 JSON 解析，也不会因转义问题损坏代码块/表格。</p>
 *
 * <p>注入格式：{@link #toPrompt} 把整包拼成一段「Skill 包」说明 + 各文件正文，
 * 由 {@code AiSkillServiceImpl.resolveSkillContent} 返回，最终由
 * {@code AiServiceClient.appendSkill} 追加到 system prompt 末尾。
 * 由于本系统模型没有文件读取能力，prompt 内显式声明「全部文件已附上、无需读取外部文件」。</p>
 *
 * @author xiangsipeng
 */
public final class AiSkillBundleSupport
{
    /** 文件段起始标记（独占行首） */
    public static final String FILE_MARK = "@@@FILE:";

    /** 文件段标记结束符 */
    public static final String MARK_SUFFIX = "@@@";

    /** 注入 prompt 的字符上限（超出按文件顺序截断并注明） */
    public static final int MAX_PROMPT_CHARS = 50000;

    private AiSkillBundleSupport()
    {
    }

    /** skill 包内单个文件 */
    public static final class Entry
    {
        private final String path;

        private final String content;

        public Entry(String path, String content)
        {
            this.path = path;
            this.content = content;
        }

        public String getPath()
        {
            return path;
        }

        public String getContent()
        {
            return content;
        }
    }

    /** 文件集合 → 存储文本 */
    public static String format(List<Entry> entries)
    {
        StringBuilder sb = new StringBuilder();
        for (Entry entry : entries)
        {
            if (sb.length() > 0)
            {
                sb.append("\n");
            }
            sb.append(FILE_MARK).append(entry.getPath()).append(MARK_SUFFIX)
                    .append("\n").append(entry.getContent());
        }
        return sb.toString();
    }

    /** 存储文本 → 文件集合（无有效段时返回空列表，不抛异常） */
    public static List<Entry> parse(String tree)
    {
        List<Entry> list = new ArrayList<>();
        if (tree == null || tree.isBlank())
        {
            return list;
        }
        String current = null;
        StringBuilder buffer = new StringBuilder();
        for (String line : tree.split("\n", -1))
        {
            String path = matchPath(line);
            if (path != null)
            {
                if (current != null)
                {
                    list.add(new Entry(current, buffer.toString()));
                }
                current = path;
                buffer = new StringBuilder();
            }
            else if (current != null)
            {
                if (buffer.length() > 0)
                {
                    buffer.append("\n");
                }
                buffer.append(line);
            }
        }
        if (current != null)
        {
            list.add(new Entry(current, buffer.toString()));
        }
        return list;
    }

    /** 取入口文件内容：优先 SKILL.md，其次第一个文件；空包返回 null */
    public static String entryContent(String tree)
    {
        List<Entry> entries = parse(tree);
        for (Entry entry : entries)
        {
            String name = entry.getPath().toLowerCase();
            if (name.equals("skill.md") || name.endsWith("/skill.md"))
            {
                return entry.getContent().trim();
            }
        }
        return entries.isEmpty() ? null : entries.get(0).getContent().trim();
    }

    /**
     * 拼成注入 system prompt 的 skill 正文。
     *
     * @param skillName skill 名称（可空）
     * @param tree      存储格式的整包文本
     * @return 注入文本；包为空时返回 null
     */
    public static String toPrompt(String skillName, String tree)
    {
        List<Entry> entries = parse(tree);
        if (entries.isEmpty())
        {
            return null;
        }
        String name = (skillName == null || skillName.isBlank()) ? "未命名" : skillName.trim();
        StringBuilder sb = new StringBuilder();
        sb.append("【Skill 包：").append(name).append("｜共 ").append(entries.size()).append(" 个文件】\n");
        sb.append("本 skill 以目录形式提供，全部文件已按原顺序完整附在下方。你无需也无法读取外部文件、"
                + "路径或执行命令；请把下列各文件当作同一份 skill 的组成部分直接遵循："
                + "入口 SKILL.md 是总纲与工作流，references/ 下为对应专题细则，冲突时以 SKILL.md 为准。");
        sb.append("\n注意：本系统只回传纯文本。skill 中若要求「生成/交付 Word（.docx）等文件」，"
                + "请改为直接输出完整文本内容（小标题用层级标题、表格用 markdown 表格表达），"
                + "禁止声称已生成文件、禁止给出下载链接或本地保存路径。");

        int used = sb.length();
        int omitted = 0;
        for (Entry entry : entries)
        {
            String block = "\n\n===== 文件：" + entry.getPath() + " =====\n" + entry.getContent().trim();
            if (used + block.length() > MAX_PROMPT_CHARS)
            {
                omitted++;
                continue;
            }
            sb.append(block);
            used += block.length();
        }
        if (omitted > 0)
        {
            sb.append("\n\n【提示】受篇幅限制，本次未附上 ").append(omitted)
                    .append(" 个文件的内容，请优先按已给出的文件执行。");
        }
        return sb.toString();
    }

    /** 判断该行是否为文件段标记，是则返回路径 */
    private static String matchPath(String line)
    {
        if (line == null || !line.startsWith(FILE_MARK) || !line.endsWith(MARK_SUFFIX))
        {
            return null;
        }
        int len = line.length() - FILE_MARK.length() - MARK_SUFFIX.length();
        if (len <= 0)
        {
            return null;
        }
        String path = line.substring(FILE_MARK.length(), line.length() - MARK_SUFFIX.length());
        return path.isBlank() ? null : path;
    }
}
