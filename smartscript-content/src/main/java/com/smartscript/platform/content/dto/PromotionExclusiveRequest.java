package com.smartscript.platform.content.dto;

/** 管理端切换推广记录的锁定标记 */
public class PromotionExclusiveRequest
{
    /** 1=锁定独占，0=可被付费素材覆盖 */
    private Integer exclusive;

    public Integer getExclusive() { return exclusive; }
    public void setExclusive(Integer exclusive) { this.exclusive = exclusive; }
}
