package com.smartscript.platform.content.dto;

import java.util.List;

/**
 * B 模块 App 侧分页载荷（接口文档 1.2：{code, message, data:{total, list}}）。
 *
 * 说明：结构与 A5 用户模块的 {@code PageResult} 相同，
 * 但刻意不跨模块复用——smartscript-content 不依赖 smartscript-user，
 * 避免业务模块之间产生编译期反向耦合（见模块 pom 说明）。
 *
 * @author xiangsipeng
 */
public class AppPageResult<T>
{
    /** 总条数 */
    private long total;

    /** 当前页数据 */
    private List<T> list;

    public AppPageResult()
    {
    }

    public AppPageResult(long total, List<T> list)
    {
        this.total = total;
        this.list = list;
    }

    public static <T> AppPageResult<T> of(long total, List<T> list)
    {
        return new AppPageResult<>(total, list == null ? List.of() : list);
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
