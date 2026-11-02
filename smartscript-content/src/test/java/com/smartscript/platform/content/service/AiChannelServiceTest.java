package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.content.domain.AiChannel;
import com.smartscript.platform.content.mapper.AiChannelMapper;
import com.smartscript.platform.content.service.impl.AiChannelServiceImpl;

/**
 * AI 调用渠道服务单元测试（E 模块）。
 *
 * 验证：名称唯一校验（新增重复抛 IllegalArgumentException、修改排除自身）、
 * 按模型名解析渠道、默认渠道（sort 最小）、用户端模型列表（不含 apiKey）。
 */
class AiChannelServiceTest
{
    private AiChannelMapper mapper;
    private AiChannelServiceImpl service;

    @BeforeEach
    void setUp() throws Exception
    {
        mapper = mock(AiChannelMapper.class);
        service = new AiChannelServiceImpl();
        setField(service, "aiChannelMapper", mapper);
    }

    @Test
    @DisplayName("新增：名称唯一校验，重复抛 IllegalArgumentException")
    void insertChannel_duplicateName_throws()
    {
        AiChannel hit = new AiChannel();
        hit.setChannelId(1L);
        hit.setChannelName("DeepSeek");
        when(mapper.checkChannelNameUnique("DeepSeek", null)).thenReturn(hit);

        AiChannel channel = new AiChannel();
        channel.setChannelName("DeepSeek");

        assertThrows(IllegalArgumentException.class, () -> service.insertChannel(channel));
    }

    @Test
    @DisplayName("新增：名称不重复时落库并回填 id")
    void insertChannel_ok()
    {
        when(mapper.checkChannelNameUnique("智谱GLM", null)).thenReturn(null);
        AiChannel channel = new AiChannel();
        channel.setChannelName("智谱GLM");
        service.insertChannel(channel);
        verify(mapper).insertChannel(channel);
    }

    @Test
    @DisplayName("修改：名称被其它渠道占用时抛异常")
    void updateChannel_occupiedName_throws()
    {
        AiChannel hit = new AiChannel();
        hit.setChannelId(9L);
        hit.setChannelName("DeepSeek");
        when(mapper.checkChannelNameUnique("DeepSeek", 2L)).thenReturn(hit);

        AiChannel channel = new AiChannel();
        channel.setChannelId(2L);
        channel.setChannelName("DeepSeek");

        assertThrows(IllegalArgumentException.class, () -> service.updateChannel(channel));
    }

    @Test
    @DisplayName("按模型名解析渠道：命中启用渠道的 models 列表返回该渠道")
    void resolveChannelByModel_hit()
    {
        AiChannel ds = new AiChannel();
        ds.setChannelId(1L);
        ds.setChannelName("DeepSeek");
        ds.setModels("deepseek-chat,deepseek-reasoner,deepseek-flash");
        ds.setDefaultModel("deepseek-flash");
        ds.setStatus("0");
        ds.setSort(1);

        AiChannel glm = new AiChannel();
        glm.setChannelId(2L);
        glm.setChannelName("智谱GLM");
        glm.setModels("glm-4,glm-4-plus");
        glm.setDefaultModel("glm-4");
        glm.setStatus("0");
        glm.setSort(2);

        when(mapper.selectEnabledChannelList()).thenReturn(Arrays.asList(ds, glm));

        assertEquals(2L, service.resolveChannelByModel("glm-4-plus").getChannelId());
        assertEquals(1L, service.resolveChannelByModel("deepseek-chat").getChannelId());
    }

    @Test
    @DisplayName("按模型名解析渠道：未命中返回 null")
    void resolveChannelByModel_miss_returnsNull()
    {
        AiChannel ds = new AiChannel();
        ds.setChannelId(1L);
        ds.setModels("deepseek-chat");
        when(mapper.selectEnabledChannelList()).thenReturn(Arrays.asList(ds));

        assertNull(service.resolveChannelByModel("unknown-model"));
        assertNull(service.resolveChannelByModel(null));
        assertNull(service.resolveChannelByModel("  "));
    }

    @Test
    @DisplayName("默认渠道：启用渠道中 sort 最小者")
    void resolveDefaultChannel_returnsMinSort()
    {
        AiChannel a = new AiChannel();
        a.setChannelId(1L);
        a.setSort(5);
        AiChannel b = new AiChannel();
        b.setChannelId(2L);
        b.setSort(1);
        when(mapper.selectEnabledChannelList()).thenReturn(Arrays.asList(a, b));

        assertEquals(2L, service.resolveDefaultChannel().getChannelId());
    }

    @Test
    @DisplayName("用户端模型列表：只含启用渠道且不暴露 apiKey")
    void listEnabledModels_hidesApiKey()
    {
        AiChannel ds = new AiChannel();
        ds.setChannelId(1L);
        ds.setChannelName("DeepSeek");
        ds.setProvider("deepseek");
        ds.setApiKey("sk-secret");
        ds.setModels("deepseek-chat,deepseek-flash");
        ds.setDefaultModel("deepseek-flash");
        ds.setStatus("0");
        when(mapper.selectEnabledChannelList()).thenReturn(Arrays.asList(ds));

        List<Map<String, Object>> models = service.listEnabledModels();
        assertEquals(1, models.size());
        Map<String, Object> row = models.get(0);
        assertEquals("DeepSeek", row.get("channelName"));
        assertEquals(Arrays.asList("deepseek-chat", "deepseek-flash"), row.get("models"));
        assertEquals("deepseek-flash", row.get("defaultModel"));
        assertTrue(!row.containsKey("apiKey"), "模型列表不得暴露 apiKey");
    }

    private void setField(Object target, String name, Object value) throws Exception
    {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
