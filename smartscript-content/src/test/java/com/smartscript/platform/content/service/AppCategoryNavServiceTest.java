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
 * 标签墙体系下作品只按标签归属，分类页导航只保留「频道 + 四维度标签」：
 *   - 频道 Tab 取自根分类且去掉「频道」后缀，入参非法时回落第一个频道；
 *   - 维度固定 hot/theme/role/plot，按 tag_type 平铺标签，不再有分类条目；
 *   - 无频道时 channels/dimensions 为空集合。
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
        when(tagMapper.selectAppTagList(isNull(), any(Long.class))).thenReturn(new ArrayList<>());
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

    private SysTag tag(long id, String name, String type, Long channelId, int useCount, int sort)
    {
        SysTag tag = new SysTag();
        tag.setTagId(id);
        tag.setTagName(name);
        tag.setTagType(type);
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
    @DisplayName("频道 Tab 取自根分类、去掉「频道」后缀；传入非法频道回落第一个")
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
    @DisplayName("四维度按 tag_type 平铺标签，条目类型恒为 tag")
    void dimensionsAreFlatTagGroups()
    {
        cat(146L, "男生频道", "channel", null, 1);
        List<SysTag> tags = List.of(
                tag(1L, "神医", "role", 146L, 5, 1),
                tag(2L, "都市", "theme", 146L, 4, 2),
                tag(3L, "复仇", "plot", null, 9, 3),
                tag(4L, "逆袭", "hot", 146L, 12, 4));
        when(tagMapper.selectAppTagList(isNull(), eq(146L))).thenReturn(tags);

        AppCategoryNavDto nav = service.getCategoryNav(146L);

        assertEquals(4, nav.getDimensions().size());
        for (String key : List.of("hot", "theme", "role", "plot"))
        {
            AppCategoryNavDto.Dimension dimension = dimension(nav, key);
            assertTrue(dimension.getGroups().size() <= 1);
            for (AppCategoryNavDto.Group group : dimension.getGroups())
            {
                for (AppCategoryNavDto.Item item : group.getItems())
                {
                    assertEquals("tag", item.getItemType());
                }
            }
        }

        AppCategoryNavDto.Dimension role = dimension(nav, "role");
        assertEquals(1, role.getGroups().size());
        assertEquals("神医", role.getGroups().get(0).getItems().get(0).getItemName());

        AppCategoryNavDto.Dimension theme = dimension(nav, "theme");
        assertEquals("都市", theme.getGroups().get(0).getItems().get(0).getItemName());

        AppCategoryNavDto.Dimension plot = dimension(nav, "plot");
        assertEquals("复仇", plot.getGroups().get(0).getItems().get(0).getItemName());

        AppCategoryNavDto.Dimension hot = dimension(nav, "hot");
        assertEquals("逆袭", hot.getGroups().get(0).getItems().get(0).getItemName());
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
