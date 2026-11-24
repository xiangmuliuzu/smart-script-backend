package com.smartscript.platform.review.util;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import java.util.List;
import java.util.function.Supplier;

/**
 * 福利配置后台列表的分页工具。
 *
 * <p>前端 el-pagination 传的是 {@code page} / {@code pageSize}，PageHelper 认的是
 * {@code pageNum} / {@code pageSize}，两者对不上。这里统一收口：把 page 归一到 pageNum，
 * 并兜住非法值与上限，避免再出现「每页 10 条却显示全量」或者被传 pageSize=100000 拖垮库。</p>
 */
public final class AdminListPager {

    /** 单页上限，与 CreatorCopyrightAssetsController 等后台列表保持一致。 */
    public static final int MAX_PAGE_SIZE = 50;

    /** 前端未传时的默认每页条数。 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    private AdminListPager() {
    }

    /**
     * 在分页上下文中执行查询，并保证无论成功失败都清理线程变量。
     *
     * <p>不这样做的话，查询抛异常时 PageHelper 的 ThreadLocal 会残留，
     * 把本次的 page/pageSize 带到同一线程的**下一个请求**上——表现为某个请求莫名少查数据。</p>
     */
    public static <T> List<T> query(Integer page, Integer pageSize, Supplier<List<T>> loader) {
        PageHelper.startPage(normalizePage(page), normalizePageSize(pageSize));
        try {
            return loader.get();
        } finally {
            PageHelper.clearPage();
        }
    }

    /** 取总数：列表为空时返回 0，避免 PageInfo 在极端情况下的歧义。 */
    public static long total(List<?> rows) {
        return rows instanceof Page ? new PageInfo<>(rows).getTotal() : rows.size();
    }

    public static int normalizePage(Integer page) {
        return page != null && page > 0 ? page : 1;
    }

    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
