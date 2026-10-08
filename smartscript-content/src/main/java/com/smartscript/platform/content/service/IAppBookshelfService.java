package com.smartscript.platform.content.service;

import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppWorkDto;

/**
 * 书架服务（App 书城 2.7.12 书架管理）
 *
 * 依据：接口文档表 2-92（书架列表 / 加入书架 / 移出书架）+
 * 云端 script_platform_dev 库 sys_bookshelf_record 表（附件5.1 表3-32）。
 *
 * 边界：归属一律取当前登录身份，写路径不接收 userId；书架可见性与 2.7.2 作品详情同口径
 * （未上架 / 已删除 / 不存在均不可加入）。移出书架为物理删除，与 sys_favorite 同一口径。
 *
 * @author xiangsipeng
 */
public interface IAppBookshelfService
{
    /**
     * 书架列表（分页，接口 2.7.12 GET）
     *
     * @param pageNum  页码（从 1 起）
     * @param pageSize 每页条数
     * @return 分页结果（total + list，list 元素与 2.7.1 作品列表一致）
     */
    public AppPageResult<AppWorkDto> pageShelf(int pageNum, int pageSize);

    /**
     * 加入书架（接口 2.7.12 POST，幂等：重复加入不产生重复记录）
     *
     * @param workId    作品ID
     * @param addSource 加入来源（接口文档 shelf_type，为空时缺省 app）
     * @return true=加入成功（含此前已在书架）；false=作品不存在/已删除/未上架，由控制层按 404 处理
     */
    public boolean addShelf(Long workId, String addSource);

    /**
     * 移出书架（接口 2.7.12 DELETE，物理删除）
     *
     * 幂等：不在书架时同样视为成功，不区分记录是否存在。
     *
     * @param workId 作品ID
     */
    public void removeShelf(Long workId);

    /**
     * 是否已在书架（文档未单列，补充接口，供详情页回显书架态）
     *
     * @param workId 作品ID
     * @return true=当前用户书架中已有该作品
     */
    public boolean isOnShelf(Long workId);
}