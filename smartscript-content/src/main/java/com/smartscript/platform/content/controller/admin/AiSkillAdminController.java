package com.smartscript.platform.content.controller.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import com.smartscript.platform.content.service.AiSkillBundleSupport;
import com.smartscript.platform.content.service.IAiSkillService;

/**
 * AI Skill（导演风格 skill）配置（PC 后台）
 *
 * 依据：云端 script_platform_dev 库 sys_ai_skill 表 + E 模块「AI Skill 配置」页。
 *
 * 支持两种上传形态：
 * 1) 单文件（file）：兼容存量单文件 skill，内容落 file_content。
 * 2) 整个文件夹（files[] + paths[]）：真实 skill 多为「SKILL.md + references/…」目录，
 *    前端用目录选择（webkitdirectory）把每个文件连相对路径一起上传，后端按原目录结构
 *    落 file_tree（@@@FILE:路径@@@ 分段格式），入口 SKILL.md 另存 file_content 便于回显。
 *    AI 调用时 resolveSkillContent 会把整包拼装后注入 system prompt。
 *
 * 路由前缀 /api/v1/admin/content/ai-skill。
 * 上传入参（multipart/form-data）：skillName 必填、file 或 files 二选一必填、
 *   paths（与 files 顺序一一对应，缺省时用文件名）、status/remark 可选。
 * 更新入参（multipart/form-data）：id 必填、file/files 可选（不传保留原内容）。
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

    /** skill 包内单个文件大小上限（300KB） */
    private static final int BUNDLE_FILE_MAX_BYTES = 300 * 1024;

    /** 整个 skill 包大小上限（2MB） */
    private static final int BUNDLE_TOTAL_MAX_BYTES = 2 * 1024 * 1024;

    /** skill 包内文件数上限 */
    private static final int BUNDLE_MAX_FILES = 200;

    /** 允许纳入 skill 包的文本类后缀 */
    private static final String[] TEXT_SUFFIXES = { ".md", ".markdown", ".txt", ".skill", ".json", ".yaml", ".yml", ".csv" };

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
     * 通过 ID 获取 skill 详情（含 fileContent / fileTree，供回显）
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
     * 新增 skill：单文件（file）或整个文件夹（files + paths）
     */
    @PostMapping
    public AjaxResult add(@RequestParam("skillName") String skillName,
                          @RequestParam(value = "file", required = false) MultipartFile file,
                          @RequestParam(value = "files", required = false) MultipartFile[] files,
                          @RequestParam(value = "paths", required = false) String[] paths,
                          @RequestParam(value = "skillType", required = false) String skillType,
                          @RequestParam(value = "status", required = false) String status,
                          @RequestParam(value = "remark", required = false) String remark)
    {
        if (skillName == null || skillName.trim().isEmpty())
        {
            return AjaxResult.error("skill 名称不能为空");
        }
        boolean asBundle = files != null && files.length > 0;
        if (!asBundle && (file == null || file.isEmpty()))
        {
            return AjaxResult.error("请上传 skill 文件，或选择整个 skill 文件夹");
        }
        String type = normalizeSkillType(skillType);
        if (type == null)
        {
            return AjaxResult.error("skill 类型不正确，应为 write（剧本/写作/润色）或 storyboard（分镜）");
        }
        try
        {
            AiSkill skill = new AiSkill();
            skill.setSkillName(skillName.trim());
            skill.setSkillType(type);
            skill.setStatus(status == null || status.isBlank() ? "0" : status.trim());
            skill.setRemark(remark);
            if (asBundle)
            {
                applyBundle(skill, files, paths);
            }
            else
            {
                applySingleFile(skill, file);
            }
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
     * 修改 skill（file / files 不传时保留原内容；可用于改名/改类型/启停用）
     */
    @PutMapping
    public AjaxResult edit(@RequestParam("id") Long id,
                           @RequestParam("skillName") String skillName,
                           @RequestParam(value = "file", required = false) MultipartFile file,
                           @RequestParam(value = "files", required = false) MultipartFile[] files,
                           @RequestParam(value = "paths", required = false) String[] paths,
                           @RequestParam(value = "skillType", required = false) String skillType,
                           @RequestParam(value = "status", required = false) String status,
                           @RequestParam(value = "remark", required = false) String remark)
    {
        if (id == null || skillName == null || skillName.trim().isEmpty())
        {
            return AjaxResult.error("skill ID 与名称不能为空");
        }
        String type = normalizeSkillType(skillType);
        if (skillType != null && !skillType.isBlank() && type == null)
        {
            return AjaxResult.error("skill 类型不正确，应为 write（剧本/写作/润色）或 storyboard（分镜）");
        }
        try
        {
            AiSkill skill = new AiSkill();
            skill.setId(id);
            skill.setSkillName(skillName.trim());
            if (type != null)
            {
                skill.setSkillType(type);
            }
            if (files != null && files.length > 0)
            {
                applyBundle(skill, files, paths);
            }
            else if (file != null && !file.isEmpty())
            {
                applySingleFile(skill, file);
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

    /** 单文件上传：内容落 file_content，file_tree 置空（覆盖旧的多文件包） */
    private void applySingleFile(AiSkill skill, MultipartFile file) throws IOException
    {
        skill.setFileName(file.getOriginalFilename());
        skill.setFileContent(readFileContent(file));
        skill.setFileTree(null);
        skill.setFileCount(1);
    }

    /**
     * 整目录上传：按相对路径顺序拼成 file_tree，入口 file_content 保留 SKILL.md 内容。
     *
     * @param files 目录内逐个文件（前端 webkitdirectory 选择）
     * @param paths 与 files 一一对应的相对路径（如 shortdrama-storyboard/references/01-x.md）
     */
    private void applyBundle(AiSkill skill, MultipartFile[] files, String[] paths) throws IOException
    {
        if (files.length > BUNDLE_MAX_FILES)
        {
            throw new IllegalArgumentException("skill 包文件过多（上限 " + BUNDLE_MAX_FILES + " 个）");
        }
        long total = 0;
        List<AiSkillBundleSupport.Entry> entries = new ArrayList<>();
        List<String> rootNames = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < files.length; i++)
        {
            MultipartFile f = files[i];
            if (f == null || f.isEmpty())
            {
                continue;
            }
            String path = normalizePath((paths != null && i < paths.length) ? paths[i] : f.getOriginalFilename());
            if (path == null)
            {
                skipped++;
                continue;
            }
            if (!isTextFile(path))
            {
                skipped++;
                continue;
            }
            if (f.getSize() > BUNDLE_FILE_MAX_BYTES)
            {
                throw new IllegalArgumentException("文件 " + path + " 超过 300KB，请精简后再上传");
            }
            total += f.getSize();
            if (total > BUNDLE_TOTAL_MAX_BYTES)
            {
                throw new IllegalArgumentException("skill 文件夹总大小超过 2MB，请精简后再上传");
            }
            entries.add(new AiSkillBundleSupport.Entry(path, stripBom(new String(f.getBytes(), StandardCharsets.UTF_8))));
            String root = path.contains("/") ? path.substring(0, path.indexOf('/')) : null;
            if (root != null && !rootNames.contains(root))
            {
                rootNames.add(root);
            }
        }
        if (entries.isEmpty())
        {
            throw new IllegalArgumentException("该文件夹内没有可用的文本 skill 文件（支持 md/markdown/txt/skill/json/yaml/yml/csv）");
        }
        skill.setFileTree(AiSkillBundleSupport.format(entries));
        skill.setFileContent(AiSkillBundleSupport.entryContent(skill.getFileTree()));
        skill.setFileCount(entries.size());
        skill.setFileName(buildBundleName(entries, rootNames, skipped));
    }

    /** 目录名：单一根目录取根名，否则用「skill 包（N 个文件）」；file_name 列宽 200，超长截断 */
    private String buildBundleName(List<AiSkillBundleSupport.Entry> entries, List<String> rootNames, int skipped)
    {
        String base = rootNames.size() == 1 ? rootNames.get(0) : "skill 包";
        String name = base + "（" + entries.size() + " 个文件）";
        name = skipped > 0 ? name + "，已跳过 " + skipped + " 个非文本/空文件" : name;
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    /** 相对路径归一化：反斜杠转正斜杠、去掉 ./ 与开头的 /，拒绝 .. 越界与空路径 */
    private String normalizePath(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        String path = raw.trim().replace('\\', '/');
        while (path.startsWith("./"))
        {
            path = path.substring(2);
        }
        while (path.startsWith("/"))
        {
            path = path.substring(1);
        }
        if (path.isBlank() || path.contains(".."))
        {
            return null;
        }
        return path;
    }

    /** 是否为可纳入 skill 包的文本类文件 */
    private boolean isTextFile(String path)
    {
        String lower = path.toLowerCase();
        for (String suffix : TEXT_SUFFIXES)
        {
            if (lower.endsWith(suffix))
            {
                return true;
            }
        }
        return false;
    }

    /** 去掉 UTF-8 BOM（Windows 记事本保存的 md 常带 BOM，会让首个标题失效） */
    private String stripBom(String text)
    {
        if (text != null && text.startsWith("\uFEFF"))
        {
            return text.substring(1);
        }
        return text;
    }

    /** 读取上传文件文本（UTF-8），超限抛 IllegalArgumentException */
    private String readFileContent(MultipartFile file) throws IOException
    {
        if (file.getSize() > FILE_MAX_BYTES)
        {
            throw new IllegalArgumentException("skill 文件过大，请控制在 200KB 以内");
        }
        return stripBom(new String(file.getBytes(), StandardCharsets.UTF_8));
    }

    /** 归一化 skill 类型：空 → 默认 write；非法值 → null */
    private String normalizeSkillType(String skillType)
    {
        if (skillType == null || skillType.isBlank())
        {
            return "write";
        }
        String t = skillType.trim();
        return "storyboard".equals(t) ? "storyboard" : "write".equals(t) ? "write" : null;
    }
}
