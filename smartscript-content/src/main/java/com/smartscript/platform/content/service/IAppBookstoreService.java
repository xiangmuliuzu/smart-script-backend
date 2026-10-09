package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysBanner;
import com.smartscript.platform.content.domain.SysCategory;
import com.smartscript.platform.content.domain.SysTag;
import com.smartscript.platform.content.dto.AppChapterDetailDto;
import com.smartscript.platform.content.dto.AppChapterListDto;
import com.smartscript.platform.content.dto.AppContactDto;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppPreviewDto;
import com.smartscript.platform.content.dto.AppRankingItem;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.dto.AppWorkQuery;

/**
 * 书城浏览（App 侧只读）服务层
 *
 * 依据：接口文档 2.7 书城模块（表 2-81 作品列表 / 2-82 作品详情 / 2-87 作品榜单 /
 * 2-93 Banner 轮播）+ 云端 script_platform_dev 库真实表结构。
 *
 * 边界：本服务只做只读查询，不写库；书城可见性统一收敛为
 * 「未删除 + 已上架」/「status 正常」，避免调用方各自拼条件产生口径漂移。
 * 复用既有 Mapper（分类/标签复用管理端语句，作品/Banner 走 App 专用语句），不新建表访问路径。
 *
 * @author xiangsipeng
 */
public interface IAppBookstoreService
{
    /**
     * 首页 Banner 轮播列表
     *
     * @param position 展示位置（可选，如 home_top）
     * @return Banner 集合（status='on' 且在展示时间窗内，按 sort_order 升序）
     */
    public List<SysBanner> listBanners(String position);

    /**
     * 分类列表（status 正常的分类）
     *
     * @param categoryType 分类类型（可选）
     * @param parentId     父分类ID（可选，传 0 取顶级分类）
     * @return 分类集合（按 sort 升序）
     */
    public List<SysCategory> listCategories(String categoryType, Long parentId);

    /**
     * 标签列表（status 正常的标签）
     *
     * @param tagType    标签类型（可选）
     * @param categoryId 分类ID（可选，按分类收敛标签，接口文档表 2-126）
     * @return 标签集合（按 use_count 降序）
     */
    public List<SysTag> listTags(String tagType, Long categoryId);

    /**
     * 书城作品列表（分页）
     *
     * @param query    查询条件（keyword/categoryId/tagId/sort）
     * @param pageNum  页码（从 1 起）
     * @param pageSize 每页条数
     * @return 分页结果（total + list）
     */
    public AppPageResult<AppWorkDto> pageWorks(AppWorkQuery query, int pageNum, int pageSize);

    /**
     * 书城作品详情
     *
     * @param workId 作品ID
     * @return 作品（未上架/已删除/不存在返回 null）
     */
    public AppWorkDto getWork(Long workId);

    /**
     * 作品榜单
     *
     * @param type  榜单类型：view（默认）/favorite/sale/rating
     * @param limit 取前 N 条
     * @return 榜单条目（rankNo 从 1 起）
     */
    public List<AppRankingItem> listRankings(String type, int limit);

    /**
     * 作品章节目录（接口 2.7.8 免费试读）
     *
     * 只取 status 正常（'0'）的章节，按 chapter_no 升序；不下发 content 全文。
     * 逐章 readable 与整体 accessScope 按当前请求身份确定：
     * 已获授权（sys_copyright_authorization 生效记录）为全文，否则仅试读范围。
     *
     * @param work 作品（由 {@link #getWork} 取得，非空；调用方需先判定可见性）
     * @return 目录载荷（accessScope/unlocked + 逐章 readable；work 为空时返回空载荷）
     */
    public AppChapterListDto getChapterCatalog(AppWorkDto work);

    /**
     * 章节正文（接口 2.7.8 免费试读）
     *
     * 章节不存在、或所属作品未上架/已删除时返回 null（由控制层按 404 处理）；
     * 不可读（既不在试读范围内、当前身份也未获授权）时返回 readable=false 且
     * content 为 null（由控制层按 403 处理）。
     *
     * @param chapterId 章节ID
     * @return 章节正文对象；不可定位时返回 null
     */
    public AppChapterDetailDto getChapterContent(Long chapterId);

    /**
     * 免费试读（接口 2.7.8）
     *
     * 本接口语义固定为「免费试读范围」，不因用户已获授权而扩大为全文
     * （全文阅读走 {@link #getChapterCatalog} / {@link #getChapterContent}）。
     *
     * @param work 作品（由 {@link #getWork} 取得，非空）
     * @return 试读载荷（试读范围内可读章节子集 + 可预览文件）
     */
    public AppPreviewDto getPreview(AppWorkDto work);

    /**
     * 版权合作联系方式（接口 2.7.9）
     *
     * 按作品作者（sys_work.author_id → sys_contact_profile.owner_id）查联系方式档案，
     * 只下发「是否登记」与「展示范围」，不下发明文联系方式。
     *
     * @param work 作品（由 {@link #getWork} 取得，非空；调用方需先判定可见性）
     * @return 联系方式摘要（无档案时 hasContact=false 且 displayScope=null）
     */
    public AppContactDto getWorkContact(AppWorkDto work);
}