package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.smartscript.platform.content.dto.AppRankingItem;
import com.smartscript.platform.content.dto.AppRankingResult;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.service.impl.AppBookstoreServiceImpl;

/**
 * 榜单服务层单元测试（方案 A：5 榜 + 时间窗口）。
 *
 * SQL 侧指标口径（窗口聚合 / 平均评分）由 MyBatis 分支实现，此处校验服务层职责：
 *   - 榜单类型白名单：5 类透传，非法值回落 view；
 *   - rankNo 按结果顺序从 1 起递增；
 *   - 返回体含 updatedAt（榜单更新时间）；
 *   - limit 边界收敛（下限默认 10、上限 50）。
 */
class AppRankingServiceTest
{
    private SysContentWorkMapper workMapper;
    private AppBookstoreServiceImpl service;

    @BeforeEach
    void setUp()
    {
        workMapper = mock(SysContentWorkMapper.class);
        service = new AppBookstoreServiceImpl();
        ReflectionTestUtils.setField(service, "workMapper", workMapper);
    }

    private AppRankingItem item(long workId)
    {
        AppRankingItem item = new AppRankingItem();
        item.setWorkId(workId);
        return item;
    }

    @Test
    @DisplayName("新书榜类型 new 透传，rankNo 从 1 起递增，updatedAt 非空")
    void newTypeIsAccepted()
    {
        when(workMapper.selectAppRankingList(eq("new"), eq(10)))
                .thenReturn(List.of(item(1001L), item(1002L), item(1003L)));

        AppRankingResult result = service.listRankings("new", 10);

        verify(workMapper).selectAppRankingList("new", 10);
        assertEquals(3, result.getList().size());
        assertEquals(1, result.getList().get(0).getRankNo());
        assertEquals(2, result.getList().get(1).getRankNo());
        assertEquals(3, result.getList().get(2).getRankNo());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    @DisplayName("非法类型回落 view（5 类白名单之外一律当热门榜）")
    void unknownTypeFallsBackToView()
    {
        when(workMapper.selectAppRankingList(eq("view"), eq(10))).thenReturn(List.of());

        service.listRankings("hottest", 10);

        verify(workMapper).selectAppRankingList("view", 10);
    }

    @Test
    @DisplayName("空/ null 类型回落 view")
    void blankTypeFallsBackToView()
    {
        when(workMapper.selectAppRankingList(eq("view"), eq(10))).thenReturn(List.of());

        service.listRankings(null, 10);
        service.listRankings("", 10);

        verify(workMapper, org.mockito.Mockito.times(2)).selectAppRankingList("view", 10);
    }

    @Test
    @DisplayName("limit 收敛：小于 1 取默认 10，超过上限夹取到 50")
    void limitIsClamped()
    {
        when(workMapper.selectAppRankingList(anyString(), eq(10))).thenReturn(List.of());
        when(workMapper.selectAppRankingList(anyString(), eq(50))).thenReturn(List.of());

        service.listRankings("view", 0);
        service.listRankings("view", 999);

        verify(workMapper).selectAppRankingList("view", 10);
        verify(workMapper).selectAppRankingList("view", 50);
    }
}
