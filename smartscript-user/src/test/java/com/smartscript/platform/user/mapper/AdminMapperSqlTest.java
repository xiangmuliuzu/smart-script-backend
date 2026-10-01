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
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

/**
 * A4 P1：管理域 Mapper 结构与 SQL 安全静态校验。
 *
 * 不连接数据库，只用 MyBatis 自身解析 XML 并生成 SQL，验证：
 *   - XML 结构合法（拒绝运行时才暴露的映射错误）；
 *   - 每个方法都有对应 statement；
 *   - 动态 SQL 生成的语句使用参数绑定，且账号域限定不可被参数放宽。
 */
class AdminMapperSqlTest
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
        // MyBatis 要求 Environment 绑定 dataSource，即使本测试不真正连接数据库。
        // 用 UnpooledDataSource 提供一个惰性数据源即可满足解析与 SQL 生成。
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

    @Test
    void allAdminMappersParse() throws Exception
    {
        Configuration configuration = parseAllMappers();
        assertTrue(configuration.hasStatement(
                "com.smartscript.platform.user.mapper.AppUserAdminMapper.selectAppUserPage"));
        assertTrue(configuration.hasStatement(
                "com.smartscript.platform.user.mapper.RealNameAdminMapper.decideIfPending"));
        assertTrue(configuration.hasStatement(
                "com.smartscript.platform.user.mapper.AuthorCapabilityAdminMapper.upsertCapability"));
        assertTrue(configuration.hasStatement(
                "com.smartscript.platform.user.mapper.AdminNotificationMapper.insertReceivers"));
        assertTrue(configuration.hasStatement(
                "com.smartscript.platform.user.mapper.AdminFeedbackMapper.replyIfAllowed"));
    }

    /**
     * 账号域限定必须无条件出现在生成的 SQL 中：即使参数完全为空，
     * 也不得放开到全部账号类型（USER-04、一票否决项）。
     */
    @Test
    void appUserPageAlwaysRestrictsAccountDomain() throws Exception
    {
        Configuration configuration = parseAllMappers();
        MappedStatement ms = configuration.getMappedStatement(
                "com.smartscript.platform.user.mapper.AppUserAdminMapper.selectAppUserPage");
        BoundSql boundSql = ms.getBoundSql(new HashMap<>());
        String sql = normalize(boundSql.getSql());
        assertTrue(sql.contains("user_type in ('01', '02', '03')"),
                "account domain restriction missing when no filters: " + sql);
        assertTrue(sql.contains("del_flag = '0'"), "del_flag filter missing: " + sql);
        assertFalse(sql.contains("'00'"), "PC admin domain must not be selectable: " + sql);
    }

    /** 表意相反但必须成立的断言：过滤条件全部给定时，域限定仍然存在。 */
    @Test
    void appUserPageRestrictsDomainWithAllFilters() throws Exception
    {
        Configuration configuration = parseAllMappers();
        MappedStatement ms = configuration.getMappedStatement(
                "com.smartscript.platform.user.mapper.AppUserAdminMapper.selectAppUserPage");
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "x");
        params.put("status", "0");
        params.put("userType", "01");
        params.put("roleCode", "creator");
        params.put("realNameStatus", "APPROVED");
        params.put("authorCapability", true);
        params.put("beginTime", "2026-01-01");
        params.put("endTime", "2026-12-31");
        BoundSql boundSql = ms.getBoundSql(params);
        String sql = normalize(boundSql.getSql());
        assertTrue(sql.contains("user_type in ('01', '02', '03')"),
                "account domain restriction lost with filters: " + sql);
        // 关键字必须走参数绑定，不得内联进 SQL
        assertTrue(boundSql.getParameterMappings().size() > 0, "expected bound parameters");
        assertFalse(sql.contains("'x'"), "keyword must not be inlined into SQL: " + sql);
    }

    /** 手机号必须以掩码表达式产出，Mapper 不得查询完整号码列。 */
    @Test
    void appUserQueriesNeverSelectRawPhone() throws Exception
    {
        String xml = readResource("mapper/user/AppUserAdminMapper.xml");
        assertTrue(xml.contains("phoneMasked"), "expected masked phone mapping");
        assertFalse(xml.contains("AS phonenumber"), "raw phone must not be projected");
        assertFalse(xml.contains("u.phonenumber AS"), "raw phone must not be projected");
        // 唯一允许出现 phonenumber 的位置是掩码表达式与关键字模糊匹配
        long rawSelects = xml.lines()
                .filter(l -> l.contains("phonenumber") && l.contains("AS ") && !l.contains("CONCAT"))
                .count();
        assertEquals(0L, rawSelects, "unmasked phonenumber projection found");
    }

    /** 实名列表不得返回材料引用；材料只在详情查询中出现。 */
    @Test
    void realNameListNeverSelectsMaterial() throws Exception
    {
        String xml = readResource("mapper/user/RealNameAdminMapper.xml");
        String listBlock = selectBlock(xml, "selectApplicationPage");
        String detailBlock = selectBlock(xml, "selectApplicationDetail");
        // 前置注释会提到 material_ref，故断言只针对 <select> 元素体
        assertFalse(listBlock.contains("material_ref"),
                "list query must not select material_ref");
        assertTrue(detailBlock.contains("rna.material_ref AS material_ref"),
                "detail query should select material_ref for permission-gated access");
    }

    /** 反馈列表不得返回附件引用；附件只在详情查询中出现。 */
    @Test
    void feedbackListNeverSelectsAttachment() throws Exception
    {
        String xml = readResource("mapper/user/AdminFeedbackMapper.xml");
        String listBlock = selectBlock(xml, "selectFeedbackPage");
        String detailBlock = selectBlock(xml, "selectFeedbackDetail");
        assertFalse(listBlock.contains("attachment_ref"),
                "list query must not select attachment_ref");
        assertTrue(detailBlock.contains("f.attachment_ref AS attachment_ref"),
                "detail query should select attachment_ref");
    }

    /**
     * 取出指定 statement 的完整 &lt;select&gt; 元素体。
     * 只包含元素本身，排除文件头部的说明性注释。
     */
    private String selectBlock(String xml, String statementId)
    {
        String marker = "id=\"" + statementId + "\"";
        int start = xml.indexOf(marker);
        assertTrue(start > 0, "statement not found: " + statementId);
        // 回退到该 statement 的起始标签，向前收进 </select> 结束处
        int open = xml.lastIndexOf("<select", start);
        int close = xml.indexOf("</select>", start);
        assertTrue(open > 0 && close > open, "malformed select element: " + statementId);
        return xml.substring(open, close);
    }

    /** 作者能力 Mapper 不得含任何 sys_user.user_type 写操作（CREATOR-04）。 */
    @Test
    void authorCapabilityNeverWritesUserType() throws Exception
    {
        String xml = readResource("mapper/user/AuthorCapabilityAdminMapper.xml");
        assertFalse(xml.contains("UPDATE sys_user"), "must not update sys_user");
        assertFalse(xml.contains("user_type ="), "must not write user_type");
    }

    /** 条件更新必须把 expectedStatus 放进 WHERE，保证并发无丢失更新。 */
    @Test
    void conditionalUpdatesCarryExpectedStatusInWhere() throws Exception
    {
        Configuration configuration = parseAllMappers();
        MappedStatement statusMs = configuration.getMappedStatement(
                "com.smartscript.platform.user.mapper.AppUserAdminMapper.updateStatusIfMatch");
        Map<String, Object> p = new HashMap<>();
        p.put("userId", 100L);
        p.put("expectedStatus", "0");
        p.put("status", "1");
        p.put("updateBy", "tester");
        String sql = normalize(statusMs.getBoundSql(p).getSql());
        assertTrue(sql.contains("status = ?") || sql.contains("status = ?"),
                "expectedStatus must bind into WHERE: " + sql);
        assertTrue(sql.contains("user_type in ('01', '02', '03')"),
                "status update must stay inside managed domain: " + sql);
    }

    /** 可授权角色清单必须只接受 app_grantable=1 且排除超级管理员。 */
    @Test
    void grantableRolesFilterByMarkerAndExcludeAdmin() throws Exception
    {
        Configuration configuration = parseAllMappers();
        MappedStatement ms = configuration.getMappedStatement(
                "com.smartscript.platform.user.mapper.AppUserAdminMapper.selectGrantableRoles");
        String sql = normalize(ms.getBoundSql(null).getSql());
        assertTrue(sql.contains("app_grantable = 1"),
                "grantable role list must require the DB marker: " + sql);
        assertTrue(sql.contains("status = '0'"), "grantable role must be enabled: " + sql);
        assertTrue(sql.contains("del_flag = '0'"), "grantable role must not be deleted: " + sql);
        assertTrue(sql.contains("role_id <> 1") || sql.contains("role_id != 1"),
                "super admin role_id must be excluded: " + sql);
        assertTrue(sql.contains("role_key <> 'admin'") || sql.contains("role_key != 'admin'"),
                "super admin role_key must be excluded: " + sql);
    }

    /**
     * 绕过测试：超级管理员角色必须被永久拒绝。
     *
     * 即使有人手工把 role_id=1 置为 app_grantable=1，SQL 的不可授权判定仍须命中它。
     * 断言 user_type='00' 之类的旁路口径不得取代这一硬约束。
     */
    @Test
    void superAdminRoleAlwaysRejectedEvenIfFlagged() throws Exception
    {
        Configuration configuration = parseAllMappers();
        MappedStatement ms = configuration.getMappedStatement(
                "com.smartscript.platform.user.mapper.AppUserAdminMapper.countNonGrantableRoles");
        String sql = normalize(ms.getBoundSql(Map.of("roleIds", java.util.List.of(1L))).getSql());
        assertTrue(sql.contains("role_id = 1"),
                "super admin must be rejected by role_id regardless of marker: " + sql);
        assertTrue(sql.contains("role_key = 'admin'"),
                "super admin must be rejected by role_key regardless of marker: " + sql);
        assertTrue(sql.contains("app_grantable <> 1"),
                "unflagged roles must count as non-grantable (default deny): " + sql);
    }

    /** 标记为 0 的角色必须被拒绝；授权校验不得回落为「无过滤即放行」。 */
    @Test
    void untaggedRoleIsDenied() throws Exception
    {
        Configuration configuration = parseAllMappers();
        MappedStatement ms = configuration.getMappedStatement(
                "com.smartscript.platform.user.mapper.AppUserAdminMapper.countNonGrantableRoles");
        String sql = normalize(ms.getBoundSql(Map.of("roleIds", java.util.List.of(2L))).getSql());
        assertTrue(sql.contains("app_grantable <> 1"),
                "role with marker 0 must be non-grantable: " + sql);
        assertFalse(sql.contains("1 = 1"),
                "authorization check must not degrade to unconditional allow: " + sql);
    }

    private String normalize(String sql)
    {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private String readResource(String resource) throws Exception
    {
        Path path = Paths.get("src/main/resources").resolve(resource);
        if (!Files.exists(path))
        {
            path = Paths.get("D:/build/smart-script-backend/smartscript-user/src/main/resources").resolve(resource);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
