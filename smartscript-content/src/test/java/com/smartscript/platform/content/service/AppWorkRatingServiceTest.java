package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.dto.AppWorkRatingDto;
import com.smartscript.platform.content.mapper.AppWorkRatingMapper;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.service.impl.AppWorkRatingServiceImpl;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 读者评分通道服务层单元测试。
 *
 * 校验点与 spec 对齐：可见性口径（未上架/不存在 → null 且不落库）、
 * 一人一评覆盖（upsert 一次、评分数不增长）、平均分回写后返回最新值、
 * 无评分时 rating 回落 0。
 */
class AppWorkRatingServiceTest
{
    private static final Long USER_ID = 7L;
    private static final Long WORK_ID = 1001L;

    private AppWorkRatingMapper ratingMapper;
    private SysContentWorkMapper workMapper;
    private IdentityProvider identityProvider;
    private AppWorkRatingServiceImpl service;

    @BeforeEach
    void setUp()
    {
        ratingMapper = mock(AppWorkRatingMapper.class);
        workMapper = mock(SysContentWorkMapper.class);
        identityProvider = mock(IdentityProvider.class);
        service = new AppWorkRatingServiceImpl();
        ReflectionTestUtils.setField(service, "ratingMapper", ratingMapper);
        ReflectionTestUtils.setField(service, "workMapper", workMapper);
        ReflectionTestUtils.setField(service, "identityProvider", identityProvider);
        when(identityProvider.currentUserId()).thenReturn(USER_ID);
    }

    private AppWorkDto visibleWork(String rating)
    {
        AppWorkDto work = new AppWorkDto();
        work.setWorkId(WORK_ID);
        work.setRating(new BigDecimal(rating));
        return work;
    }

    @Test
    @DisplayName("作品未上架/不存在：读评分态返回 null")
    void getRatingOfWorkNotVisible()
    {
        when(workMapper.selectAppWorkById(WORK_ID)).thenReturn(null);
        assertNull(service.getRating(WORK_ID));
        assertNull(service.getRating(null));
    }

    @Test
    @DisplayName("读评分态：平均分取 sys_work.rating，评分数取记录数，我的评分取当前身份分值")
    void getRatingOfVisibleWork()
    {
        when(workMapper.selectAppWorkById(WORK_ID)).thenReturn(visibleWork("4.5"));
        when(ratingMapper.countByWork(WORK_ID)).thenReturn(2);
        when(ratingMapper.selectMyScore(USER_ID, WORK_ID)).thenReturn(5);

        AppWorkRatingDto dto = service.getRating(WORK_ID);

        assertEquals(WORK_ID, dto.getWorkId());
        assertEquals(0, dto.getRating().compareTo(new BigDecimal("4.5")));
        assertEquals(2, dto.getRatingCount());
        assertEquals(5, dto.getMyScore());
    }

    @Test
    @DisplayName("无评分：平均分回落 0，我的评分为 null")
    void getRatingWhenNobodyRated()
    {
        when(workMapper.selectAppWorkById(WORK_ID)).thenReturn(visibleWork("0.0"));
        when(ratingMapper.countByWork(WORK_ID)).thenReturn(0);
        when(ratingMapper.selectMyScore(USER_ID, WORK_ID)).thenReturn(null);

        AppWorkRatingDto dto = service.getRating(WORK_ID);

        assertEquals(0, dto.getRating().compareTo(BigDecimal.ZERO));
        assertEquals(0, dto.getRatingCount());
        assertNull(dto.getMyScore());
    }

    @Test
    @DisplayName("作品不可见：提交评分不落库，返回 null")
    void rateOfWorkNotVisible()
    {
        when(workMapper.selectAppWorkById(WORK_ID)).thenReturn(null);
        assertNull(service.rate(WORK_ID, 5));
        verify(ratingMapper, never()).upsertRating(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("提交评分：upsert + 回写平均分，返回回写后的最新分值与我的评分")
    void rateWritesAggregate()
    {
        when(workMapper.selectAppWorkById(WORK_ID))
                .thenReturn(visibleWork("0.0"), visibleWork("8.0"));
        when(ratingMapper.countByWork(WORK_ID)).thenReturn(1);

        // 每星 2 分：4 星 = 8 分
        AppWorkRatingDto dto = service.rate(WORK_ID, 8);

        verify(ratingMapper).upsertRating(USER_ID, WORK_ID, 8);
        verify(ratingMapper).refreshWorkRating(WORK_ID);
        assertEquals(0, dto.getRating().compareTo(new BigDecimal("8.0")));
        assertEquals(1, dto.getRatingCount());
        assertEquals(8, dto.getMyScore());
    }

    @Test
    @DisplayName("覆盖提交：仍只写一行，评分数不因重复评分增长")
    void rateOverwritesExisting()
    {
        when(workMapper.selectAppWorkById(WORK_ID)).thenReturn(visibleWork("8.0"));
        when(ratingMapper.countByWork(WORK_ID)).thenReturn(1);

        // 3 星 = 6 分
        AppWorkRatingDto dto = service.rate(WORK_ID, 6);

        // 唯一键 uk_work_user 落覆盖更新，故 upsert 只调用一次，评分数保持 1
        verify(ratingMapper).upsertRating(USER_ID, WORK_ID, 6);
        assertEquals(1, dto.getRatingCount());
        assertEquals(6, dto.getMyScore());
    }
}
