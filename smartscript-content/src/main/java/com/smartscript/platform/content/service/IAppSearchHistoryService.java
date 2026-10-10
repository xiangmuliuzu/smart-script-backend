package com.smartscript.platform.content.service;

import com.smartscript.platform.content.dto.AppSearchHistoryListDto;

/**
 * 搜索历史服务（App 书城 2.7.4 列表 / 2.7.5 删除单条 / 2.7.6 清空）。
 *
 * 依据：接口文档 2.7 书城模块表 2-84/2-85/2-86 + 云端 script_platform_dev 库
 * sys_search_history 表（附件5.1 表3-30）。
 *
 * 边界：
 * 1. 归属一律取当前 App 登录身份（IdentityProvider），方法签名不接受 userId 入参，
 *    调用方无法读写他人历史。
 * 2. 文档未定义「写入搜索历史」接口，{@link #recordHistory} 为按表结构补充的接口，
 *    否则列表只能永远返回空集。
 *
 * @author xiangsipeng
 */
public interface IAppSearchHistoryService
{
    /**
     * 搜索历史列表（不分页，按最近搜索时间倒序取最近若干条）
     *
     * @return 列表载荷（仅 list，无 total，与契约输出一致）
     */
    public AppSearchHistoryListDto listHistory();

    /**
     * 记录一次搜索（文档未定义，按表结构补充）
     *
     * 同关键词已存在则 search_count 累加并刷新 last_search_at，否则新增一条。
     *
     * @param keyword 搜索关键词（调用方需先校验非空）
     * @return 影响行数
     */
    public int recordHistory(String keyword);

    /**
     * 删除单条搜索历史
     *
     * @param id 历史记录ID
     * @return true=删除成功；false=记录不存在或不属于当前用户
     */
    public boolean removeHistory(Long id);

    /**
     * 清空当前用户的全部搜索历史
     *
     * @return 删除条数（0 表示本来就没有历史）
     */
    public int clearHistory();
}