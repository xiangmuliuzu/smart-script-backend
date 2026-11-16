package com.smartscript.platform.content.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 版权中心配置对象 sys_copyright_center_config
 * 
 * @author SmartScript Team
 * @date 2024-01
 */
public class SysCopyrightCenterConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 配置ID */
    private Long configId;

    /** 版权中心编码 */
    @Excel(name = "中心编码")
    private String centerCode;

    /** 版权中心名称 */
    @Excel(name = "中心名称")
    private String centerName;

    /** API地址 */
    @Excel(name = "API地址")
    private String apiUrl;

    /** API密钥 */
    private String apiKey;

    /** API秘密 */
    private String apiSecret;

    /** 状态（enabled启用 disabled停用） */
    @Excel(name = "状态", readConverterExp = "enabled=启用,disabled=停用")
    private String status;

    /** 优先级 */
    @Excel(name = "优先级")
    private Integer priority;

    /** 扩展配置（JSON格式） */
    private String configJson;

    /** 备注 */
    private String remark;

    /** 创建者 */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新者 */
    private String updateBy;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public void setConfigId(Long configId) 
    {
        this.configId = configId;
    }

    public Long getConfigId() 
    {
        return configId;
    }

    public void setCenterCode(String centerCode) 
    {
        this.centerCode = centerCode;
    }

    public String getCenterCode() 
    {
        return centerCode;
    }

    public void setCenterName(String centerName) 
    {
        this.centerName = centerName;
    }

    public String getCenterName() 
    {
        return centerName;
    }

    public void setApiUrl(String apiUrl) 
    {
        this.apiUrl = apiUrl;
    }

    public String getApiUrl() 
    {
        return apiUrl;
    }

    public void setApiKey(String apiKey) 
    {
        this.apiKey = apiKey;
    }

    public String getApiKey() 
    {
        return apiKey;
    }

    public void setApiSecret(String apiSecret) 
    {
        this.apiSecret = apiSecret;
    }

    public String getApiSecret() 
    {
        return apiSecret;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    public void setPriority(Integer priority) 
    {
        this.priority = priority;
    }

    public Integer getPriority() 
    {
        return priority;
    }

    public void setConfigJson(String configJson) 
    {
        this.configJson = configJson;
    }

    public String getConfigJson() 
    {
        return configJson;
    }

    public void setRemark(String remark) 
    {
        this.remark = remark;
    }

    public String getRemark() 
    {
        return remark;
    }

    public void setCreateBy(String createBy) 
    {
        this.createBy = createBy;
    }

    public String getCreateBy() 
    {
        return createBy;
    }

    public void setCreateTime(Date createTime) 
    {
        this.createTime = createTime;
    }

    public Date getCreateTime() 
    {
        return createTime;
    }

    public void setUpdateBy(String updateBy) 
    {
        this.updateBy = updateBy;
    }

    public String getUpdateBy() 
    {
        return updateBy;
    }

    public void setUpdateTime(Date updateTime) 
    {
        this.updateTime = updateTime;
    }

    public Date getUpdateTime() 
    {
        return updateTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("configId", getConfigId())
            .append("centerCode", getCenterCode())
            .append("centerName", getCenterName())
            .append("apiUrl", getApiUrl())
            .append("status", getStatus())
            .append("priority", getPriority())
            .append("configJson", getConfigJson())
            .append("remark", getRemark())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
