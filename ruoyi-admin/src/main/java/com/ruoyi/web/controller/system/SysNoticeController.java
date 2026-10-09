package com.ruoyi.web.controller.system;

import java.util.List;
import java.util.Map;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestParam;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.NoticeReceipt;
import com.ruoyi.system.service.NoticeRecipientService;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.domain.SysNotice;
import com.ruoyi.system.service.ISysNoticeService;

/**
 * 公告 信息操作处理
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/system/notice")
public class SysNoticeController extends BaseController
{
    @Autowired
    private ISysNoticeService noticeService;

    @Autowired
    private NoticeRecipientService recipientService;

    /**
     * 获取通知公告列表
     */
    @PreAuthorize("@ss.hasPermi('system:notice:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysNotice notice,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize)
    {
        requireAdministrator();
        PageHelper.startPage(Math.max(1, pageNum), Math.max(1, Math.min(100, pageSize)));
        List<SysNotice> list = noticeService.selectNoticeList(notice);
        return getDataTable(list);
    }

    /**
     * 根据通知公告编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:notice:query')")
    @GetMapping(value = "/{noticeId}")
    public AjaxResult getInfo(@PathVariable Long noticeId)
    {
        requireAdministrator();
        return success(noticeService.selectNoticeById(noticeId));
    }

    /**
     * 新增通知公告
     */
    @PreAuthorize("@ss.hasPermi('system:notice:add')")
    @Log(title = "通知公告", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysNotice notice)
    {
        requireAdministrator();
        notice.setCreateBy(getUsername());
        return toAjax(noticeService.insertNotice(notice));
    }

    /**
     * 修改通知公告
     */
    @PreAuthorize("@ss.hasPermi('system:notice:edit')")
    @Log(title = "通知公告", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysNotice notice)
    {
        requireAdministrator();
        notice.setUpdateBy(getUsername());
        return toAjax(noticeService.updateNotice(notice));
    }

    /**
     * 首页顶部公告列表（返回全部正常公告，带当前用户已读标记，最多5条）
     */
    @GetMapping("/listTop")
    @ResponseBody
    public AjaxResult listTop()
    {
        requireAdministrator();
        PageHelper.startPage(1, 5, false);
        List<NoticeReceipt> list = recipientService.list(getUserId(), "00");
        AjaxResult result = success(list);
        result.put("unreadCount", recipientService.unreadCount(getUserId(), "00"));
        return result;
    }

    /**
     * 标记公告已读
     */
    @PostMapping("/markRead")
    @ResponseBody
    public AjaxResult markRead(Long noticeId)
    {
        requireAdministrator();
        recipientService.markRead(getUserId(), "00", noticeId);
        return success();
    }

    /**
     * 批量标记已读
     */
    @PostMapping("/markReadAll")
    @ResponseBody
    public AjaxResult markReadAll(String ids)
    {
        requireAdministrator();
        Long[] noticeIds = Convert.toLongArray(ids);
        recipientService.markReadBatch(getUserId(), "00", noticeIds);
        return success();
    }

    /**
     * 删除通知公告
     */
    @PreAuthorize("@ss.hasPermi('system:notice:remove')")
    @Log(title = "通知公告", businessType = BusinessType.DELETE)
    @DeleteMapping("/{noticeIds}")
    public AjaxResult remove(@PathVariable Long[] noticeIds)
    {
        requireAdministrator();
        return toAjax(noticeService.deleteNoticeByIds(noticeIds));
    }

    @GetMapping("/received")
    public TableDataInfo received(@RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize)
    {
        requireAdministrator();
        PageHelper.startPage(Math.max(1, pageNum), Math.max(1, Math.min(100, pageSize)));
        return getDataTable(recipientService.list(getUserId(), "00"));
    }

    @GetMapping("/received/{noticeId}")
    public AjaxResult receivedDetail(@PathVariable Long noticeId)
    {
        requireAdministrator();
        return success(recipientService.detail(getUserId(), "00", noticeId));
    }

    @GetMapping("/received/unread-count")
    public AjaxResult unreadCount()
    {
        requireAdministrator();
        return success(Map.of("total", recipientService.unreadCount(getUserId(), "00")));
    }

    @PutMapping("/received/{noticeId}/read")
    public AjaxResult read(@PathVariable Long noticeId)
    {
        requireAdministrator();
        return success(Map.of("changed", recipientService.markRead(getUserId(), "00", noticeId)));
    }

    @PutMapping("/received/read-all")
    public AjaxResult readAll()
    {
        requireAdministrator();
        return success(Map.of("updated", recipientService.markAllRead(getUserId(), "00")));
    }

    private void requireAdministrator()
    {
        var user = SecurityUtils.getLoginUser().getUser();
        if (user == null || !"00".equals(user.getUserType()) || !"0".equals(user.getStatus())
                || !"0".equals(user.getDelFlag()))
            throw new ServiceException("仅后台管理员可访问", 403);
    }

}
