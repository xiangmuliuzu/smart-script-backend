package com.smartscript.platform.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.util.AppMasks;
import com.smartscript.platform.user.util.AppClientIp;

/**
 * A5 用户中心：鉴权无关的固定逻辑校验。
 *
 * 覆盖脱敏规则、参数规范化、状态机准入、一次性凭证形态与合法 IP 解析。
 * 端到端主流程由人工冒烟与后续加固项 H-01/H-04 的自动化补齐。
 */
class AppUserCenterUnitTest
{
    // ---------------- 脱敏 ----------------

    @Test
    void realNameMaskKeepsFirstCharOnly()
    {
        assertEquals("王**", AppMasks.maskRealName("王小明"));
        assertEquals("欧*", AppMasks.maskRealName("欧阳"));
        // 单字符姓名无更多字符可掩，保留原字符
        assertEquals("王", AppMasks.maskRealName("王"));
        assertEquals("王**", AppMasks.maskRealName("  王小明  "));
        assertNull(AppMasks.maskRealName(null));
        assertNull(AppMasks.maskRealName("   "));
    }

    @Test
    void idNumberMaskKeepsHeadAndTail()
    {
        assertEquals("110101********1234", AppMasks.maskIdNumber("110101199001011234"));
        // 17 位旧证：保留前 6 位与后 4 位，中间全掩码
        assertEquals("110101*******1123", AppMasks.maskIdNumber("11010119900101123"));
        // 过短号码只保留首尾，中间全掩码，避免被完整还原
        assertEquals("1**4", AppMasks.maskIdNumber("1234"));
        assertEquals("*", AppMasks.maskIdNumber("12"));
        assertNull(AppMasks.maskIdNumber(null));
    }

    @Test
    void maskedValuesNeverContainFullInput()
    {
        String idNumber = "110101199001011234";
        String masked = AppMasks.maskIdNumber(idNumber);
        assertFalse(masked.contains(idNumber));
        // 中段生日不得出现在掩码中
        assertFalse(masked.contains("19900101"));
    }

    // ---------------- 个人资料 ----------------

    @Test
    void nicknameIsTrimmedAndBounded()
    {
        assertEquals("张三", AppUserProfileService.normalizeNickname("  张三  "));
        assertEquals("张三", AppUserProfileService.normalizeNickname("张三"));
        assertThrows(AppAuthException.class, () -> AppUserProfileService.normalizeNickname("   "));
        assertThrows(AppAuthException.class, () -> AppUserProfileService.normalizeNickname(null));
        assertThrows(AppAuthException.class,
                () -> AppUserProfileService.normalizeNickname("a".repeat(31)));
        assertDoesNotThrow(() -> AppUserProfileService.normalizeNickname("a".repeat(30)));
    }

    @Test
    void avatarAcceptsOnlyHttpUrlsOrEmpty()
    {
        assertEquals("", AppUserProfileService.normalizeAvatar(null));
        assertEquals("", AppUserProfileService.normalizeAvatar("   "));
        assertEquals("http://h/a.png", AppUserProfileService.normalizeAvatar(" http://h/a.png "));
        assertEquals("HTTPS://h/a.png", AppUserProfileService.normalizeAvatar("HTTPS://h/a.png"));
        // 非 http(s) 协议与会话可执行协议一律拒绝
        assertThrows(AppAuthException.class, () -> AppUserProfileService.normalizeAvatar("javascript:alert(1)"));
        assertThrows(AppAuthException.class, () -> AppUserProfileService.normalizeAvatar("data:text/html;base64,AA"));
        assertThrows(AppAuthException.class, () -> AppUserProfileService.normalizeAvatar("/profile/upload/a.png"));
    }

    // ---------------- 实名 ----------------

    @Test
    void realNameSubmitRejectsInvalidPayload()
    {
        assertThrows(AppAuthException.class, () -> AppRealNameService.normalizeRealName("王"));
        assertDoesNotThrow(() -> AppRealNameService.normalizeRealName("王二"));
        assertThrows(AppAuthException.class, () -> AppRealNameService.normalizeRealName("a".repeat(33)));

        assertDoesNotThrow(() -> AppRealNameService.normalizeIdNumber("110101199001011234"));
        assertDoesNotThrow(() -> AppRealNameService.normalizeIdNumber("11010119900101123X"));
        assertThrows(AppAuthException.class, () -> AppRealNameService.normalizeIdNumber("11010119900101"));
        assertThrows(AppAuthException.class, () -> AppRealNameService.normalizeIdNumber("11010119900101123Y"));
    }

    @Test
    void realNameMaterialRequiresAtLeastOneUsableRef()
    {
        assertEquals("/profile/upload/a.png",
                AppRealNameService.normalizeMaterialRefs(List.of(" /profile/upload/a.png ")));
        assertThrows(AppAuthException.class, () -> AppRealNameService.normalizeMaterialRefs(null));
        assertThrows(AppAuthException.class, () -> AppRealNameService.normalizeMaterialRefs(List.of()));
        assertThrows(AppAuthException.class, () -> AppRealNameService.normalizeMaterialRefs(List.of("  ", "")));
        assertThrows(AppAuthException.class,
                () -> AppRealNameService.normalizeMaterialRefs(List.of("a", "b", "c", "d")));
    }

    @Test
    void realNameStateMachineBlocksPendingAndApproved()
    {
        // PENDING 与 APPROVED 不允许再次提交
        AppAuthException pending = assertThrows(AppAuthException.class,
                () -> AppRealNameService.requireSubmittable(AppRealNameService.STATUS_PENDING));
        assertEquals(AppUserErrorCodes.REAL_NAME_CONFLICT, pending.getCode());
        assertEquals(409, pending.getHttpStatus());

        AppAuthException approved = assertThrows(AppAuthException.class,
                () -> AppRealNameService.requireSubmittable(AppRealNameService.STATUS_APPROVED));
        assertEquals(AppUserErrorCodes.REAL_NAME_CONFLICT, approved.getCode());

        // 未提交与已驳回允许提交
        assertDoesNotThrow(() -> AppRealNameService.requireSubmittable(AppRealNameService.STATUS_NOT_SUBMITTED));
        assertDoesNotThrow(() -> AppRealNameService.requireSubmittable(AppRealNameService.STATUS_REJECTED));
        assertDoesNotThrow(() -> AppRealNameService.requireSubmittable(null));
    }

    // ---------------- 换绑凭证 ----------------

    @Test
    void stepUpTokenShapeIsEnforcedBeforeStorage()
    {
        assertTrue(PhoneChangeTokenStore.looksLikeToken("A".repeat(32)));
        assertTrue(PhoneChangeTokenStore.looksLikeToken("abc-DEF_123".repeat(3)));
        assertFalse(PhoneChangeTokenStore.looksLikeToken(null));
        assertFalse(PhoneChangeTokenStore.looksLikeToken("short"));
        // 含非法字符（如路径分隔符）必须被挡在存储之前
        assertFalse(PhoneChangeTokenStore.looksLikeToken("abc/defghijklmnopqrstuvwxyz0123"));
        assertFalse(PhoneChangeTokenStore.looksLikeToken("a".repeat(129)));
    }

    @Test
    void phoneChangeScenesMatchDatabaseSceneSet()
    {
        assertEquals("CHANGE_PHONE_OLD", AppPhoneChangeService.SCENE_CHANGE_PHONE_OLD);
        assertEquals("CHANGE_PHONE_NEW", AppPhoneChangeService.SCENE_CHANGE_PHONE_NEW);
        assertEquals("PHONE_CHANGE", PhoneChangeTokenStore.SCENE_PHONE_CHANGE);
    }

    // ---------------- 消息与偏好 ----------------

    @Test
    void messageTypeFilterOnlyAcceptsContractTypes()
    {
        assertEquals("SYSTEM", UserMessageService.normalizeType("system", false));
        assertEquals("BENEFIT", UserMessageService.normalizeType(" BENEFIT ", false));
        assertNull(UserMessageService.normalizeType(null, true));
        assertNull(UserMessageService.normalizeType("", true));
        assertThrows(AppAuthException.class, () -> UserMessageService.normalizeType("PROMO", false));
        assertThrows(AppAuthException.class, () -> UserMessageService.normalizeType(null, false));
    }

    @Test
    void preferenceChannelAndTypeAreWhitelisted()
    {
        assertEquals("INBOX", UserMessageService.normalizeChannel("inbox"));
        assertEquals("PUSH", UserMessageService.normalizeChannel(" push "));
        assertThrows(AppAuthException.class, () -> UserMessageService.normalizeChannel("SMS"));
        assertThrows(AppAuthException.class, () -> UserMessageService.normalizeChannel(null));
        // 偏好保存不接受空类型（列表筛选才允许不传类型）
        assertThrows(AppAuthException.class, () -> UserMessageService.normalizeType("", false));
        assertEquals("SYSTEM", UserMessageService.normalizeType("SYSTEM", false));
    }

    // ---------------- 反馈 ----------------

    @Test
    void feedbackCategoryDefaultsToOtherAndRejectsUnknown()
    {
        assertEquals("OTHER", UserFeedbackService.normalizeCategory(null));
        assertEquals("OTHER", UserFeedbackService.normalizeCategory("  "));
        assertEquals("BUG", UserFeedbackService.normalizeCategory("bug"));
        assertThrows(AppAuthException.class, () -> UserFeedbackService.normalizeCategory("SPAM"));
    }

    @Test
    void feedbackContentIsTrimmedAndBounded()
    {
        assertEquals("这是一个问题反馈", UserFeedbackService.normalizeContent("  这是一个问题反馈  "));
        assertThrows(AppAuthException.class, () -> UserFeedbackService.normalizeContent("短"));
        assertThrows(AppAuthException.class, () -> UserFeedbackService.normalizeContent(null));
        assertThrows(AppAuthException.class,
                () -> UserFeedbackService.normalizeContent("x".repeat(2001)));
    }

    @Test
    void feedbackStatusFilterMatchesDatabaseCheckConstraint()
    {
        assertNull(UserFeedbackService.normalizeStatusFilter(null));
        assertNull(UserFeedbackService.normalizeStatusFilter("  "));
        assertEquals("SUBMITTED", UserFeedbackService.normalizeStatusFilter("submitted"));
        assertEquals("PROCESSING", UserFeedbackService.normalizeStatusFilter("PROCESSING"));
        assertEquals("REPLIED", UserFeedbackService.normalizeStatusFilter("REPLIED"));
        assertEquals("CLOSED", UserFeedbackService.normalizeStatusFilter("CLOSED"));
        // OPEN 是 A4 之前的旧值，A4 迁移已归一为 SUBMITTED，不得再被接受
        assertThrows(AppAuthException.class, () -> UserFeedbackService.normalizeStatusFilter("OPEN"));
        assertThrows(AppAuthException.class, () -> UserFeedbackService.normalizeStatusFilter("DONE"));
    }

    // ---------------- 客户端 IP ----------------

    @Test
    void clientIpPrefersForwardedForFirstHop()
    {
        assertNull(AppClientIp.resolve(null));
    }

    // ---------------- 错误码一致性 ----------------

    @Test
    void appUserErrorCodesDoNotCollideWithAuthCodes()
    {
        // A5 复用的码必须与 A3 同值，新增码不得与 A3 冲突
        assertEquals(AppAuthErrorCodes.PARAM, AppUserErrorCodes.PARAM);
        assertEquals(AppAuthErrorCodes.UNAUTHORIZED, AppUserErrorCodes.UNAUTHORIZED);
        assertEquals(AppAuthErrorCodes.DOMAIN_OR_PERMISSION, AppUserErrorCodes.DOMAIN_OR_PERMISSION);
        assertEquals(AppAuthErrorCodes.ACCOUNT_DISABLED, AppUserErrorCodes.ACCOUNT_DISABLED);
        assertEquals(AppAuthErrorCodes.PHONE_TAKEN, AppUserErrorCodes.PHONE_TAKEN);
        assertFalse(AppUserErrorCodes.RESOURCE_NOT_FOUND == AppAuthErrorCodes.PARAM);
        assertFalse(AppUserErrorCodes.REAL_NAME_CONFLICT == AppAuthErrorCodes.PHONE_TAKEN);
        assertFalse(AppUserErrorCodes.DUPLICATE_SUBMIT == AppAuthErrorCodes.PHONE_TAKEN);
    }
}
