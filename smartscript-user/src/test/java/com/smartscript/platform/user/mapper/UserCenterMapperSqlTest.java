package com.smartscript.platform.user.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
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
 * A5 用户中心 Mapper 结构与 SQL 安全静态校验。
 *
 * 不连接数据库：只用 MyBatis 自身解析 XML 并生成 SQL，验证
 *   - XML 结构合法（拒绝运行时才暴露的映射错误）；
 *   - 每个接口方法都有对应 statement；
 *   - 「我的」语义由 SQL 的 user_id 条件强制，越权查询不可能因参数而放宽；
 *   - 已读等写操作为条件更新（幂等）。
 */
class UserCenterMapperSqlTest
{
    private static final String MAPPER_RESOURCE = "mapper/user/AppUserCenterMapper.xml";

    private Configuration parseMapper() throws Exception
    {
        Configuration configuration = new Configuration();
        // MyBatis 要求 Environment 绑定 dataSource，即使本测试不真正连接数据库
        javax.sql.DataSource ds = new org.apache.ibatis.datasource.unpooled.UnpooledDataSource();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), ds));
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(MAPPER_RESOURCE))
        {
            assertNotNull(in, "mapper resource missing: " + MAPPER_RESOURCE);
            new XMLMapperBuilder(in, configuration, MAPPER_RESOURCE, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    @Test
    void xmlParsesAndDeclaresAllInterfaceMethods() throws Exception
    {
        Configuration configuration = parseMapper();
        String ns = "com.smartscript.platform.user.mapper.AppUserCenterMapper.";
        for (String statement : new String[] {
                "updateNickName", "updateAvatar", "countPhoneTaken", "updatePhone", "insertPhoneChangeLog",
                "selectLatestRealName", "selectLatestRealNameStatus", "insertRealName",
                "selectMyMessages", "selectMyMessage", "countUnread", "countUnreadGroupByType",
                "markRead", "markAllRead",
                "selectPreferences", "upsertPreference", "deletePreference",
                "insertFeedback", "selectMyFeedbackPage", "selectMyFeedback" })
        {
            assertTrue(configuration.hasStatement(ns + statement), "missing statement: " + statement);
        }
    }

    @Test
    void myMessageQueriesBindUserId() throws Exception
    {
        Configuration configuration = parseMapper();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 4242L);
        params.put("type", "SYSTEM");

        String listSql = sqlOf(configuration,
                "com.smartscript.platform.user.mapper.AppUserCenterMapper.selectMyMessages", params);
        assertTrue(listSql.contains("r.user_id = ?"), "list must filter by receiver user_id: " + listSql);
        assertFalse(listSql.contains("4242"), "user_id must be bound as parameter, not concatenated");

        String detailSql = sqlOf(configuration,
                "com.smartscript.platform.user.mapper.AppUserCenterMapper.selectMyMessage", params);
        assertTrue(detailSql.contains("r.user_id = ?"), "detail must filter by receiver user_id");

        String unreadSql = sqlOf(configuration,
                "com.smartscript.platform.user.mapper.AppUserCenterMapper.countUnread", params);
        assertTrue(unreadSql.contains("r.user_id = ?"), "unread count must filter by receiver user_id");
    }

    @Test
    void statusFiltersBindInsteadOfConcat() throws Exception
    {
        Configuration configuration = parseMapper();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 1L);
        params.put("status", "CLOSED");

        String feedbackSql = sqlOf(configuration,
                "com.smartscript.platform.user.mapper.AppUserCenterMapper.selectMyFeedbackPage", params);
        assertTrue(feedbackSql.contains("f.user_id = ?"), "feedback list must filter by owner");
        assertTrue(feedbackSql.contains("f.status = ?"), "feedback status must be bound");
        assertFalse(feedbackSql.contains("CLOSED"), "status must not be concatenated into SQL");
    }

    @Test
    void markReadIsIdempotentConditionalUpdate() throws Exception
    {
        Configuration configuration = parseMapper();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 7L);
        params.put("messageId", 9L);
        params.put("now", new java.util.Date());

        String sql = sqlOf(configuration,
                "com.smartscript.platform.user.mapper.AppUserCenterMapper.markRead", params);
        assertTrue(sql.contains("read_at IS NULL"), "already-read rows must not be updated again");
        assertTrue(sql.contains("user_id = ?"), "已读只能作用于当前用户的收件记录");
    }

    @Test
    void phoneChangeLogNeverSelectsOrWritesCodes() throws Exception
    {
        Configuration configuration = parseMapper();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 1L);
        String sql = sqlOf(configuration,
                "com.smartscript.platform.user.mapper.AppUserCenterMapper.insertPhoneChangeLog", params);
        // 审计只写掩码列，绝不出现验证码/明文号码列
        assertTrue(sql.contains("old_phone_mask"));
        assertTrue(sql.contains("new_phone_mask"));
        assertFalse(sql.toLowerCase().contains("code"));
    }

    @Test
    void realNameDetailIsTheOnlyPlaceSelectingMaterialRef() throws Exception
    {
        Configuration configuration = parseMapper();
        Map<String, Object> params = new HashMap<>();
        params.put("userId", 1L);

        String latest = sqlOf(configuration,
                "com.smartscript.platform.user.mapper.AppUserCenterMapper.selectLatestRealName", params);
        assertTrue(latest.contains("material_ref"), "本人可读自己的材料引用");

        String statusOnly = sqlOf(configuration,
                "com.smartscript.platform.user.mapper.AppUserCenterMapper.selectLatestRealNameStatus", params);
        assertFalse(statusOnly.contains("material_ref"), "状态查询不得携带材料引用");
    }

    private static String sqlOf(Configuration configuration, String statementId, Map<String, Object> params)
    {
        MappedStatement statement = configuration.getMappedStatement(statementId);
        BoundSql boundSql = statement.getBoundSql(params);
        return boundSql.getSql().replaceAll("\\s+", " ");
    }
}
