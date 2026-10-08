package com.smartscript.platform.content.service;

import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppWorkDto;

/**
 * 收藏服务（App 书城 2.7.10 / 2.7.11）
 *
 * 依据：接口文档表 2-90（收藏/取消收藏）、表 2-91（收藏列表）+
 * 云端 script_platform_dev 库 sys_favorite 表（附件5.1 表3-28）。
 *
 * 边界：归属一律取当前登录身份，写路径不接收 userId；收藏可见性与 2.7.2 作品详情同口径
 * （未上架 / 已删除 / 不存在均不可收藏）。sys_work.favorite_count 由库内触发器维护，
 * 本服务不写作品计数。
 *
 * @author xiangsipeng
 */
public interface IAppFavoriteService
{
    /**
     * 收藏列表（分页，接口 2.7.11）
     *
     * @param pageNum  页码（从 1 起）
     * @param pageSize 每页条数
     * @return 分页结果（total + list，list 元素与 2.7.1 作品列表一致）
     */
    public AppPageResult<AppWorkDto> pageFavorites(int pageNum, int pageSize);

    /**
     * 收藏（接口 2.7.10，幂等）
     *
     * @param workId 作品ID
     * @return true=收藏成功（含此前已收藏）；false=作品不存在/已删除/未上架，由控制层按 404 处理
     */
    public boolean favorite(Long workId);

    /**
     * 取消收藏（接口 2.7.10）
     *
     * 幂等：未收藏时同样视为成功，不区分记录是否存在。
     *
     * @param workId 作品ID
     */
    public void unfavorite(Long workId);

    /**
     * 是否已收藏（文档外补充接口，供详情页回显收藏态）
     *
     * @param workId 作品ID
     * @return true=当前用户已收藏该作品
     */
    public boolean isFavorited(Long workId);
}