package com.smartscript.platform.content.service;

import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppShelfWorkDto;

/**
 * 书架服务（App 书城 2.7.12）
 *
 * 语义：书架 = 「已购买 / 已获授权」的作品库，不再支持把普通作品加入书架。
 * 列表由订单完成态与生效授权派生，归属一律取当前登录身份。
 * 阅读进度（last_read_chapter_id / last_read_at）随列表下发，并由 {@link #saveProgress} 写入。
 *
 * @author xiangsipeng
 */
public interface IAppBookshelfService
{
    /**
     * 已购作品列表（分页）
     *
     * @param pageNum  页码（从 1 起）
     * @param pageSize 每页条数
     * @return 分页结果（total + list，list 元素为书城作品字段 + 阅读进度）
     */
    public AppPageResult<AppShelfWorkDto> pageShelf(int pageNum, int pageSize);

    /**
     * 当前用户是否已获得该作品授权（已购 / 生效授权）
     *
     * @param workId 作品ID
     * @return true=已购或授权生效中
     */
    public boolean isAuthorized(Long workId);

    /**
     * 记录阅读进度（首次阅读自动建立进度行，不要求作品预先在书架中）
     *
     * 不校验章节是否属于该作品（阅读页只上报当前章节）。
     *
     * @param workId    作品ID
     * @param chapterId 最近阅读章节ID
     * @return true=已记录；false=参数不完整
     */
    public boolean saveProgress(Long workId, Long chapterId);
}
