package com.smartscript.platform.content.controller.admin;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.smartscript.platform.content.domain.SysWork;
import com.smartscript.platform.content.domain.SysWorkChapter;
import com.smartscript.platform.content.service.ISysWorkService;

/**
 * 作品管理（PC 后台，只读）
 *
 * 依据：云端 script_platform_dev 库 sys_work + sys_work_chapter 表 + PC 功能清单
 * （作品管理页：列表、详情、章节查看；清单未列作品新增/编辑/删除，作品录入走
 * 创作端，故本控制器只读，写操作归书城作品控制器 SysBookstoreController）。
 * 路由前缀 /api/v1/admin/content/work（附件6.1 统一版本前缀 /api/v1 + 后台 /admin）。
 * 鉴权：若依 RBAC，@PreAuthorize + Bearer Token。
 * 返回：列表 TableDataInfo，其余 AjaxResult。
 * 反推处理点：03 PC 接口文档未列 B 模块作品管理接口，按云端表结构 + PC 功能清单反推。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/work")
public class SysWorkController extends BaseController
{
    @Autowired
    private ISysWorkService workService;

    /**
     * 获取作品列表
     * 入参（query，均可选）：title 模糊、authorId/genreId/status/workType/tradeType 精确；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:work:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysWork query)
    {
        startPage();
        List<SysWork> list = workService.selectWorkList(query);
        return getDataTable(list);
    }

    /**
     * 通过作品ID获取详细信息（含 authorName/genreName）
     */
    @PreAuthorize("@ss.hasPermi('content:work:query')")
    @GetMapping("/{workId}")
    public AjaxResult getInfo(@PathVariable Long workId)
    {
        return success(workService.selectWorkById(workId));
    }

    /**
     * 按作品ID查询章节列表（分页，不含 content 全文）
     */
    @PreAuthorize("@ss.hasPermi('content:work:list')")
    @GetMapping("/{workId}/chapters")
    public TableDataInfo chapters(@PathVariable Long workId)
    {
        startPage();
        SysWorkChapter query = new SysWorkChapter();
        query.setWorkId(workId);
        List<SysWorkChapter> list = workService.selectChapterListByWorkId(query);
        return getDataTable(list);
    }
}
