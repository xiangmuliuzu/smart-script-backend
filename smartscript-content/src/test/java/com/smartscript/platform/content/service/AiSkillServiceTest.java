package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.content.domain.AiSkill;
import com.smartscript.platform.content.mapper.AiSkillMapper;
import com.smartscript.platform.content.service.impl.AiSkillServiceImpl;
import com.smartscript.platform.content.service.impl.AiWriteServiceImpl;

/**
 * AI Skill（导演风格 skill）配置服务单元测试（E 模块）。
 *
 * 验证：名称唯一校验（新增重复抛 IllegalArgumentException、修改排除自身）、
 * 启停用/正常 skill 轻量列表、resolveSkillContent（不存在/停用/正常 三态）。
 */
class AiSkillServiceTest
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

    @Test
    @DisplayName("新增：名称唯一校验，重复抛 IllegalArgumentException")
    void insertSkill_duplicateName_throws()
    {
        AiSkill hit = new AiSkill();
        hit.setId(1L);
        hit.setSkillName("悬疑烧脑导演");
        when(mapper.checkSkillNameUnique("悬疑烧脑导演", null)).thenReturn(hit);

        AiSkill skill = new AiSkill();
        skill.setSkillName("悬疑烧脑导演");

        assertThrows(IllegalArgumentException.class, () -> service.insertSkill(skill));
    }

    @Test
    @DisplayName("新增：名称不重复时落库并回填 id")
    void insertSkill_ok()
    {
        when(mapper.checkSkillNameUnique("甜宠爆款导演", null)).thenReturn(null);
        AiSkill skill = new AiSkill();
        skill.setSkillName("甜宠爆款导演");
        service.insertSkill(skill);
        verify(mapper).insertSkill(skill);
    }

    @Test
    @DisplayName("修改：名称重复但为自身时放行（排除自身）")
    void updateSkill_sameNameSelf_ok()
    {
        when(mapper.checkSkillNameUnique("悬疑烧脑导演", 1L)).thenReturn(null);
        AiSkill skill = new AiSkill();
        skill.setId(1L);
        skill.setSkillName("悬疑烧脑导演");
        service.updateSkill(skill);
        verify(mapper).updateSkill(skill);
    }

    @Test
    @DisplayName("修改：名称被其它 skill 占用时抛异常")
    void updateSkill_occupiedName_throws()
    {
        AiSkill other = new AiSkill();
        other.setId(2L);
        other.setSkillName("甜宠爆款导演");
        when(mapper.checkSkillNameUnique("甜宠爆款导演", 1L)).thenReturn(other);

        AiSkill skill = new AiSkill();
        skill.setId(1L);
        skill.setSkillName("甜宠爆款导演");

        assertThrows(IllegalArgumentException.class, () -> service.updateSkill(skill));
    }

    @Test
    @DisplayName("删除：物理删除委托 mapper")
    void deleteSkill_ok()
    {
        service.deleteSkillById(3L);
        verify(mapper).deleteSkillById(3L);
    }

    @Test
    @DisplayName("列表：列表查询与正常 skill 轻量列表委托 mapper")
    void list_ok()
    {
        AiSkill query = new AiSkill();
        query.setStatus("0");
        service.selectSkillList(query);
        verify(mapper).selectSkillList(query);

        service.selectEnabledSkillBrief();
        verify(mapper).selectEnabledSkillBrief();
    }

    @Test
    @DisplayName("resolveSkillContent：不存在返回 null")
    void resolveContent_missing_null()
    {
        when(mapper.selectSkillById(99L)).thenReturn(null);
        assertNull(service.resolveSkillContent(99L));
    }

    @Test
    @DisplayName("resolveSkillContent：已停用返回 null（不影响既有调用）")
    void resolveContent_disabled_null()
    {
        AiSkill skill = new AiSkill();
        skill.setId(1L);
        skill.setStatus("1");
        skill.setFileContent("导演要求");
        when(mapper.selectSkillById(1L)).thenReturn(skill);
        assertNull(service.resolveSkillContent(1L));
    }

    @Test
    @DisplayName("resolveSkillContent：正常且非空返回 trim 后的内容")
    void resolveContent_enabled_ok()
    {
        AiSkill skill = new AiSkill();
        skill.setId(1L);
        skill.setStatus("0");
        skill.setFileContent("  悬念先行、逻辑闭环。  ");
        when(mapper.selectSkillById(1L)).thenReturn(skill);
        assertEquals("悬念先行、逻辑闭环。", service.resolveSkillContent(1L));
    }

    @Test
    @DisplayName("resolveSkillContent：内容为空返回 null")
    void resolveContent_blank_null()
    {
        AiSkill skill = new AiSkill();
        skill.setId(1L);
        skill.setStatus("0");
        skill.setFileContent("   ");
        when(mapper.selectSkillById(1L)).thenReturn(skill);
        assertNull(service.resolveSkillContent(1L));
    }

    @Test
    @DisplayName("listEnabledSkills：正常 skill 转为 {id, skillName, skillType, fileName}（无 fileContent）")
    void briefMapping_ok() throws Exception
    {
        AiSkill a = new AiSkill();
        a.setId(1L);
        a.setSkillName("悬疑烧脑导演");
        a.setSkillType("write");
        a.setFileName("悬疑烧脑导演.skill.md");
        com.smartscript.platform.content.service.IAiSkillService skillService =
                mock(com.smartscript.platform.content.service.IAiSkillService.class);
        when(skillService.selectEnabledSkillBrief()).thenReturn(List.of(a));
        AiWriteServiceImpl writeService = new AiWriteServiceImpl();
        setField(writeService, "aiSkillService", skillService);

        java.util.List<java.util.Map<String, Object>> list = writeService.listEnabledSkills();
        assertEquals(1, list.size());
        assertEquals(1L, list.get(0).get("id"));
        assertEquals("悬疑烧脑导演", list.get(0).get("skillName"));
        assertEquals("write", list.get(0).get("skillType"));
        assertEquals("悬疑烧脑导演.skill.md", list.get(0).get("fileName"));
        assertTrue(!list.get(0).containsKey("fileContent"));
    }

    @Test
    @DisplayName("listEnabledSkills：skillType 为空时兜底为 write")
    void briefMapping_typeFallback_ok() throws Exception
    {
        AiSkill a = new AiSkill();
        a.setId(2L);
        a.setSkillName("分镜导演");
        a.setSkillType(null);
        a.setFileName("分镜.skill.md");
        com.smartscript.platform.content.service.IAiSkillService skillService =
                mock(com.smartscript.platform.content.service.IAiSkillService.class);
        when(skillService.selectEnabledSkillBrief()).thenReturn(List.of(a));
        AiWriteServiceImpl writeService = new AiWriteServiceImpl();
        setField(writeService, "aiSkillService", skillService);

        java.util.List<java.util.Map<String, Object>> list = writeService.listEnabledSkills();
        assertEquals("write", list.get(0).get("skillType"));
    }

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
