package com.smartscript.platform.content.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.WorkSummary;
import com.smartscript.platform.content.repository.SampleWorkRepository;
import com.smartscript.platform.identity.IdentityContext;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 内容/书城服务（A6 示例主流程，契约 A6-IDENTITY-CONTRACT-v1 §2）。
 *
 * 本类是 B 模块接入统一身份的**参考实现**，演示了 §10 的三条使用规则：
 *   1. 用户 ID 只从 {@link IdentityProvider} 取得，不接受请求体传入（{@link #shelf()}）；
 *   2. 游客身份正常可读公开列表，其 userId 为 null，本模块不伪造用户 ID；
 *   3. 角色/权限用于授权，实名状态用于业务准入，两者分别判断（{@link #shelf()} 中体现）。
 *
 * 边界：本模块不依赖 smartscript-user，也不读认证表、Token 表与实名材料表；
 * 公开列表不下发身份中的敏感字段（手机号、Token 等）。
 */
@Service
public class ContentWorkService
{
    /** 示例权限标识：待 B/C/D/E 负责人统一命名（A6-Q2）。 */
    public static final String PERM_WORK_QUERY = "content:work:query";

    private final SampleWorkRepository workRepository;
    private final IdentityProvider identityProvider;

    public ContentWorkService(SampleWorkRepository workRepository, IdentityProvider identityProvider)
    {
        this.workRepository = workRepository;
        this.identityProvider = identityProvider;
    }

    /**
     * 公开作品列表 + 当前身份摘要（游客可读）。
     *
     * 已登录时 `personalized=true`，由身份决定是否附带个性化标记；
     * 公开接口只返回身份的**非敏感**摘要（不含手机号、昵称、Token）。
     */
    public Map<String, Object> listPublicWorks()
    {
        IdentityContext identity = identityProvider.currentIdentity();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("identity", identitySummary(identity));
        result.put("works", workRepository.listPublic());
        result.put("total", workRepository.count());
        result.put("personalized", identity.isAuthenticated());
        return result;
    }

    /**
     * 我的书架（示例受保护主流程）。
     *
     * 身份规则：
     *   - 未登录：由 App 凭证域过滤器拒绝在本方法之前（不会到达这里）；此处仍做防御性判断；
     *   - 已登录：`ownerUserId` 取自服务端身份，保证归属；
     *   - 已实名：额外标记可下载（业务准入），与角色授权无关。
     */
    public Map<String, Object> shelf()
    {
        IdentityContext identity = identityProvider.currentIdentity();
        if (!identity.isAuthenticated())
        {
            // 防御性分支：正常情况下私有接口在过滤器层已被拒绝
            throw new IllegalStateException("shelf requires authenticated identity");
        }
        List<WorkSummary> works = new ArrayList<>(workRepository.listPublic());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("identity", identitySummary(identity));
        result.put("ownerUserId", identity.getUserId());
        result.put("works", works);
        result.put("total", works.size());
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
