package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.github.pagehelper.Page;
import com.smartscript.platform.user.domain.admin.AppUserSummary;
import com.smartscript.platform.user.mapper.AppUserAdminMapper;

/**
 * H-12-01（2026-09-28 负责人选方案 A）：管理列表越界页必须保留筛选后的真实 total。
 *
 * 缺陷根因（第 21 批黑盒确证）：{@code AppUserAdminService.page} 对空结果返回
 * {@code new ArrayList<>()}，丢弃 PageHelper 的 Page 类型，Controller 的
 * {@code new PageInfo(list).getTotal()} 退化为 0；同批对照中 real-name/feedback 列表不受影响。
 *
 * 本用例为判别性测试：修复前 page() 返回普通 List（assertSame 与 total 断言必失败）。
 */
class AppUserAdminServicePageTotalTest
{
    @Test
    void emptyPageKeepsRealTotalForOutOfRangePage()
    {
        AppUserAdminMapper mapper = mock(AppUserAdminMapper.class);
        AppSessionRevocationService revocationService = mock(AppSessionRevocationService.class);
        // 模拟 PageHelper 在"越界页"下的返回：内容为空、但 count 查询给出真实总数
        Page<AppUserSummary> emptyPage = new Page<>(99999, 10);
        emptyPage.setTotal(42L);
        when(mapper.selectAppUserPage(anyMap())).thenReturn(emptyPage);

        AppUserAdminService service = new AppUserAdminService(mapper, revocationService);
        List<AppUserSummary> rows = service.page(new HashMap<>());

        assertSame(emptyPage, rows, "空结果必须原样透传 Page（保留 total），不得替换为普通 List");
        assertEquals(42L, ((Page<?>) rows).getTotal(), "越界页 total 必须是筛选后的真实总数");
    }
}
