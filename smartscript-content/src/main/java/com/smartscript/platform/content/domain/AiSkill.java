package com.smartscript.platform.content.domain;

import java.util.Date;

/**
 * AI 导演风格 skill 配置（E 模块）
 *
 * 依据：云端 script_platform_dev 库 sys_ai_skill 表。
 * 字段口径：skill_name 唯一；file_name 为上传的原始文件名；file_content 为文件文本
 * 内容（UTF-8，管理员上传时由后端读取落库）；status 0 正常 / 1 停用。
 *
 * @author xiangsipeng
 */
public class AiSkill
{
    /** skill ID */
    private Long id;

    /** skill 名称（唯一） */
    private String skillName;

    /** skill 类型：write（剧本/写作/润色用） / storyboard（分镜用） */
    private String skillType;

    /** 上传的原始文件名 */
    private String fileName;

    /** skill 文件文本内容（单文件 skill；多文件包时为 SKILL.md 入口内容） */
    private String fileContent;

    /**
     * 多文件 skill 包：整目录内容，按「@@@FILE:相对路径@@@」分段顺序拼接。
     * 单文件 skill 为空，此时仅用 fileContent。
     */
    private String fileTree;

    /** skill 包内文件数（单文件 = 1） */
    private Integer fileCount;

    /** 状态：0正常 1停用 */
    private String status;

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    private Date createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    private Date updateTime;

    /** 备注 */
    private String remark;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public String getSkillName()
    {
        return skillName;
    }

    public void setSkillName(String skillName)
    {
        this.skillName = skillName;
    }

    public String getSkillType()
    {
        return skillType;
    }

    public void setSkillType(String skillType)
    {
        this.skillType = skillType;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getFileContent()
    {
        return fileContent;
    }

    public void setFileContent(String fileContent)
    {
        this.fileContent = fileContent;
    }

    public String getFileTree()
    {
        return fileTree;
    }

    public void setFileTree(String fileTree)
    {
        this.fileTree = fileTree;
    }

    public Integer getFileCount()
    {
        return fileCount;
    }

    public void setFileCount(Integer fileCount)
    {
        this.fileCount = fileCount;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getCreateBy()
    {
        return createBy;
    }

    public void setCreateBy(String createBy)
    {
        this.createBy = createBy;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getUpdateBy()
    {
        return updateBy;
    }

    public void setUpdateBy(String updateBy)
    {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }
}
