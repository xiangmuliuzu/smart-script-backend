package com.smartscript.platform.content.dto;

/**
 * 版权合作联系方式（App 侧只读下发对象，接口文档 2.7.9 表2-89）。
 *
 * 依据：云端 script_platform_dev 库 sys_contact_profile 表（附件5.1 表3-62）。
 * 契约仅下发 work_id / has_contact / display_scope 三项，**不下发明文联系方式**：
 * phone_cipher / email_cipher / wechat_cipher 为数据库加密列，本接口只用于判定
 * 「是否已登记联系方式」，不读入内存、不解密、不对外输出（明文查看属 PC 侧 2.4.14 接口）。
 *
 * hasContact 口径（反推处理点）：作者存在联系方式档案，且 phone/email/wechat 三列密文
 * 至少一项非空。不叠加 display_status / verify_status 过滤 —— App 契约未下发这两列，
 * 故不按未定义口径裁剪。
 *
 * displayScope 原样下发（public/certified_partner/platform_forward/hidden，
 * 取值见接口文档 2.4.14 / 2.5.6），服务端不做身份可见性判定，由客户端按范围决定展示文案。
 *
 * @author xiangsipeng
 */
public class AppContactDto
{
    /** 作品ID */
    private Long workId;

    /** 作者是否登记了联系方式（三列密文至少一项非空） */
    private Boolean hasContact;

    /** 展示范围：public/certified_partner/platform_forward/hidden（原样下发，无档案时为 null） */
    private String displayScope;

    public AppContactDto()
    {
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public Boolean getHasContact()
    {
        return hasContact;
    }

    public void setHasContact(Boolean hasContact)
    {
        this.hasContact = hasContact;
    }

    public String getDisplayScope()
    {
        return displayScope;
    }

    public void setDisplayScope(String displayScope)
    {
        this.displayScope = displayScope;
    }
}