package com.smartscript.platform.user.dto;

import java.util.List;

/**
 * A5 App 分页载荷（契约 §1.1）。
 *
 * App 用户域统一使用 A3 的 {@code {code, message, data}} 信封，
 * 分页信息放在 data 内，而不是改用若依 PC 侧的 {@code TableDataInfo}
 * （{@code {total, rows, code, msg}}）。两者服务不同凭证域，不互相覆盖。
 */
public class PageResult<T>
{
    private long total;
    private List<T> list;

    public PageResult()
    {
    }

    public PageResult(long total, List<T> list)
    {
        this.total = total;
        this.list = list;
    }

    public static <T> PageResult<T> of(long total, List<T> list)
    {
        return new PageResult<>(total, list == null ? List.of() : list);
    }

    public long getTotal()
    {
        return total;
    }

    public void setTotal(long total)
    {
        this.total = total;
    }

    public List<T> getList()
    {
        return list;
    }

    public void setList(List<T> list)
    {
        this.list = list;
    }
}
