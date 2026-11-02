package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.smartscript.platform.content.domain.SysCategory;
import com.smartscript.platform.content.domain.SysTag;
import com.smartscript.platform.content.dto.AppCategoryNavDto;
import com.smartscript.platform.content.mapper.SysCategoryMapper;
import com.smartscript.platform.content.mapper.SysTagMapper;
import com.smartscript.platform.content.service.impl.AppBookstoreServiceImpl;

/**
 * 分类页导航（AppCategoryNavDto）服务层单元测试。
 *
 * 校验服务层职责，SQL 侧条件（status='0' 等）由 Mapper 分支保证：
 *   - 频道 Tab 取自根分类且去掉「频道」后缀，入参非法时回落第一个频道；
 *   - theme 维度以题材为分组标题、三级细分为 chip，无细分时以题材自身兜底；
 *   - role / plot 维度按 tag_type 分维度、group_name 分组；
 *   - hot 维度：标签取 use_count 降序前 12、题材取作品数降序前 8；
 *   - channel_id 为 NULL 的通用标签在两个频道都下发。
 */
class AppCategoryNavServiceTest
{
    private SysCategoryMapper categoryMapper;

    private SysTagMapper tagMapper;

    private AppBookstoreServiceImpl service;

    private final List<SysCategory> categories = new ArrayList<>();

    @BeforeEach
    void setUp()
    {
        categoryMapper = mock(SysCategoryMapper.class);
        tagMapper = mock(SysTagMapper.class);
        service = new AppBookstoreServiceImpl();
        ReflectionTestUtils.setField(service, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(service, "tagMapper", tagMapper);
        categories.clear();
        when(categoryMapper.selectCategoryList(any(SysCategory.class))).thenReturn(categories);
        when(tagMapper.selectAppTagList(isNull(), isNull(), any(Long.class))).thenReturn(new ArrayList<>());
    }

    private SysCategory cat(long id, String name, String type, Long parentId, int sort)
    {
        SysCategory row = new SysCategory();
        row.setCategoryId(id);
        row.setCategoryName(name);
        row.setCategoryType(type);
        row.setParentId(parentId);
        row.setSort(sort);
        row.setStatus("0");
        categories.add(row);
        return row;
    }

    private SysTag tag(long id, String name, String type, String group, Long channelId, int useCount, int sort)
    {
        SysTag tag = new SysTag();
        tag.setTagId(id);
        tag.setTagName(name);
        tag.setTagType(type);
        tag.setGroupName(group);
        tag.setChannelId(channelId);
        tag.setUseCount(useCount);
        tag.setSort(sort);
        return tag;
    }

    private static AppCategoryNavDto.Dimension dimension(AppCategoryNavDto nav, String key)
    {
        return nav.getDimensions().stream()
                .filter(d -> key.equals(d.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少维度：" + key));
    }

    @Test
    @DisplayName("频道 Tab 取自根分类、去掉「频道」后缀；issue 非法频道回落第一个")
    void channelsAreDerivedFromRoots()
    {
        cat(146L, "男生频道", "channel", null, 1);
        cat(147L, "女生频道", "channel", null, 2);

        AppCategoryNavDto nav = service.getCategoryNav(null);

        assertEquals(2, nav.getChannels().size());
        assertEquals("男生", nav.getChannels().get(0).getChannelName());
        assertEquals("女生", nav.getChannels().get(1).getChannelName());
        assertEquals(146L, nav.getChannelId());

        AppCategoryNavDto fallback = service.getCategoryNav(999L);
        assertEquals(146L, fallback.getChannelId());
    }

    @Test
    @DisplayName("theme 维度：题材为分组标题，细分为 chip；无细分时以题材自身兜底")
    void themeDimensionGroupsBySubCategories()
    {
        cat(146L, "男生频道", "channel", null, 1);
        cat(20L, "都市", "theme", 146L, 1);
        cat(31L, "都市异能", "work", 20L, 1);
        cat(32L, "都市重生", "work", 20L, 2);
        cat(21L, "悬疑", "theme", 146L, 2);

        AppCategoryNavDto nav = service.getCategoryNav(146L);
        AppCategoryNavDto.Dimension theme = dimension(nav, "theme");

        assertEquals(2, theme.getGroups().size());
        assertEquals("都市", theme.getGroups().get(0).getGroupName());
        assertEquals(2, theme.getGroups().get(0).getItems().size());
        assertEquals(31L, theme.getGroups().get(0).getItems().get(0).getItemId());
        assertEquals("category", theme.getGroups().get(0).getItems().get(0).getItemType());

        // 悬疑无细分，以题材自身作为条目，避免分组为空
        assertEquals("悬疑", theme.getGroups().get(1).getGroupName());
        assertEquals(1, theme.getGroups().get(1).getItems().size());
        assertEquals(21L, theme.getGroups().get(1).getItems().get(0).getItemId());
    }

    @Test
    @DisplayName("role / plot 维度按 group_name 分组，通用标签（channel_id=NULL）两个频道都下发")
    void tagDimensionsGroupByGroupName()
    {
        cat(146L, "男生频道", "channel", null, 1);
        cat(147L, "女生频道", "channel", null, 2);

        List<SysTag> general = List.of(
                tag(1L, "神医", "role", "人物身份", 146L, 5, 1),
                tag(2L, "大女主", "role", "人物身份", 147L, 6, 2),
                tag(3L, "复仇", "plot", "逆袭爽感", null, 9, 3));
        when(tagMapper.selectAppTagList(isNull(), isNull(), eq(146L)))
                .thenReturn(List.of(general.get(0), general.get(2)));
        when(tagMapper.selectAppTagList(isNull(), isNull(), eq(147L)))
                .thenReturn(List.of(general.get(1), general.get(2)));

        AppCategoryNavDto male = service.getCategoryNav(146L);
        AppCategoryNavDto.Dimension role = dimension(male, "role");
        assertEquals(1, role.getGroups().size());
        assertEquals("人物身份", role.getGroups().get(0).getGroupName());
        assertEquals("神医", role.getGroups().get(0).getItems().get(0).getItemName());
        assertEquals("tag", role.getGroups().get(0).getItems().get(0).getItemType());

        AppCategoryNavDto.Dimension plot = dimension(male, "plot");
        assertEquals("逆袭爽感", plot.getGroups().get(0).getGroupName());
        assertEquals("复仇", plot.getGroups().get(0).getItems().get(0).getItemName());

        AppCategoryNavDto female = service.getCategoryNav(147L);
        assertTrue(dimension(female, "role").getGroups().get(0).getItems().stream()
                .anyMatch(item -> "大女主".equals(item.getItemName())));
        assertTrue(dimension(female, "plot").getGroups().get(0).getItems().stream()
                .anyMatch(item -> "复仇".equals(item.getItemName())));
    }

    @Test
    @DisplayName("hot 维度：标签按 use_count 降序取前 12，题材按作品数降序取前 8")
    void hotDimensionIsLimited()
    {
        cat(146L, "男生频道", "channel", null, 1);
        List<SysTag> tags = new ArrayList<>();
        for (int i = 1; i <= 15; i++)
        {
            tags.add(tag(i, "标签" + i, "plot", "热门", null, i, i));
        }
        when(tagMapper.selectAppTagList(isNull(), isNull(), eq(146L))).thenReturn(tags);

        for (int i = 1; i <= 10; i++)
        {
            cat(100L + i, "题材" + i, "theme", 146L, i);
            when(categoryMapper.countWorkByCategoryId(100L + i)).thenReturn(i);
        }

        AppCategoryNavDto nav = service.getCategoryNav(146L);
        AppCategoryNavDto.Dimension hot = dimension(nav, "hot");

        AppCategoryNavDto.Group hotTags = hot.getGroups().get(0);
        assertEquals("热门标签", hotTags.getGroupName());
        assertEquals(12, hotTags.getItems().size());
        assertEquals("标签15", hotTags.getItems().get(0).getItemName());

        AppCategoryNavDto.Group hotThemes = hot.getGroups().get(1);
        assertEquals("热门题材", hotThemes.getGroupName());
        assertEquals(8, hotThemes.getItems().size());
        assertEquals("题材10", hotThemes.getItems().get(0).getItemName());
    }

    @Test
    @DisplayName("无频道时返回空 channels / dimensions，不抛异常")
    void emptyWhenNoChannel()
    {
        AppCategoryNavDto nav = service.getCategoryNav(null);

        assertNotNull(nav.getChannels());
        assertTrue(nav.getChannels().isEmpty());
        assertTrue(nav.getDimensions().isEmpty());
    }
}
