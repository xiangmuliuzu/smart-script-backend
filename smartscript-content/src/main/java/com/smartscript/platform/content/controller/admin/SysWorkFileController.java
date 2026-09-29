package com.smartscript.platform.content.controller.admin;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.smartscript.platform.content.domain.SysWorkFile;
import com.smartscript.platform.content.domain.SysWorkVersion;
import com.smartscript.platform.content.service.ISysWorkFileService;

/**
 * 作品文件 / 作品版本管理（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_work_file + sys_work_version 表 + 作品上传资料查看页。
 * 路由前缀 /api/v1/admin/content/workfile（附件6.1 统一版本前缀 /api/v1 + 后台 /admin）。
 * 鉴权：若依 RBAC，@PreAuthorize + Bearer Token。
 * 返回：列表 TableDataInfo，详情 AjaxResult。
 * 反推处理点：
 * 1. 03 PC 接口文档未列 B 模块作品上传资料查看接口，按云端表结构 + 作品上传资料查看页反推；
 * 2. 版本列表不返回 content 全文（longtext 过大），仅返回版本号/标题/变更日志等元信息；
 * 3. 版本详情返回 content 与 file_url 全字段。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/workfile")
public class SysWorkFileController extends BaseController
{
    @Autowired
    private ISysWorkFileService workFileService;

    /**
     * 获取作品文件列表
     * 入参（query，均可选）：workId 精确、fileType 精确、isPreview 精确；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:workfile:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysWorkFile query)
    {
        startPage();
        List<SysWorkFile> list = workFileService.selectFileList(query);
        return getDataTable(list);
    }

    /**
     * 通过文件ID获取作品文件详细信息
     */
    @PreAuthorize("@ss.hasPermi('content:workfile:query')")
    @GetMapping("/{fileId}")
    public AjaxResult getInfo(@PathVariable Long fileId)
    {
        return success(workFileService.selectFileById(fileId));
    }

    /**
     * 获取作品版本列表（不含 content 全文）
     * 入参（query，必填）：workId 精确；pageNum/pageSize 分页
     */
    @PreAuthorize("@ss.hasPermi('content:workfile:list')")
    @GetMapping("/versions")
    public TableDataInfo versions(SysWorkVersion query,
                                  @RequestParam(value = "workId", required = false) Long workId)
    {
        if (workId != null)
        {
            query.setWorkId(workId);
        }
        startPage();
        List<SysWorkVersion> list = workFileService.selectVersionList(query);
        return getDataTable(list);
    }

    /**
     * 通过版本ID获取作品版本详细信息（含 content 与 file_url 全字段）
     */
    @PreAuthorize("@ss.hasPermi('content:workfile:query')")
    @GetMapping("/version/{versionId}")
    public AjaxResult getVersion(@PathVariable Long versionId)
    {
        return success(workFileService.selectVersionById(versionId));
    }
}
