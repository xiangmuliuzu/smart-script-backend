package com.smartscript.platform.user.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

/**
 * A 模块 PC 统一登录：AppUserMapper SQL 静态校验（不连库，仅 MyBatis 解析）。
 * 重点约束 selectByLoginIdentifier 的账号域安全语义：
 * 未删除账号、用户名/手机号双通道，供 PC 统一登录按 user_type 分流。
 */
class PcLoginMapperSqlTest
{
    private static final String MAPPER_RESOURCE = "mapper/user/AppUserMapper.xml";

    private Configuration parseMapper() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(),
                new org.apache.ibatis.datasource.unpooled.UnpooledDataSource()));
        configuration.getTypeAliasRegistry().registerAlias("AppUserRecord",
                com.smartscript.platform.user.domain.AppUserRecord.class);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(MAPPER_RESOURCE))
        {
            assertNotNull(in, "mapper resource missing: " + MAPPER_RESOURCE);
            new XMLMapperBuilder(in, configuration, MAPPER_RESOURCE, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    @Test
    void xmlParsesAndDeclaresLoginIdentifierStatement() throws Exception
    {
        Configuration configuration = parseMapper();
        String ns = "com.smartscript.platform.user.mapper.AppUserMapper.";
        for (String statement : new String[] {
                "selectByPhone", "selectByLoginIdentifier", "selectById",
                "insertAppUser", "updatePassword", "updateLoginInfo", "selectRealNameStatus" })
        {
            assertTrue(configuration.hasStatement(ns + statement), "missing statement: " + statement);
        }
    }

    @Test
    void selectByLoginIdentifierExcludesDeletedAndMatchesBothChannels() throws Exception
    {
        Configuration configuration = parseMapper();
        MappedStatement ms = configuration.getMappedStatement(
                "com.smartscript.platform.user.mapper.AppUserMapper.selectByLoginIdentifier");
        BoundSql bound = ms.getBoundSql(java.util.Map.of("identifier", "someone"));
        String sql = bound.getSql().replaceAll("\\s+", " ");
        assertTrue(sql.contains("del_flag = '0'"), "must exclude deleted accounts");
        assertTrue(sql.contains("user_name ="), "must match by user_name");
        assertTrue(sql.contains("phonenumber ="), "must match by phone");
        // 用户名/手机号撞值时必须确定性地取用户名命中的账号，保证 user_type 分流稳定
        assertTrue(sql.contains("ORDER BY CASE WHEN user_name ="),
                "must prefer user_name match on collision");
        assertTrue(sql.contains("LIMIT 1"), "must return at most one row");
    }
}
