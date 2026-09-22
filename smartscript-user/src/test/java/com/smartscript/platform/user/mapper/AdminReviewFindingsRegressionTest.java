package com.smartscript.platform.user.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

/**
 * A4 独立评审整改的回归测试。
 *
 * 每条对应一个评审发现，防止修复被后续改动悄悄回退。
 * 不连接数据库：用 MyBatis 解析 XML 并生成 SQL，加上源码结构断言。
 */
class AdminReviewFindingsRegressionTest
{
    private static final String[] MAPPER_RESOURCES = {
        "mapper/user/AppUserAdminMapper.xml",
        "mapper/user/RealNameAdminMapper.xml",
        "mapper/user/AuthorCapabilityAdminMapper.xml",
        "mapper/user/AdminNotificationMapper.xml",
        "mapper/user/AdminFeedbackMapper.xml"
    };

    private Configuration parseAllMappers() throws Exception
    {
        Configuration configuration = new Configuration();
        javax.sql.DataSource ds = new org.apache.ibatis.datasource.unpooled.UnpooledDataSource();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        for (String resource : MAPPER_RESOURCES)
        {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource))
            {
                assertNotNull(in, "mapper resource missing: " + resource);
                new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }

    private String normalize(String sql)
    {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private String readMain(String relative) throws Exception
    {
        Path p = Paths.get("src/main").resolve(relative);
        if (!Files.exists(p))
        {
            p = Paths.get("D:/build/smart-script-backend/smartscript-user/src/main").resolve(relative);
        }
        assertTrue(Files.exists(p), "missing source: " + relative);
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // 评审 #2：不存在的角色 ID 不能通过授权边界检查
    // ------------------------------------------------------------------

    /**
     * 以「请求的 ID 集合」为基准左连接 sys_role。
     * 若改回直接过滤 sys_role，不存在的 ID 不会产生行，COUNT 为 0 即被误判为可授权。
     */
    @Test
    void nonExistentRoleIdIsCountedAsNonGrantable() throws Exception
    {
        Configuration configuration = parseAllMappers();
        MappedStatement ms = configuration.getMappedStatement(
                "com.smartscript.platform.user.mapper.AppUserAdminMapper.countNonGrantableRoles");
        String sql = normalize(ms.getBoundSql(Map.of("roleIds", List.of(999999L))).getSql());

        // 必须是左连接形式，且以「角色不存在」为可计数条件
        assertTrue(sql.contains("left join sys_role"), "must left join sys_role from requested ids: " + sql);
        assertTrue(sql.contains("r.role_id is null"),
                "non-existent role must be counted as non-grantable: " + sql);
        assertFalse(sql.contains("where r.role_id in"),
                "must not filter sys_role directly (that lets missing ids pass): " + sql);
    }

    // ------------------------------------------------------------------
    // 评审 #3：实名审核必须校验客户端 expectedStatus
    // ------------------------------------------------------------------

    @Test
    void realNameDecideHonoursClientExpectedStatus() throws Exception
    {
        String service = readMain("java/com/smartscript/platform/user/service/RealNameReviewService.java");
        assertTrue(service.contains("resolveExpectedStatus"),
                "service must resolve and validate client expectedStatus");
        assertTrue(service.contains("clientExpectedStatus"),
                "decide must accept expectedStatus from the caller");
        // 条件更新使用同一 expected 值，保证并发与错误值都不会偶然成功
        assertTrue(service.contains("mapper.decideIfPending(applicationId, expectedStatus"),
                "conditional update must use the resolved expectedStatus");

        String controller = readController("A4AdminController.java");
        assertTrue(controller.contains("body.get(\"expectedStatus\")"),
                "controller must read expectedStatus from the request body");
    }

    // ------------------------------------------------------------------
    // 评审 #4：材料/附件必须是服务端强制的短时令牌，且不下发永久地址
    // ------------------------------------------------------------------

    @Test
    void materialRefsAreOpaqueShortLivedTokensOnly() throws Exception
    {
        String tokenService = readMain(
                "java/com/smartscript/platform/user/service/MaterialAccessTokenService.java");
        assertTrue(tokenService.contains("TOKEN_TTL_SECONDS"),
                "token service must enforce a TTL on the server side");
        // 一次性由 Lua 脚本在 Redis 内原子完成，不再是先 GET 再 deleteObject
        assertTrue(tokenService.contains("redis.call('DEL'"),
                "token must be single-use (deleted atomically in the redeem script)");
        assertTrue(tokenService.contains("fingerprint"),
                "raw ref must not be logged; only a fingerprint is acceptable");

        // 两个业务服务都不得再把原始引用拼进返回值
        for (String rel : List.of(
                "java/com/smartscript/platform/user/service/RealNameReviewService.java",
                "java/com/smartscript/platform/user/service/UserFeedbackAdminService.java"))
        {
            String src = readMain(rel);
            assertFalse(src.contains("\"?exp=\""),
                    rel + " must not append exp to a raw ref (that leaks the permanent address)");
            assertTrue(src.contains("materialTokenService.issue"),
                    rel + " must issue an opaque token instead");
        }
    }

    @Test
    void materialRedeemEndpointRequiresDetailPermissionAndIsAudited() throws Exception
    {
        String controller = readController("A4AdminController.java");
        assertTrue(controller.contains("/material-refs/redeem"),
                "a redeem endpoint must exist so tokens can be exchanged");
        assertTrue(controller.contains("redeemMaterialRef"),
                "redeem handler missing");
        // 兑换属于敏感访问，必须有权限注解与审计
        int idx = controller.indexOf("redeemMaterialRef");
        String before = controller.substring(Math.max(0, idx - 700), idx);
        assertTrue(before.contains("hasAnyPermi"),
                "redeem must require a query permission");
        assertTrue(before.contains("@Log"),
                "redeem must be audited");
    }

    // ------------------------------------------------------------------
    // 评审 #5：消息幂等并发竞态必须收敛为确定结果
    // ------------------------------------------------------------------

    @Test
    void notificationCreateHandlesConcurrentDuplicateKey() throws Exception
    {
        String src = readMain(
                "java/com/smartscript/platform/user/service/UserNotificationAdminService.java");
        assertTrue(src.contains("catch (DuplicateKeyException"),
                "concurrent same-requestId inserts must be caught, not surfaced as 500");
        assertTrue(src.contains("resolveConcurrentWinnerInNewTransaction"),
                "must re-read the winning row and return reuse/conflict");
        // 必须用 TransactionTemplate：同类的 REQUIRES_NEW 方法属于自调用，
        // 不经过代理，传播级别不会生效，冲突后仍会读到 rollback-only 事务
        assertTrue(src.contains("TransactionTemplate"),
                "winner lookup must use a real new transaction (not self-invocation)");
        assertTrue(src.contains("PROPAGATION_REQUIRES_NEW"),
                "winner lookup must run in an independent transaction");
    }

    // ------------------------------------------------------------------
    // 评审 #6：敏感材料/附件详情读取必须有访问审计
    // ------------------------------------------------------------------

    @Test
    void sensitiveDetailReadsAreAudited() throws Exception
    {
        String controller = readController("A4AdminController.java");
        for (String title : List.of("A4-实名材料访问", "A4-反馈附件访问"))
        {
            assertTrue(controller.contains(title),
                    "missing access audit for sensitive read: " + title);
        }
    }

    // ------------------------------------------------------------------
    // 评审 #7：反馈重复回复的幂等判断必须比较正文
    // ------------------------------------------------------------------

    @Test
    void feedbackReplyIdempotencyComparesContent() throws Exception
    {
        String src = readMain(
                "java/com/smartscript/platform/user/service/UserFeedbackAdminService.java");
        assertTrue(src.contains("selectFeedbackReply"),
                "reply idempotency must compare the stored reply content");
        assertFalse(src.contains("return AppAdminConstants.FEEDBACK_REPLIED.equals(current) && reply != null;"),
                "must not treat any non-empty reply on a REPLIED row as equivalent");

        Configuration configuration = parseAllMappers();
        assertTrue(configuration.hasStatement(
                "com.smartscript.platform.user.mapper.AdminFeedbackMapper.selectFeedbackReply"),
                "selectFeedbackReply statement must exist");
    }

    // ------------------------------------------------------------------
    // 变量名不得引入角色名绕过语义（lint-static 同规则）
    // ------------------------------------------------------------------

    @Test
    void feedbackHandlersCarryExpectedStatusIntoWhere() throws Exception
    {
        Configuration configuration = parseAllMappers();
        MappedStatement ms = configuration.getMappedStatement(
                "com.smartscript.platform.user.mapper.AdminFeedbackMapper.replyIfAllowed");
        Map<String, Object> p = new HashMap<>();
        p.put("feedbackId", 1L);
        p.put("expectedStatus", "SUBMITTED");
        p.put("allowedFrom", List.of("SUBMITTED", "PROCESSING"));
        p.put("reply", "x");
        p.put("handlerId", 1L);
        String sql = normalize(ms.getBoundSql(p).getSql());
        assertTrue(sql.contains("status = ?"), "expectedStatus must bind into WHERE: " + sql);
        assertTrue(sql.contains("status in"), "allowedFrom guard missing: " + sql);
    }

    /**
     * A4 管理接口必须完整存在。
     *
     * 本测试来自一次真实事故：重构 Controller 时按行区间替换，
     * 整段 App 用户接口被静默删除，而编译仍然通过（Java 允许缺少方法），
     * 直到接口矩阵返回「No static resource」才暴露。这里用契约中的
     * 路径逐一断言，防止再次发生。
     */
    @Test
    void allContractEndpointsArePresent() throws Exception
    {
        String controller = readController("A4AdminController.java");
        String[] required = {
            "@GetMapping(\"/app-users\")",
            "@GetMapping(\"/app-users/grantable-roles\")",
            "@GetMapping(\"/app-users/{userId}\")",
            "@PutMapping(\"/app-users/{userId}/status\")",
            "@PutMapping(\"/app-users/{userId}/roles\")",
            "@GetMapping(\"/real-name-applications\")",
            "@GetMapping(\"/real-name-applications/{applicationId}\")",
            "@PutMapping(\"/real-name-applications/{applicationId}/decision\")",
            "@GetMapping(\"/author-capabilities\")",
            "@PutMapping(\"/author-capabilities/{userId}\")",
            "@GetMapping(\"/notifications\")",
            "@GetMapping(\"/notifications/{notificationId}\")",
            "@PostMapping(\"/notifications\")",
            "@GetMapping(\"/feedback\")",
            "@GetMapping(\"/feedback/{feedbackId}\")",
            "@PutMapping(\"/feedback/{feedbackId}/handle\")",
            "@PostMapping(\"/material-refs/redeem\")"
        };
        List<String> missing = new java.util.ArrayList<>();
        for (String path : required)
        {
            if (!controller.contains(path))
            {
                missing.add(path);
            }
        }
        assertTrue(missing.isEmpty(), "missing A4 endpoints: " + missing);
    }

    private String readController(String name) throws Exception
    {
        Path p = Paths.get("D:/build/smart-script-backend/ruoyi-admin/src/main/java/com/ruoyi/web/controller/a4")
                .resolve(name);
        if (!Files.exists(p))
        {
            p = Paths.get("../ruoyi-admin/src/main/java/com/ruoyi/web/controller/a4").toAbsolutePath().normalize()
                    .resolve(name);
        }
        assertTrue(Files.exists(p), "missing controller: " + name);
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // 复审第 1 项（一票否决）：令牌与材料地址不得进入操作日志
    // ------------------------------------------------------------------

    @Test
    void sensitiveEndpointsDisableResponseLogging() throws Exception
    {
        String controller = readController("A4AdminController.java");
        // 三个含令牌/材料字段的接口都必须禁止记录响应体
        for (String title : List.of("A4-敏感材料兑换", "A4-实名材料访问", "A4-反馈附件访问"))
        {
            int idx = controller.indexOf("title = \"" + title + "\"");
            assertTrue(idx > 0, "endpoint not found: " + title);
            int annEnd = controller.indexOf("@", idx);
            String annotation = controller.substring(idx, annEnd > idx ? annEnd : idx + 400);
            assertTrue(annotation.contains("isSaveResponseData = false"),
                    title + " must set isSaveResponseData = false (response carries token/material)");
        }
    }

    @Test
    void redeemTokenTravelsInBodyNotUrlPath() throws Exception
    {
        String controller = readController("A4AdminController.java");
        // 令牌放在 URL 路径会被 oper_url 完整记录，必须用 POST + 请求体
        assertFalse(controller.contains("/material-refs/{token}"),
                "token must not be a path variable (oper_url would record it)");
        assertTrue(controller.contains("@PostMapping(\"/material-refs/redeem\")"),
                "redeem must be a POST with the token in the body");
        int idx = controller.indexOf("A4-敏感材料兑换");
        String annotation = controller.substring(Math.max(0, idx - 300), idx + 200);
        assertTrue(annotation.contains("excludeParamNames"),
                "redeem must exclude the token parameter from request logging");
    }

    // ------------------------------------------------------------------
    // 复审第 2 项：令牌必须绑定材料类型
    // ------------------------------------------------------------------

    @Test
    void tokenIsBoundToMaterialKind() throws Exception
    {
        String service = readMain(
                "java/com/smartscript/platform/user/service/MaterialAccessTokenService.java");
        assertTrue(service.contains("KIND_REAL_NAME") && service.contains("KIND_FEEDBACK"),
                "token service must define material kinds");
        assertTrue(service.contains("permissionForKind"),
                "service must map material kind to required permission");
        // 类型与权限必须参与判定，否则持有反馈权限即可兑换实名材料
        assertTrue(service.contains("requiredPerm"),
                "redeem must check the permission implied by the stored kind");
        String controller = readController("A4AdminController.java");
        assertTrue(controller.contains("getPermissions()"),
                "controller must pass the caller's granted permissions for kind checking");
    }

    // ------------------------------------------------------------------
    // 复审第 3 项：一次性兑换必须原子
    // ------------------------------------------------------------------

    @Test
    void tokenRedeemIsAtomic() throws Exception
    {
        String service = readMain(
                "java/com/smartscript/platform/user/service/MaterialAccessTokenService.java");
        // 先 GET 再 DEL 不是原子操作，并发下会多次兑换成功
        assertTrue(service.contains("REDEEM_SCRIPT"),
                "redeem must use a Lua script");
        assertTrue(service.contains("redis.call('GET'") && service.contains("redis.call('DEL'"),
                "Lua script must GET and DEL in one atomic step");
        assertTrue(service.contains("DefaultRedisScript"),
                "script must be executed via DefaultRedisScript");
        // 不得回退为「先 GET 再 DELETE」的非原子实现
        assertFalse(service.contains("redisCache.getCacheObject(key)"),
                "must not fall back to non-atomic GET then DELETE");
    }

    /**
     * 兑换与内容接口的响应都不得把真实引用交给客户端。
     *
     * 注意：`getRawRefInternal()` 允许出现在**服务端内部**调用中
     * （例如用它签发一次性访问链接、读文件），禁止的是它进入响应体。
     * 因此这里断言的是"响应字段集合"而非简单的字符串出现。
     */
    @Test
    void redeemAndContentResponsesNeverCarryRawRef() throws Exception
    {
        String controller = readController("A4AdminController.java");

        // 兑换响应字段：只允许 kind / accessUrl / expiresInSeconds
        int idx = controller.indexOf("public AjaxResult redeemMaterialRef");
        assertTrue(idx > 0, "redeem endpoint missing");
        String redeemBody = controller.substring(idx, Math.min(controller.length(), idx + 1800));
        int successIdx = redeemBody.indexOf("AjaxResult.success(Map.of(");
        assertTrue(successIdx > 0, "redeem response map missing");
        String responseMap = redeemBody.substring(successIdx,
                redeemBody.indexOf("));", successIdx));
        assertFalse(responseMap.contains("RawRef") || responseMap.contains("getRawRef"),
                "raw ref must not be placed into the redeem response map");

        // 访问链接不得包含材料引用本身
        assertTrue(controller.contains("materialLinkService.issue(result.getKind(), result.getRawRefInternal())"),
                "access link must be issued from the internal ref server-side only");

        // 内容接口必须消费链接（一次性），而不是直接按引用读取
        int contentIdx = controller.indexOf("readMaterialContent");
        assertTrue(contentIdx > 0, "content endpoint missing");
        String contentBody = controller.substring(contentIdx,
                Math.min(controller.length(), contentIdx + 2000));
        assertTrue(contentBody.contains("materialLinkService.consume"),
                "content endpoint must consume the one-time link");
        assertTrue(contentBody.contains("materialGateway.open"),
                "content endpoint must stream bytes via the gateway");
        assertFalse(contentBody.contains("AjaxResult.success"),
                "content endpoint must return bytes, not a JSON success envelope");
    }

    /** 断言失败时的可读信息保留。 */
    @Test
    void mapperSetIsComplete() throws Exception
    {
        Configuration configuration = parseAllMappers();
        assertEquals(5, MAPPER_RESOURCES.length);
        assertTrue(configuration.hasStatement(
                "com.smartscript.platform.user.mapper.AppUserAdminMapper.countNonGrantableRoles"));
    }
}
