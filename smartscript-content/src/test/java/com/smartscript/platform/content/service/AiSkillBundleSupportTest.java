package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.smartscript.platform.content.domain.AiSkill;
import com.smartscript.platform.content.mapper.AiSkillMapper;
import com.smartscript.platform.content.service.impl.AiSkillServiceImpl;

/**
 * 多文件 skill 包（整目录上传）单元测试。
 *
 * 覆盖：目录文件集合 → 存储文本 → 解析回原文的往返一致性；入口文件选取；
 * 注入 prompt 的包装声明（无需读取外部文件、docx 改为纯文本输出）；超长截断；
 * 以及 AiSkillServiceImpl.resolveSkillContent 对「多文件包 / 单文件 / 停用」三态的路由。
 */
class AiSkillBundleSupportTest
{
    private AiSkillMapper mapper;

    private AiSkillServiceImpl service;

    @BeforeEach
    void setUp() throws Exception
    {
        mapper = mock(AiSkillMapper.class);
        service = new AiSkillServiceImpl();
        setField(service, "aiSkillMapper", mapper);
    }

    private static List<AiSkillBundleSupport.Entry> sampleEntries()
    {
        List<AiSkillBundleSupport.Entry> list = new ArrayList<>();
        list.add(new AiSkillBundleSupport.Entry("shortdrama-storyboard/SKILL.md",
                "# 节奏分镜\n\n| 镜号 | 时长 |\n| --- | --- |\n| 1 | 3s |\n\n```text\n台词\n```\n"));
        list.add(new AiSkillBundleSupport.Entry("shortdrama-storyboard/references/01-八要素字段规则.md",
                "## 八要素\n1. 镜号\n2. 时长\n"));
        list.add(new AiSkillBundleSupport.Entry("shortdrama-storyboard/references/02-短剧节奏工程.md",
                "## 节奏\n- 前 3 秒钩子\n"));
        return list;
    }

    @Test
    @DisplayName("format→parse 往返：路径与内容（表格/代码块/中文）完整保留")
    void formatAndParse_roundTrip()
    {
        String tree = AiSkillBundleSupport.format(sampleEntries());
        List<AiSkillBundleSupport.Entry> parsed = AiSkillBundleSupport.parse(tree);

        assertEquals(3, parsed.size());
        assertEquals("shortdrama-storyboard/SKILL.md", parsed.get(0).getPath());
        assertEquals("shortdrama-storyboard/references/02-短剧节奏工程.md", parsed.get(2).getPath());
        assertTrue(parsed.get(0).getContent().contains("| 镜号 | 时长 |"));
        assertTrue(parsed.get(0).getContent().contains("```text"));
        assertTrue(parsed.get(1).getContent().contains("1. 镜号"));
        assertEquals(sampleEntries().get(1).getContent().trim(), parsed.get(1).getContent().trim());
    }

    @Test
    @DisplayName("parse：null / 空串 / 无标记文本 → 空列表，不抛异常")
    void parse_blankInput_returnsEmpty()
    {
        assertTrue(AiSkillBundleSupport.parse(null).isEmpty());
        assertTrue(AiSkillBundleSupport.parse("   ").isEmpty());
        assertTrue(AiSkillBundleSupport.parse("# 普通 markdown\n没有分段标记").isEmpty());
    }

    @Test
    @DisplayName("entryContent：优先取 SKILL.md（含子目录、大小写不敏感）")
    void entryContent_prefersSkillMd()
    {
        String tree = AiSkillBundleSupport.format(sampleEntries());
        String entry = AiSkillBundleSupport.entryContent(tree);
        assertTrue(entry.startsWith("# 节奏分镜"));
    }

    @Test
    @DisplayName("entryContent：无 SKILL.md 时取第一个文件；空包返回 null")
    void entryContent_fallback()
    {
        List<AiSkillBundleSupport.Entry> noSkill = new ArrayList<>();
        noSkill.add(new AiSkillBundleSupport.Entry("pkg/README.md", "# 说明"));
        assertEquals("# 说明", AiSkillBundleSupport.entryContent(AiSkillBundleSupport.format(noSkill)));
        assertNull(AiSkillBundleSupport.entryContent(null));
        assertNull(AiSkillBundleSupport.entryContent(""));
    }

    @Test
    @DisplayName("toPrompt：声明整包已附上、无需读取外部文件，并约束不得声称生成文件")
    void toPrompt_declaresBundleAndPlainText()
    {
        String prompt = AiSkillBundleSupport.toPrompt("节奏分镜", AiSkillBundleSupport.format(sampleEntries()));
        assertTrue(prompt.contains("【Skill 包：节奏分镜｜共 3 个文件】"));
        assertTrue(prompt.contains("无需也无法读取外部文件"));
        assertTrue(prompt.contains("禁止声称已生成文件"));
        assertTrue(prompt.contains("===== 文件：shortdrama-storyboard/SKILL.md ====="));
        assertTrue(prompt.contains("===== 文件：shortdrama-storyboard/references/01-八要素字段规则.md ====="));
        assertFalse(prompt.contains("受篇幅限制"));
    }

    @Test
    @DisplayName("toPrompt：空包返回 null；skillName 为空时用默认名")
    void toPrompt_edgeCases()
    {
        assertNull(AiSkillBundleSupport.toPrompt("空包", null));
        String prompt = AiSkillBundleSupport.toPrompt(null, AiSkillBundleSupport.format(sampleEntries()));
        assertTrue(prompt.contains("【Skill 包：未命名｜共 3 个文件】"));
    }

    @Test
    @DisplayName("toPrompt：超过字符上限时截断，并注明未附上的文件数")
    void toPrompt_overLimit_truncated()
    {
        List<AiSkillBundleSupport.Entry> big = new ArrayList<>();
        String body = "规".repeat(6000);
        for (int i = 0; i < 12; i++)
        {
            big.add(new AiSkillBundleSupport.Entry("pkg/references/0" + i + ".md", body));
        }
        String prompt = AiSkillBundleSupport.toPrompt("超大包", AiSkillBundleSupport.format(big));
        assertTrue(prompt.contains("受篇幅限制，本次未附上"));
        // 头部声明 + 已纳入的文件块不超过上限（留出提示语余量）
        assertTrue(prompt.length() < AiSkillBundleSupport.MAX_PROMPT_CHARS + 500);
    }

    @Test
    @DisplayName("resolveSkillContent：多文件包返回拼装 prompt（含各文件路径）")
    void resolveSkillContent_bundle()
    {
        AiSkill skill = new AiSkill();
        skill.setId(9L);
        skill.setSkillName("节奏分镜");
        skill.setSkillType("storyboard");
        skill.setStatus("0");
        skill.setFileTree(AiSkillBundleSupport.format(sampleEntries()));
        when(mapper.selectSkillById(9L)).thenReturn(skill);

        String content = service.resolveSkillContent(9L);
        assertTrue(content.contains("【Skill 包：节奏分镜｜共 3 个文件】"));
        assertTrue(content.contains("references/02-短剧节奏工程.md"));
    }

    @Test
    @DisplayName("resolveSkillContent：单文件 skill 仍返回原文（不带包包装）")
    void resolveSkillContent_singleFile()
    {
        AiSkill skill = new AiSkill();
        skill.setId(3L);
        skill.setSkillName("悬疑烧脑导演");
        skill.setStatus("0");
        skill.setFileContent("  # 导演 Skill：悬疑烧脑\n1. 悬念先行  ");
        when(mapper.selectSkillById(3L)).thenReturn(skill);

        String content = service.resolveSkillContent(3L);
        assertEquals("# 导演 Skill：悬疑烧脑\n1. 悬念先行", content);
        assertFalse(content.contains("Skill 包"));
    }

    @Test
    @DisplayName("resolveSkillContent：停用 / 不存在 / 空 id 均返回 null")
    void resolveSkillContent_disabledOrMissing()
    {
        AiSkill disabled = new AiSkill();
        disabled.setId(5L);
        disabled.setStatus("1");
        disabled.setFileTree(AiSkillBundleSupport.format(sampleEntries()));
        when(mapper.selectSkillById(5L)).thenReturn(disabled);

        assertNull(service.resolveSkillContent(5L));
        assertNull(service.resolveSkillContent(404L));
        assertNull(service.resolveSkillContent(null));
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
