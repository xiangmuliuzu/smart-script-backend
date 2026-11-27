package com.smartscript.platform.review.controller;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.smartscript.platform.review.domain.PointsRecord;
import com.smartscript.platform.review.mapper.PointsRecordMapper;
import com.smartscript.platform.review.service.PointsService;
import com.smartscript.platform.review.util.AdminListPager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 福利与积分配置页分页回归测试（PointsController）。
 *
 * <p>缺陷背景：/account/list 与 /record/list 原先没有接收 page / pageSize，前端传的值被
 * 静默丢弃——「每页 10 条」实际返回全表，total 也写成当前列表长度。</p>
 *
 * <p>断言方式：PageHelper 是查询前拦截、查询后清理，所以在 mock 被调用的**那一瞬间**
 * 抓取线程变量，才能证明 limit 确实按前端参数下发了；事后 verify 时它已经被清掉了。</p>
 */
class PointsControllerPaginationTest {

    private final PointsRecordMapper recordMapper = mock(PointsRecordMapper.class);
    private final PointsService pointsService = newServiceWith(recordMapper);
    private final PointsController controller = newControllerWith(pointsService);

    /** mapper 被调用瞬间的分页上下文。 */
    private final AtomicReference<Page<?>> pagedAtQuery = new AtomicReference<>();

    @AfterEach
    void clearThreadLocal() {
        PageHelper.clearPage();
    }

    @Test
    void recordList_把page与pageSize交给分页插件_而不是查全表() {
        stubMapperReturning(records(10));

        controller.recordList(new PointsRecord(), 2, 10);

        assertPage(2, 10);
    }

    @Test
    void 未传分页参数时按默认每页10条_不会退化成全表() {
        stubMapperReturning(records(10));

        controller.recordList(new PointsRecord(), null, null);

        assertPage(1, AdminListPager.DEFAULT_PAGE_SIZE);
    }

    @Test
    void 分页上限被强制收敛到50_防止pageSize被放大拖垮数据库() {
        stubMapperReturning(records(50));

        controller.recordList(new PointsRecord(), 1, 100000);

        assertPage(1, AdminListPager.MAX_PAGE_SIZE);
    }

    @Test
    void 翻页参数为0或负数时回到第一页() {
        stubMapperReturning(records(10));

        controller.recordList(new PointsRecord(), 0, -5);

        assertPage(1, AdminListPager.DEFAULT_PAGE_SIZE);
    }

    @Test
    void 查询结束后分页线程变量被清理() {
        stubMapperReturning(records(10));

        controller.recordList(new PointsRecord(), 1, 10);

        assertNull(PageHelper.getLocalPage(), "分页只能作用于本次查询，不能泄漏到下一个请求");
    }

    @Test
    void 查询抛异常时分页线程变量也要清理掉() {
        when(recordMapper.selectPointsRecordList(any(PointsRecord.class)))
                .thenThrow(new RuntimeException("db down"));

        assertThrows(RuntimeException.class,
                () -> controller.recordList(new PointsRecord(), 3, 20));

        assertNull(PageHelper.getLocalPage(),
                "异常路径若不清理，下一个请求会沿用这次的 page/pageSize");
    }

    @Test
    void 普通List没有总数时退回列表长度_空列表为0() {
        assertEquals(3L, AdminListPager.total(List.of(1, 2, 3)));
        assertEquals(0L, AdminListPager.total(List.of()));
    }

    @Test
    void 分页参数归一() {
        assertEquals(1, AdminListPager.normalizePage(0));
        assertEquals(1, AdminListPager.normalizePage(-3));
        assertEquals(1, AdminListPager.normalizePage(null));
        assertEquals(10, AdminListPager.normalizePageSize(null));
        assertEquals(10, AdminListPager.normalizePageSize(0));
        assertEquals(50, AdminListPager.normalizePageSize(100000));
        assertEquals(20, AdminListPager.normalizePageSize(20));
    }

    // ---- helpers ----

    private void stubMapperReturning(List<PointsRecord> rows) {
        when(recordMapper.selectPointsRecordList(any(PointsRecord.class)))
                .thenAnswer(invocation -> {
                    pagedAtQuery.set(PageHelper.getLocalPage());
                    return rows;
                });
    }

    private void assertPage(int expectedPageNum, int expectedPageSize) {
        Page<?> local = pagedAtQuery.get();
        assertNotNull(local, "查询时必须处在分页上下文中，否则就是查了全表");
        assertEquals(expectedPageNum, local.getPageNum());
        assertEquals(expectedPageSize, local.getPageSize());
    }

    private static PointsService newServiceWith(PointsRecordMapper mapper) {
        PointsService service = new PointsService();
        ReflectionTestUtils.setField(service, "pointsRecordMapper", mapper);
        return service;
    }

    private static PointsController newControllerWith(PointsService service) {
        PointsController c = new PointsController();
        ReflectionTestUtils.setField(c, "pointsService", service);
        return c;
    }

    private static List<PointsRecord> records(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    PointsRecord r = new PointsRecord();
                    r.setRecordId((long) (i + 1));
                    r.setChangeType("EARN");
                    r.setPoints(10);
                    return r;
                })
                .toList();
    }
}
