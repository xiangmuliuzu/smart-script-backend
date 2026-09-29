package com.smartscript.platform.user.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A 模块鉴权 / 数据归属 / 事务契约的静态回归测试（H-04）。
 *
 * 与运行时矩阵（shared/scripts/h-a-backend-matrix.mjs，78 项黑盒 HTTP + 数据库断言）互补：
 * 运行时矩阵证明「当前行为正确」，本测试把产生该行为的代码约束固定下来，防止后续改动回退。
 * 不启动 Spring、不连接数据库。
 *
 * 覆盖：
 *   1. 数据归属：所有「我的」语句必须在 SQL 层绑定 user_id（禁止先查全量再内存过滤，评审标准 §5.4）。
 *   2. 防越权（一票否决第 12 项）：App 请求 DTO 不得携带 userId，控制器不得从请求取操作者身份。
 *   3. 事务：写操作必须标注 @Transactional，避免部分提交。
 */
class AppAuthzOwnershipContractTest
{
    private static final String PKG = "com/smartscript/platform/user";

    /** 尝试多种工作目录基准，兼容「模块目录」与「仓库根」两种执行位置。 */
    /** 归属过滤子句：WHERE/AND/ON 后的 user_id **必须**等值绑定到认证上下文占位符 #{userId}（别名可选）。 */
    private static final Pattern OWNER_FILTER = Pattern.compile(
            "(?:where|and|on)\\s+(?:[a-z_]+\\s*\\.\\s*)?user_id\\s*=\\s*#\\{\\s*userId\\s*\\}",
            Pattern.CASE_INSENSITIVE);

    /** INSERT 的列清单首位为 user_id。 */
    private static final Pattern OWNER_INSERT_COLUMN = Pattern.compile(
            "insert\\s+into\\s+[a-z_0-9]+\\s*\\(\\s*user_id\\s*,", Pattern.CASE_INSENSITIVE);

    /** INSERT 的值首位取自 #{userId} 占位符（归属来自认证上下文）。 */
    private static final Pattern OWNER_INSERT_VALUE = Pattern.compile(
            "values\\s*\\(\\s*#\\{\\s*userId\\s*\\}", Pattern.CASE_INSENSITIVE);

    /** 去掉 XML 注释，避免「注释里提到 user_id = #{userId}」被误判为真实归属条件。 */
    private static String stripXmlComments(String xml)
    {
        return xml.replaceAll("(?s)<!--.*?-->", " ");
    }

    private static Path sourceRoot()
    {
        List<String> candidates = List.of(
                "src/main/java/" + PKG,
                "smartscript-user/src/main/java/" + PKG,
                "D:/build/smart-script-backend/smartscript-user/src/main/java/" + PKG);
        for (String c : candidates)
        {
            Path p = Paths.get(c);
            if (Files.isDirectory(p))
            {
                return p;
            }
        }
        throw new IllegalStateException("找不到源码根目录，候选: " + candidates);
    }

    private static String read(String relative) throws IOException
    {
        return Files.readString(sourceRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    /** mapper XML 位于 src/main/resources，与 Java 源码根分开解析。 */
    private static Path resourceRoot()
    {
        List<String> candidates = List.of(
                "src/main/resources",
                "smartscript-user/src/main/resources",
                "D:/build/smart-script-backend/smartscript-user/src/main/resources");
        for (String c : candidates)
        {
            Path p = Paths.get(c);
            if (Files.isDirectory(p))
            {
                return p;
            }
        }
        throw new IllegalStateException("找不到资源根目录，候选: " + candidates);
    }

    private static String readResource(String relative) throws IOException
    {
        return Files.readString(resourceRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    /** 取出 mapper XML 中某个 statement 的正文（不含标签外的其它语句）。 */
    private static String statementBody(String xml, String id)
    {
        int i = xml.indexOf("id=\"" + id + "\"");
        if (i < 0)
        {
            return null;
        }
        int end = -1;
        for (String tag : new String[] { "</select>", "</update>", "</insert>", "</delete>" })
        {
            int e = xml.indexOf(tag, i);
            if (e >= 0 && (end < 0 || e < end))
            {
                end = e;
            }
        }
        // 返回前剥离 XML 注释：注释中的 SQL 片段不构成真实约束，不得参与断言
        return end < 0 ? null : stripXmlComments(xml.substring(i, end));
    }

    // ------------------------------------------------------------------
    // 1. 数据归属：SQL 层强制 user_id
    // ------------------------------------------------------------------

    @Test
    void everyUserScopedStatementFiltersByOwnerInSql() throws Exception
    {
        String xml = readResource("mapper/user/AppUserCenterMapper.xml");
        // 语句 -> 归属参数来源说明（全部应含 user_id 条件）
        Map<String, String> scoped = new LinkedHashMap<>();
        scoped.put("updateNickName", "资料-昵称");
        scoped.put("updateAvatar", "资料-头像");
        scoped.put("updatePhone", "换绑手机号");
        scoped.put("selectLatestRealName", "实名最新申请");
        scoped.put("selectLatestRealNameStatus", "实名状态");
        scoped.put("selectMyMessages", "消息列表");
        scoped.put("selectMyMessage", "消息详情");
        scoped.put("countUnread", "未读数");
        scoped.put("countUnreadGroupByType", "未读分组");
        scoped.put("markRead", "单条已读");
        scoped.put("markAllRead", "全部已读");
        scoped.put("selectPreferences", "通知偏好查询");
        scoped.put("upsertPreference", "通知偏好写入");
        scoped.put("deletePreference", "通知偏好删除");
        scoped.put("selectMyFeedbackPage", "反馈列表");
        scoped.put("selectMyFeedback", "反馈详情");

        for (Map.Entry<String, String> e : scoped.entrySet())
        {
            String body = statementBody(xml, e.getKey());
            assertNotNull(body, "缺少 statement: " + e.getKey());
            // 收紧断言：不能只要求正文出现 "user_id"（列清单、注释里出现同样会通过）。
            // 必须存在真正的归属判定：过滤子句中的 user_id = #{...}，
            // 或 INSERT 时把 user_id 列直接绑定到 #{userId} 占位符（如 upsertPreference）。
            assertTrue(bindsOwnerInSql(body),
                    e.getValue() + "(" + e.getKey() + ") 必须在 SQL 中以 user_id = #{...} 绑定归属"
                            + "（INSERT 场景需将 user_id 列绑定 #{userId}），禁止先查全量再内存过滤");
        }
    }

    /** 归属判定：过滤子句中的 user_id = #{userId} 等值绑定，或 INSERT 的 user_id 列取自 #{userId}。
     *  自身剥离 XML 注释，因此「仅在注释中提及 user_id」不会被判为已绑定。 */
    private static boolean bindsOwnerInSql(String body)
    {
        String clean = stripXmlComments(body);
        if (OWNER_FILTER.matcher(clean).find())
        {
            return true;
        }
        return OWNER_INSERT_COLUMN.matcher(clean).find() && OWNER_INSERT_VALUE.matcher(clean).find();
    }

    @Test
    void markReadIsConditionalUpdateForIdempotency() throws Exception
    {
        String xml = readResource("mapper/user/AppUserCenterMapper.xml");
        String markRead = statementBody(xml, "markRead");
        assertNotNull(markRead, "缺少 markRead");
        assertTrue(markRead.toLowerCase().contains("user_id"), "markRead 必须限定当前用户");
        assertTrue(markRead.replaceAll("\\s+", " ").contains("read_at IS NULL"),
                "markRead 必须是 read_at IS NULL 的条件更新，保证重复标记幂等且不丢更新");
    }

    @Test
    void preferencesUpsertIsIdempotentOnUniqueKey() throws Exception
    {
        String xml = readResource("mapper/user/AppUserCenterMapper.xml");
        String upsert = statementBody(xml, "upsertPreference");
        assertNotNull(upsert, "缺少 upsertPreference");
        assertTrue(upsert.toUpperCase().contains("ON DUPLICATE KEY UPDATE"),
                "通知偏好写入必须依赖唯一键的 ON DUPLICATE KEY UPDATE 保证幂等");
    }

    // ------------------------------------------------------------------
    // 2. 防越权：操作者身份只能来自认证上下文
    // ------------------------------------------------------------------

    @Test
    void appRequestDtosNeverCarryActorId() throws Exception
    {
        Path dtoDir = sourceRoot().resolve("dto");
        assertTrue(Files.isDirectory(dtoDir), "缺少 dto 目录");
        Pattern field = Pattern.compile("private\\s+\\w+\\s+(userId|user_id)\\s*;");
        StringBuilder offenders = new StringBuilder();
        try (Stream<Path> files = Files.list(dtoDir))
        {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith("Request.java")).toList())
            {
                String src = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = field.matcher(src);
                if (m.find())
                {
                    offenders.append(f.getFileName()).append(' ');
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "App 请求 DTO 不得携带 userId：操作者身份必须取自认证上下文（一票否决第 12 项）。违规: " + offenders);
    }

    @Test
    void appControllersDeriveIdentityFromSecurityContext() throws Exception
    {
        for (String c : List.of("controller/AppAuthController.java", "controller/AppUserController.java",
                "controller/AppMessageController.java", "controller/AppFeedbackController.java"))
        {
            String src = read(c);
            assertTrue(src.contains("currentIdentity()") || src.contains("currentUserId()"),
                    c + " 必须通过 currentIdentity()/currentUserId() 从安全上下文取身份");
            assertFalse(Pattern.compile("@RequestParam[^)]*userId").matcher(src).find(),
                    c + " 不得从 @RequestParam 取 userId");
            assertFalse(src.contains("getParameter(\"userId\")"), c + " 不得从原始请求参数取 userId");
        }
    }

    @Test
    void appControllersDoNotRelyOnPcPermissionAnnotations() throws Exception
    {
        // App 端由 AppAuthSecurityConfig 过滤链治理；PC 权限标识不应出现在 App 控制器上
        // （否则会出现「只在前端隐藏按钮、后端权限口径混乱」的问题）。
        for (String c : List.of("controller/AppAuthController.java", "controller/AppUserController.java",
                "controller/AppMessageController.java", "controller/AppFeedbackController.java"))
        {
            String src = read(c);
            assertFalse(src.contains("@PreAuthorize"),
                    c + " 不应使用 PC @PreAuthorize 权限标识，App 端鉴权由 AppAuthSecurityConfig 承担");
        }
    }

    // ------------------------------------------------------------------
    // 3. 事务：写操作必须标注 @Transactional
    // ------------------------------------------------------------------

    /**
     * 断言助手自身的有效性验证（防「断言过松导致空转」）。
     *
     * 这些反例正是本轮复核指出的风险：仅检查正文出现 "user_id"、或仅向前固定字符数
     * 搜索 @Transactional，都会把不合规代码判为通过。
     */
    @Test
    void ownerBindingCheckRejectsNonCompliantSql()
    {
        // 不合规：user_id 只出现在列清单，没有归属过滤
        assertFalse(bindsOwnerInSql(
                "SELECT r.user_id AS owner FROM user_notification_receiver r WHERE r.id = #{messageId}"),
                "仅在列清单中出现 user_id 时不得判为已绑定归属");
        // 不合规：完全没有归属过滤条件
        assertFalse(bindsOwnerInSql(
                "SELECT id FROM t WHERE id = #{id}"),
                "无归属过滤条件时不得判为已绑定归属");
        // 不合规：过滤条件绑定的不是认证上下文占位符 #{userId}
        assertFalse(bindsOwnerInSql(
                "SELECT id FROM t WHERE r.user_id = #{ownerId}"),
                "归属必须绑定 #{userId}，绑定其它占位符（如调用方传入的 ownerId）不得判为已绑定");
        assertFalse(bindsOwnerInSql(
                "UPDATE t SET a = 1 WHERE user_id = #{id} AND del_flag = '0'"),
                "归属占位符必须名为 userId");
        // 不合规：只在 XML 注释里写了归属条件
        assertFalse(bindsOwnerInSql(
                "<!-- SELECT id FROM t WHERE user_id = #{userId} --> SELECT id FROM t WHERE id = #{id}"),
                "XML 注释中的 user_id = #{userId} 不得判为已绑定归属");
        // 合规：过滤子句等值绑定（带别名）
        assertTrue(bindsOwnerInSql("SELECT id FROM t WHERE r.user_id = #{userId}"));
        // 合规：AND 子句等值绑定（无别名）
        assertTrue(bindsOwnerInSql("UPDATE t SET a = 1 WHERE user_id = #{userId} AND del_flag = '0'"));
        // 合规：占位符两侧空白容忍
        assertTrue(bindsOwnerInSql("SELECT id FROM t WHERE user_id = #{ userId }"));
        // 合规：INSERT 将 user_id 列绑定到 #{userId}
        assertTrue(bindsOwnerInSql(
                "INSERT INTO user_notification_preference (user_id, channel) VALUES (#{userId}, #{channel})"));
        // 不合规：INSERT 的 user_id 来自请求体字段而非认证上下文占位符
        assertFalse(bindsOwnerInSql(
                "INSERT INTO user_notification_preference (user_id, channel) VALUES (#{ownerId}, #{channel})"),
                "user_id 必须取自 #{userId}，不得取自调用方提供的字段");
    }

    @Test
    void transactionalCheckDoesNotInheritPreviousMethodAnnotation()
    {
        String src = "class X {\n"
                + "    @Transactional\n"
                + "    public void first() { }\n"
                + "\n"
                + "    public void second() { }\n"
                + "}\n";
        assertTrue(isTransactionalMethod(src, "first"), "first 自身标注了 @Transactional");
        assertFalse(isTransactionalMethod(src, "second"),
                "second 未标注 @Transactional，不得因前一方法的注解而误判通过");
    }

    @Test
    void mutatingServiceMethodsAreTransactional() throws Exception
    {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("service/AppRealNameService.java", List.of("submit", "resubmit"));
        expected.put("service/AppPhoneChangeService.java", List.of("confirm"));
        expected.put("service/AppAuthenticationService.java",
                List.of("smsLogin", "passwordLogin", "register", "setPassword", "changePassword", "resetPassword"));
        expected.put("service/UserMessageService.java", List.of("markRead", "markAllRead", "updatePreferences"));
        expected.put("service/UserFeedbackService.java", List.of("create"));
        expected.put("service/AppUserProfileService.java", List.of("updateProfile"));

        for (Map.Entry<String, List<String>> e : expected.entrySet())
        {
            String src = read(e.getKey());
            for (String method : e.getValue())
            {
                assertTrue(isTransactionalMethod(src, method),
                        e.getKey() + "#" + method + " 是写操作，必须标注 @Transactional（避免部分提交）");
            }
        }
    }

    /**
     * 判断 @Transactional 是否作用于该方法本身。
     *
     * 收紧实现：只取该方法签名之前、且上一个成员（以 } 或 ; 结束）之后的**注解块**，
     * 在块内去掉注释后再查找 @Transactional。这样不会像「向前固定 400 字符」那样，
     * 把上一个方法的注解误算到当前方法头上，也不会因块内出现 { 而误判为方法体。
     */
    private static boolean isTransactionalMethod(String src, String method)
    {
        Pattern p = Pattern.compile("public\\s+[\\w<>,\\[\\]. ]+\\s+" + Pattern.quote(method) + "\\s*\\(");
        Matcher m = p.matcher(src);
        while (m.find())
        {
            int idx = m.start();
            // 边界取「上一个成员结束」：前一个 } 或 ; 之后即当前成员的注解区
            int cut = Math.max(src.lastIndexOf('}', idx), src.lastIndexOf(';', idx));
            String block = src.substring(cut + 1, idx);
            // 首个成员之前可能还包含类体的左花括号；把边界推进到最后一个 { 之后。
            // （注解数组如 @Log(excludeParamNames = { "x" }) 也含 {，同样被安全跳过。）
            int brace = block.lastIndexOf('{');
            if (brace >= 0)
            {
                block = block.substring(brace + 1);
            }
            block = block.replaceAll("(?s)/\\*.*?\\*/", " ")   // 去掉块注释/Javadoc
                         .replaceAll("//[^\\n]*", " ");        // 去掉行注释
            if (block.contains("}"))
            {
                continue; // 边界不可靠时跳过而非误判
            }
            if (Pattern.compile("@Transactional\\b").matcher(block).find())
            {
                return true;
            }
        }
        return false;
    }
}
