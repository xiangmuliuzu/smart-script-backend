package com.smartscript.platform.content.controller.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppContactDto;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.dto.AppPreviewDto;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.dto.AppWorkQuery;
import com.smartscript.platform.content.service.IAppBookstoreService;

/**
 * App 书城作品（B 模块，接口文档 2.7.1 表 2-81 作品列表 / 2.7.2 表 2-82 作品详情）。
 *
 * 鉴权：公开接口，游客可读（在 App 凭证域白名单登记，无需 App Token）。
 * 返回：列表 {total, list}（App 信封内），详情直接下发作品对象。
 *
 * 可见性：只返回未删除且已上架（status='on_shelf'）的作品；未上架作品按 404 处理，
 * 不区分「不存在」与「未上架」，避免通过错误信息探测未公开内容。
 *
 * 路由顺序：/{workId} 为路径变量，静态子路径若后续新增需声明在其之前。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content/works")
public class AppWorkController
{
    /** 作品不存在或未上架 */
    private static final int CODE_NOT_FOUND = 404;

    private final IAppBookstoreService bookstoreService;

    public AppWorkController(IAppBookstoreService bookstoreService)
    {
        this.bookstoreService = bookstoreService;
    }

    /**
     * 作品列表（分页 + 筛选 + 排序）
     *
     * @param categoryId 分类ID（可选）
     * @param tagId      标签ID（可选）
     * @param keyword    关键词（可选，按标题模糊）
     * @param sort       排序（可选：latest/view/favorite/sale/rating/price_asc/price_desc，非法值回落 latest）
     * @param page       页码（可选，默认 1）
     * @param pageSize   每页条数（可选，默认 10，上限 50）
     */
    @GetMapping
    public AppApiResponse<AppPageResult<AppWorkDto>> list(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize)
    {
        AppWorkQuery query = new AppWorkQuery();
        query.setCategoryId(categoryId);
        query.setTagId(tagId);
        query.setKeyword(keyword);
        query.setSort(sort);
        int pageNum = page == null ? 1 : page;
        int size = pageSize == null ? 10 : pageSize;
        return AppApiResponse.ok(bookstoreService.pageWorks(query, pageNum, size));
    }

    /**
     * 作品详情
     *
     * @param workId 作品ID
     */
    @GetMapping("/{workId}")
    public AppApiResponse<AppWorkDto> detail(@PathVariable Long workId)
    {
        AppWorkDto work = bookstoreService.getWork(workId);
        if (work == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或已下架");
        }
        return AppApiResponse.ok(work);
    }

    /**
     * 版权合作联系方式（接口文档 2.7.9 表 2-89）
     *
     * 按作品作者查联系方式档案，只下发 {workId, hasContact, displayScope}，
     * 不下发明文联系方式；未上架/不存在按 404 处理，与详情口径一致。
     *
     * @param workId 作品ID
     */
    @GetMapping("/{workId}/contact")
    public AppApiResponse<AppContactDto> contact(@PathVariable Long workId)
    {
        AppWorkDto work = bookstoreService.getWork(workId);
        if (work == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或已下架");
        }
        return AppApiResponse.ok(bookstoreService.getWorkContact(work));
    }

    /**
     * 免费试读（接口文档 2.7.8）
     *
     * @param workId 作品ID
     * @return 试读载荷（试读开关与集数 + 可读章节子集 + 可预览文件）
     */
    @GetMapping("/{workId}/preview")
    public AppApiResponse<AppPreviewDto> preview(@PathVariable Long workId)
    {
        AppWorkDto work = bookstoreService.getWork(workId);
        if (work == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "作品不存在或已下架");
        }
        return AppApiResponse.ok(bookstoreService.getPreview(work));
    }
}