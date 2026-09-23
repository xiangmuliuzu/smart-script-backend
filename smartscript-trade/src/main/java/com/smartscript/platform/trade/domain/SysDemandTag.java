package com.smartscript.platform.trade.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * C module: demand tag entity (sys_demand_tag).
 * Tags used to classify partner demands (e.g. "film", "TV series", "suspense").
 */
public class SysDemandTag extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long tagId;
    private String tagName;
    private Integer usedCount;

    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }
    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }
}
