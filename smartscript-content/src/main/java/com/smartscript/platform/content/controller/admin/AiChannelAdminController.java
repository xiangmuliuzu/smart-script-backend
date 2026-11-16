package com.smartscript.platform.content.controller.admin;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.smartscript.platform.content.domain.AiChannel;
import com.smartscript.platform.content.service.AiServiceClient;
import com.smartscript.platform.content.service.IAiChannelService;

/**
 * AI 调用渠道配置（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_ai_channel 表 + E 模块
 * 「AI 创作 Skill 和模型配置」页「AI 渠道 / 模型配置」tab。
 *
 * 路由前缀 /api/v1/admin/content/ai-channel。
 * 入参（JSON）：channelName 必填、baseUrl 必填、models 必填；默认模型不在渠道表单里填写，
 * 由「默认模型设置」按钮统一设置全站默认（POST /default-model），渠道内默认模型自动取模型列表第一个；
 * apiKey 可空（空时该渠道不参与调用路由，调用回退默认环境配置）。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/ai-channel")
public class AiChannelAdminController extends BaseController
{
    @Autowired
    private IAiChannelService aiChannelService;

    @Autowired
    private AiServiceClient aiServiceClient;

    /**
     * 获取渠道列表（channelName 模糊、status 精确；分页）
     */
    @GetMapping("/list")
    public TableDataInfo list(AiChannel query)
    {
        startPage();
        List<AiChannel> list = aiChannelService.selectChannelList(query);
        return getDataTable(list);
    }

    /**
     * 渠道连通性测试：用该渠道自己的 baseUrl / apiKey / 默认模型发一次最小请求。
     *
     * 与正式调用不同，上游非 2xx 时不统一包装成「服务不可用」，
     * 而是原样返回状态码与响应体，便于管理员定位问题（账号池耗尽 / 密钥无效 / 模型不存在）。
     *
     * @param body {"channelId": 7, "model": "可选，默认取渠道 defaultModel"}
     */
    @PostMapping("/test")
    public AjaxResult test(@RequestBody Map<String, Object> body)
    {
        Object idValue = body == null ? null : body.get("channelId");
        if (idValue == null || String.valueOf(idValue).isBlank())
        {
            return AjaxResult.error("渠道 ID 不能为空");
        }
        Long channelId;
        try
        {
            channelId = Long.valueOf(String.valueOf(idValue).trim());
        }
        catch (NumberFormatException e)
        {
            return AjaxResult.error("渠道 ID 不合法");
        }
        AiChannel channel = aiChannelService.selectChannelById(channelId);
        if (channel == null)
        {
            return AjaxResult.error("渠道不存在");
        }
        String model = body.get("model") == null ? channel.getDefaultModel()
                : String.valueOf(body.get("model")).trim();
        try
        {
            return AjaxResult.success(aiServiceClient.testChannel(channel.getBaseUrl(), channel.getApiKey(), model));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 通过 ID 获取渠道详情（含 apiKey，供回显）
     */
    @GetMapping("/{channelId}")
    public AjaxResult getInfo(@PathVariable Long channelId)
    {
        AiChannel channel = aiChannelService.selectChannelById(channelId);
        if (channel == null)
        {
            return AjaxResult.error("渠道不存在");
        }
        return AjaxResult.success(channel);
    }

    /**
     * 新增渠道
     */
    @PostMapping
    public AjaxResult add(@RequestBody AiChannel channel)
    {
        String error = validate(channel);
        if (error != null)
        {
            return AjaxResult.error(error);
        }
        channel.setStatus(channel.getStatus() == null || channel.getStatus().isBlank() ? "0" : channel.getStatus().trim());
        channel.setSort(channel.getSort() == null ? 0 : channel.getSort());
        try
        {
            aiChannelService.insertChannel(channel);
            return AjaxResult.success(channel);
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 修改渠道（启停用走同一接口，status 0/1）
     * 编辑为部分更新：未传字段保持原值；默认模型不再由表单填写，
     * 未传或模型列表变更导致原值失效时自动取列表第一个。
     */
    @PutMapping
    public AjaxResult edit(@RequestBody AiChannel channel)
    {
        if (channel.getChannelId() == null)
        {
            return AjaxResult.error("渠道 ID 不能为空");
        }
        AiChannel exist = aiChannelService.selectChannelById(channel.getChannelId());
        if (exist == null)
        {
            return AjaxResult.error("渠道不存在");
        }
        try
        {
            aiChannelService.updateChannel(channel);
            return AjaxResult.success(channel);
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 设置全站默认模型（「默认模型设置」按钮）
     *
     * @param body {"channelId": 1, "model": "deepseek-chat"}
     */
    @PostMapping("/default-model")
    public AjaxResult setDefaultModel(@RequestBody Map<String, Object> body)
    {
        Object idValue = body == null ? null : body.get("channelId");
        if (idValue == null || String.valueOf(idValue).isBlank())
        {
            return AjaxResult.error("请选择渠道");
        }
        Long channelId;
        try
        {
            channelId = Long.valueOf(String.valueOf(idValue).trim());
        }
        catch (NumberFormatException e)
        {
            return AjaxResult.error("渠道 ID 不合法");
        }
        String model = body.get("model") == null ? "" : String.valueOf(body.get("model")).trim();
        try
        {
            aiChannelService.setGlobalDefault(channelId, model);
            return AjaxResult.success();
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 删除渠道（物理删除）
     */
    @DeleteMapping("/{channelId}")
    public AjaxResult remove(@PathVariable Long channelId)
    {
        aiChannelService.deleteChannelById(channelId);
        return AjaxResult.success();
    }

    /** 校验：名称 / 基址 / 模型列表必填（默认模型不再由表单填写，由服务层自动取列表第一个） */
    private String validate(AiChannel channel)
    {
        if (channel == null)
        {
            return "请求体不能为空";
        }
        if (channel.getChannelName() == null || channel.getChannelName().isBlank())
        {
            return "渠道名称不能为空";
        }
        if (channel.getBaseUrl() == null || channel.getBaseUrl().isBlank())
        {
            return "API 基址不能为空";
        }
        String models = channel.getModels();
        if (models == null || models.isBlank())
        {
            return "模型列表不能为空";
        }
        return null;
    }
}
