package com.smartscript.platform.user.mapper;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class NicknameFilterRegressionTest
{
    @Test
    void allThreeListsIgnoreRetiredNicknameFilter() throws Exception
    {
        String[][] cases = {
            {"AppUserAdminMapper", "selectAppUserPage"},
            {"CreatorApplicationAdminMapper", "selectApplicationPage"},
            {"AdminFeedbackMapper", "selectFeedbackPage"}
        };
        for (String[] item : cases)
        {
            Configuration config = new Configuration();
            String resource = "mapper/user/" + item[0] + ".xml";
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource))
            {
                assertNotNull(in);
                new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse();
            }
            var statement = config.getMappedStatement("com.smartscript.platform.user.mapper." + item[0] + "." + item[1]);
            for (String nickname : new String[] {"222", "14786152186", "%' OR 1=1 --"})
            {
                var bound = statement.getBoundSql(Map.of("nickname", nickname));
                String sql = bound.getSql().replaceAll("\\s+", " ");
                assertEquals(statement.getBoundSql(Map.of()).getSql().replaceAll("\\s+", " "), sql);
                assertTrue(bound.getParameterMappings().isEmpty());
            }
            assertFalse(statement.getBoundSql(Map.of()).getSql().contains("u.nick_name = ?"));
        }
    }

    @Test
    void appUserKeywordOnlyMatchesPhoneWithBoundParameters() throws Exception
    {
        Configuration config = new Configuration();
        String resource = "mapper/user/AppUserAdminMapper.xml";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource))
        {
            assertNotNull(in);
            new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse();
        }
        var statement = config.getMappedStatement(
                "com.smartscript.platform.user.mapper.AppUserAdminMapper.selectAppUserPage");
        for (String keyword : new String[] {"14786152186", "222", "%' OR 1=1 --"})
        {
            var bound = statement.getBoundSql(Map.of("keyword", keyword, "nickname", "222"));
            String sql = bound.getSql().replaceAll("\\s+", " ");
            assertTrue(sql.contains("AND u.phonenumber LIKE CONCAT('%', ?, '%')"), sql);
            assertFalse(sql.contains("u.nick_name LIKE"), sql);
            assertFalse(sql.contains("u.user_name LIKE"), sql);
            assertEquals(1, bound.getParameterMappings().size());
            assertEquals("keyword", bound.getParameterMappings().get(0).getProperty());
            assertFalse(sql.contains(keyword), "search input must not be inlined");
            assertTrue(sql.contains("u.user_type IN ('01', '02', '03')"), sql);
            assertTrue(sql.contains("u.del_flag = '0'"), sql);
        }
    }
}
