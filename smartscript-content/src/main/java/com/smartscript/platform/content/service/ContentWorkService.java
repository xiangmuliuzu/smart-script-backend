package com.smartscript.platform.content.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.smartscript.platform.identity.IdentityContext;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 内容/书城身份摘要服务（A6 示例主流程，契约 A6-IDENTITY-CONTRACT-v1 §2）。
 *
 * 本类是 B 模块接入统一身份的**参考实现**，演示了 §10 的三条使用规则：
 *   1. 用户 ID 只从 {@link IdentityProvider} 取得，不接受请求体传入（{@link #identityBlock()}）；
 *   2. 游客身份正常可读公开列表，本模块不伪造用户 ID（公开列表见 AppWorkController）；
 *   3. 角色/权限用于授权，实名状态用于业务准入，两者分别判断（{@link #identityBlock()} 中体现）。
 *
 * 现状（B 模块 2.7.12 书架管理）：原示例书架接口的作品数据（示例内存仓储）已替换为
 * 真实书架分页数据，由 {@link com.smartscript.platform.content.controller.app.AppBookshelfController}
 * 组装；本服务只负责下发身份摘要块，不再持有任何示例数据。
 *
 * 边界：本模块不依赖 smartscript-user，也不读认证表、Token 表与实名材料表；
 * 身份摘要不下发敏感字段（手机号、Token 等）。
 */
@Service
public class ContentWorkService
{
    /** 示例权限标识：待 B/C/D/E 负责人统一命名（A6-Q2）。 */
    public static final String PERM_WORK_QUERY = "content:work:query";

    private final IdentityProvider identityProvider;

    public ContentWorkService(IdentityProvider identityProvider)
    {
        this.identityProvider = identityProvider;
    }

    /**
     * 身份摘要块（供书架列表与前端身份卡片使用）。
     *
     * 身份规则：
     *   - 未登录：由 App 凭证域过滤器拒绝在本方法之前（不会到达这里）；此处仍做防御性判断；
     *   - 已登录：`ownerUserId` 取自服务端身份，保证归属；
     *   - 已实名：额外标记可下载（业务准入），与角色授权无关。
     *
     * @return 可安全下发的身份字段：identity / ownerUserId / downloadable / realNameRequired
     */
    public Map<String, Object> identityBlock()
    {
        IdentityContext identity = identityProvider.currentIdentity();
        if (!identity.isAuthenticated())
        {
            // 防御性分支：正常情况下私有接口在过滤器层已被拒绝
            throw new IllegalStateException("shelf requires authenticated identity");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("identity", identitySummary(identity));
        result.put("ownerUserId", identity.getUserId());
        // 业务准入：实名通过才可下载素材；与角色授权独立判断
        result.put("downloadable", identity.isRealNameApproved());
        result.put("realNameRequired", !identity.isRealNameApproved());
        return result;
    }

    /**
     * 身份摘要：只输出供前端展示与准入判断所需的字段。
     *
     * 刻意不包含手机号、昵称、头像与任何凭证，避免公开接口泄漏身份细节。
     */
    private Map<String, Object> identitySummary(IdentityContext identity)
    {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("authenticated", identity.isAuthenticated());
        summary.put("guest", identity.isGuest());
        summary.put("userId", identity.getUserId());
        summary.put("accountType", identity.getAccountType());
        summary.put("realNameStatus", identity.getRealNameStatus());
        summary.put("authorCapability", identity.isAuthorCapability());
        summary.put("roleCodes", identity.getRoleCodes());
        summary.put("permissionCodes", identity.getPermissionCodes());
        return summary;
    }
}