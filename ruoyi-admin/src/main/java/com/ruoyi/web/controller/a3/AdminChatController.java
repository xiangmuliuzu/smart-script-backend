package com.ruoyi.web.controller.a3;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.smartscript.platform.user.dto.ChatMessageSendRequest;
import com.smartscript.platform.user.dto.ChatSessionCreateRequest;
import com.smartscript.platform.user.service.ChatService;
import com.smartscript.platform.user.exception.AppAuthException;

/**
 * A3 管理端聊天接口（若依 Token 鉴权）。
 *
 * 路由前缀 /api/v1/admin/chat，打开详情即处理并已读，可结束或重新打开会话。
 */
@RestController
@RequestMapping("/api/v1/admin/chat")
public class AdminChatController extends BaseController
{
    private final ChatService chatService;

    public AdminChatController(ChatService chatService)
    {
        this.chatService = chatService;
    }

    /** 全部会话列表（分页 + 筛选） */
    @PreAuthorize("@ss.hasPermi('chat:session:list')")
    @GetMapping("/sessions")
    public TableDataInfo listSessions(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String keyword)
    {
        startPage();
        var list = chatService.adminSessions(status, businessType, keyword);
        return getDataTable(list);
    }

    /** 当前管理员参与的会话未读总数，不受列表分页限制。 */
    @PreAuthorize("@ss.hasPermi('chat:session:list')")
    @GetMapping("/unread-count")
    public AjaxResult unreadCount()
    {
        return success(chatService.chatUnreadCount(SecurityUtils.getUserId()));
    }

    /** 会话详情（含用户信息） */
    @PreAuthorize("@ss.hasPermi('chat:session:query')")
    @GetMapping("/sessions/{sessionId}")
    public AjaxResult sessionDetail(@PathVariable Long sessionId)
    {
        return success(chatService.adminSessionDetail(sessionId));
    }

    /** 进入详情，由当前管理员接手并将本次显示的消息标为已读。 */
    @PreAuthorize("@ss.hasPermi('chat:session:query') and @ss.hasPermi('chat:message:list')")
    @Log(title = "用户沟通-打开", businessType = BusinessType.UPDATE)
    @PutMapping("/sessions/{sessionId}/open")
    public AjaxResult openSession(@PathVariable Long sessionId,
                                  @RequestParam(defaultValue = "0") Long throughMessageId)
    {
        requireAdministrator();
        return success(chatService.openAdminSession(sessionId, SecurityUtils.getUserId(), throughMessageId));
    }

    private void requireAdministrator()
    {
        var user = SecurityUtils.getLoginUser().getUser();
        if (user == null || !"00".equals(user.getUserType()) || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag()))
            throw new ServiceException("仅管理员可处理用户沟通", 403);
    }

    /** 聊天服务共用 App 领域异常，在管理端返回若依错误信封。 */
    @ExceptionHandler(AppAuthException.class)
    public AjaxResult handleChatException(AppAuthException exception)
    {
        return AjaxResult.error(exception.getHttpStatus(), exception.getMessage());
    }

    /** 会话内历史消息 */
    @PreAuthorize("@ss.hasPermi('chat:message:list')")
    @GetMapping("/sessions/{sessionId}/messages")
    public AjaxResult listMessages(@PathVariable Long sessionId)
    {
        return success(chatService.messages(sessionId));
    }

    /** 管理员创建/获取会话（从管理端发起） */
    @PreAuthorize("@ss.hasPermi('chat:session:create')")
    @Log(title = "用户沟通", businessType = BusinessType.INSERT)
    @PostMapping("/sessions")
    public AjaxResult createSession(@RequestBody ChatSessionCreateRequest req)
    {
        Long adminId = SecurityUtils.getUserId();
        // 管理端主动联系用户时，targetUserId 为 App 用户（user1），管理员为 user2
        if (req.getTargetUserId() != null)
        {
            req.setTargetAdminId(adminId);
            return success(chatService.createOrGetSession(req.getTargetUserId(), req));
        }
        // 兼容旧逻辑：管理员作为 user1
        return success(chatService.createOrGetSession(adminId, req));
    }

    /** 管理员回复消息 */
    @PreAuthorize("@ss.hasPermi('chat:message:send')")
    @Log(title = "用户沟通", businessType = BusinessType.OTHER)
    @PostMapping("/sessions/{sessionId}/messages")
    public AjaxResult sendMessage(@PathVariable Long sessionId,
                                  @RequestBody ChatMessageSendRequest req)
    {
        Long adminId = SecurityUtils.getUserId();
        chatService.assertAdminCanOperate(sessionId, adminId);
        return success(chatService.sendMessage(sessionId, adminId, req));
    }

    /** 详情前台自动已读，仅更新已展示的消息；旧调用未传编号时沿用全部已读。 */
    @PreAuthorize("@ss.hasPermi('chat:session:query') and @ss.hasPermi('chat:message:list')")
    @PutMapping("/sessions/{sessionId}/read")
    public AjaxResult markRead(@PathVariable Long sessionId,
                              @RequestParam(defaultValue = "9223372036854775807") Long throughMessageId)
    {
        requireAdministrator();
        return success(chatService.markAdminSessionRead(sessionId, SecurityUtils.getUserId(), throughMessageId));
    }

    /** 可分配的管理员候选列表（管理域账号且挂有有效角色，供分配下拉使用） */
    @PreAuthorize("@ss.hasPermi('chat:session:list')")
    @GetMapping("/admins")
    public AjaxResult listAdmins()
    {
        return success(chatService.adminCandidates());
    }

    /** 分配处理管理员 */
    @PreAuthorize("@ss.hasPermi('chat:session:assign')")
    @Log(title = "用户沟通-分配", businessType = BusinessType.UPDATE)
    @PutMapping("/sessions/{sessionId}/assign")
    public AjaxResult assignAdmin(@PathVariable Long sessionId,
                                  @RequestParam Long adminId)
    {
        return success(chatService.assignAdmin(sessionId, adminId));
    }

    /** 变更会话状态（结束/重新打开） */
    @PreAuthorize("@ss.hasPermi('chat:session:status')")
    @Log(title = "用户沟通-状态", businessType = BusinessType.UPDATE)
    @PutMapping("/sessions/{sessionId}/status")
    public AjaxResult changeStatus(@PathVariable Long sessionId,
                                   @RequestParam String action)
    {
        requireAdministrator();
        return success(chatService.changeStatus(sessionId, SecurityUtils.getUserId(), action));
    }
}
