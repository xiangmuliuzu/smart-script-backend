package com.smartscript.platform.content.controller.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.smartscript.platform.content.domain.AiSkill;
import com.smartscript.platform.content.service.IAiSkillService;

/**
 * AI Skill（导演风格 skill）配置（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_ai_skill 表 + E 模块「AI Skill 配置」页。
 * 字段仅 skill 名称 + 文件：管理员直接上传 skill 文件（文本），后端读取文件内容
 * 落库（file_content），便于管理；AI 调用时按「导演(skill)选择」注入 prompt。
 *
 * 路由前缀 /api/v1/admin/content/ai-skill。
 * 上传入参（multipart/form-data）：skillName 必填、file 必填、status 可选、remark 可选。
 * 更新入参（multipart/form-data）：id 必填、file 可选（不传保留原内容）、其余同上。
 * 鉴权：与 AiQuotaController 一致走 /api/v1/admin 私有域（登录管理员可访问）。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/admin/content/ai-skill")
public class AiSkillAdminController extends BaseController
{
    /** 单个 skill 文件大小上限（200KB，文本 skill 足够） */
    private static final int FILE_MAX_BYTES = 200 * 1024;

    @Autowired
    private IAiSkillService aiSkillService;

    /**
     * 获取 skill 列表（skillName 模糊、status 精确；分页）
     */
    @GetMapping("/list")
    public TableDataInfo list(AiSkill query)
    {
        startPage();
        List<AiSkill> list = aiSkillService.selectSkillList(query);
        return getDataTable(list);
    }

    /**
     * 通过 ID 获取 skill 详情（含 fileContent，供回显）
     */
    @GetMapping("/{skillId}")
    public AjaxResult getInfo(@PathVariable Long skillId)
    {
        AiSkill skill = aiSkillService.selectSkillById(skillId);
        if (skill == null)
        {
            return AjaxResult.error("skill 不存在");
        }
        return AjaxResult.success(skill);
    }

    /**
     * 新增 skill（上传文件）
     */
    @PostMapping
    public AjaxResult add(@RequestParam("skillName") String skillName,
                          @RequestParam("file") MultipartFile file,
                          @RequestParam(value = "status", required = false) String status,
                          @RequestParam(value = "remark", required = false) String remark)
    {
        if (skillName == null || skillName.trim().isEmpty())
        {
            return AjaxResult.error("skill 名称不能为空");
        }
        if (file == null || file.isEmpty())
        {
            return AjaxResult.error("请上传 skill 文件");
        }
        try
        {
            String content = readFileContent(file);
            AiSkill skill = new AiSkill();
            skill.setSkillName(skillName.trim());
            skill.setFileName(file.getOriginalFilename());
            skill.setFileContent(content);
            skill.setStatus(status == null || status.isBlank() ? "0" : status.trim());
            skill.setRemark(remark);
            aiSkillService.insertSkill(skill);
            return AjaxResult.success(skill);
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
        catch (IOException e)
        {
            return AjaxResult.error("skill 文件读取失败");
        }
    }

    /**
     * 修改 skill（file 不传时保留原内容；可用于改名/启停用）
     */
    @PutMapping
    public AjaxResult edit(@RequestParam("id") Long id,
                           @RequestParam("skillName") String skillName,
                           @RequestParam(value = "file", required = false) MultipartFile file,
                           @RequestParam(value = "status", required = false) String status,
                           @RequestParam(value = "remark", required = false) String remark)
    {
        if (id == null || skillName == null || skillName.trim().isEmpty())
        {
            return AjaxResult.error("skill ID 与名称不能为空");
        }
        try
        {
            AiSkill skill = new AiSkill();
            skill.setId(id);
            skill.setSkillName(skillName.trim());
            if (file != null && !file.isEmpty())
            {
                skill.setFileName(file.getOriginalFilename());
                skill.setFileContent(readFileContent(file));
            }
            if (status != null && !status.isBlank())
            {
                skill.setStatus(status.trim());
            }
            skill.setRemark(remark);
            aiSkillService.updateSkill(skill);
            return AjaxResult.success(skill);
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
        catch (IOException e)
        {
            return AjaxResult.error("skill 文件读取失败");
        }
    }

    /**
     * 删除 skill（物理删除）
     */
    @DeleteMapping("/{skillId}")
    public AjaxResult remove(@PathVariable Long skillId)
    {
        aiSkillService.deleteSkillById(skillId);
        return AjaxResult.success();
    }

    /** 读取上传文件文本（UTF-8），超限抛 IllegalArgumentException */
    private String readFileContent(MultipartFile file) throws IOException
    {
        if (file.getSize() > FILE_MAX_BYTES)
        {
            throw new IllegalArgumentException("skill 文件过大，请控制在 200KB 以内");
        }
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }
}
