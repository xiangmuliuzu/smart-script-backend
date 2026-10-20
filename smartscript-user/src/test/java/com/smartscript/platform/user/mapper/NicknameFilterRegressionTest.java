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
    void allThreeListsBindExactNicknameSeparatelyFromKeyword() throws Exception
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
                assertTrue(sql.contains("AND u.nick_name = ?"), sql);
                assertFalse(sql.contains("LIKE"), sql);
                assertEquals("nickname", bound.getParameterMappings().get(0).getProperty());
            }
            assertFalse(statement.getBoundSql(Map.of()).getSql().contains("u.nick_name = ?"));
        }
    }
}
