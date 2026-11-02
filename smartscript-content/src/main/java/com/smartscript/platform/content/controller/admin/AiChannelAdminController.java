package com.smartscript.platform.content.controller.admin;

import java.util.List;
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
import com.smartscript.platform.content.service.IAiChannelService;

/**
 * AI 调用渠道配置（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_ai_channel 表 + E 模块
 * 「AI 创作 Skill 和模型配置」页「AI 渠道 / 模型配置」tab。
 *
 * 路由前缀 /api/v1/admin/content/ai-channel。
 * 入参（JSON）：channelName 必填、baseUrl 必填、models 必填、defaultModel 必填（须属于 models）；
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
     * 编辑为部分更新：未传字段保持原值；仅当传入或保留后的 defaultModel 不属于
     * 传入/保留后的 models 时返回错误（例如改了模型列表却遗漏默认模型）。
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
        String models = channel.getModels() != null && !channel.getModels().isBlank()
                ? channel.getModels().trim() : exist.getModels();
        String defaultModel = channel.getDefaultModel() != null && !channel.getDefaultModel().isBlank()
                ? channel.getDefaultModel().trim() : exist.getDefaultModel();
        String error = checkDefaultInModels(models, defaultModel);
        if (error != null)
        {
            return AjaxResult.error(error);
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
     * 删除渠道（物理删除）
     */
    @DeleteMapping("/{channelId}")
    public AjaxResult remove(@PathVariable Long channelId)
    {
        aiChannelService.deleteChannelById(channelId);
        return AjaxResult.success();
    }

    /** 校验：名称 / 基址 / 模型列表 / 默认模型（须属于列表）必填合法 */
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
        String defaultModel = channel.getDefaultModel();
        if (defaultModel == null || defaultModel.isBlank())
        {
            return "默认模型不能为空";
        }
        return checkDefaultInModels(models, defaultModel);
    }

    /** defaultModel 必须属于 models 逗号分隔列表；空值跳过 */
    private String checkDefaultInModels(String models, String defaultModel)
    {
        if (models == null || models.isBlank() || defaultModel == null || defaultModel.isBlank())
        {
            return null;
        }
        boolean hit = false;
        for (String m : models.split(","))
        {
            if (defaultModel.trim().equals(m == null ? "" : m.trim()))
            {
                hit = true;
                break;
            }
        }
        return hit ? null : "默认模型必须属于模型列表";
    }
}
