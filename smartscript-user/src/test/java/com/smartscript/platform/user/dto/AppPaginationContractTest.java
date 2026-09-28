package com.smartscript.platform.user.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.user.constant.AppAdminConstants;
import com.smartscript.platform.user.constant.AppPageConstants;

/**
 * H-12 分页契约（第 5 批加固新增）。
 *
 * 锁定两条容易被无意破坏的口径：
 *   1. App 端与 PC 管理端共用同一分页上限（1<=pageSize<=100），不出现两套边界。
 *   2. App {@link PageResult} 的 total 与 list 正交：即使当前页无数据（超出末页或空数据），
 *      total 也必须保留、list 必须是空集合而非 null——H-12 运行时矩阵 P10/P13 依赖该契约。
 */
class AppPaginationContractTest
{
    @Test
    void appAndPcShareTheSamePageSizeBoundary()
    {
        assertEquals(100, AppPageConstants.PAGE_SIZE_MAX);
        assertEquals(AppPageConstants.PAGE_SIZE_MAX, AppAdminConstants.PAGE_SIZE_MAX,
                "App 与 PC 必须共用同一分页上限，避免同一后端出现两套边界");
        assertEquals(1, AppPageConstants.DEFAULT_PAGE_NUM);
        assertEquals(10, AppPageConstants.DEFAULT_PAGE_SIZE);
    }

    @Test
    void emptyListStillKeepsTotal()
    {
        PageResult<String> beyondLastPage = PageResult.of(12L, List.of());
        assertEquals(12L, beyondLastPage.getTotal(), "超出末页时 total 必须保留");
        assertNotNull(beyondLastPage.getList());
        assertTrue(beyondLastPage.getList().isEmpty());

        PageResult<String> emptyData = PageResult.of(0L, List.of());
        assertEquals(0L, emptyData.getTotal());
        assertTrue(emptyData.getList().isEmpty());
    }

    @Test
    void nullListIsNormalisedToEmptyList()
    {
        PageResult<String> result = PageResult.of(5L, null);
        assertEquals(5L, result.getTotal());
        assertNotNull(result.getList(), "null 列表必须归一为空集合，避免调用方 NPE");
        assertTrue(result.getList().isEmpty());
    }
}
