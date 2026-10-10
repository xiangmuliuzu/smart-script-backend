package com.smartscript.platform.user.domain.admin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A4 PC 管理域：实名申请（契约 §3.2）。
 *
 * 映射自 user_real_name_auth（A2 结构，A4 不改列名，只做 DTO 映射）：
 *   create_time -> submittedAt
 *   audited_at  -> reviewedAt
 *   auditor_id  -> reviewerName（关联 sys_user.nick_name 取得）
 *
 * 列表查询不填充 materialRefs；详情按权限填充短时授权引用。
 * 别名掩码字段由数据库列直接提供，本对象不持有明文姓名或证件号。
 */
public class RealNameApplication
{
    private Long applicationId;
    private Long userId;
    private String nickname;
    private String phoneMasked;
    private String status;
    private String realNameMasked;
    private String idNumberMasked;
    private String rejectReason;
    private String reviewerName;
    private Date submittedAt;
    private Date reviewedAt;

    /**
     * 材料短时授权引用；列表不返回，详情按权限返回。
     * NON_EMPTY：空集合时不出现在响应中，避免列表响应携带敏感引用字段名。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> materialRefs = new ArrayList<>();

    /**
     * 数据库中的单值材料列（user_real_name_auth.material_ref）。
     *
     * 仅作为 Mapper 的接收字段存在，不参与 JSON 序列化；服务层据此按
     * 裁决 GAP-5 生成零或单元素 materialRefs，禁止按分隔符拆分。
     */
    @JsonIgnore
    private String materialRef;

    public Long getApplicationId()
    {
        return applicationId;
    }

    public void setApplicationId(Long applicationId)
    {
        this.applicationId = applicationId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getNickname()
    {
        return nickname;
    }

    public void setNickname(String nickname)
    {
        this.nickname = nickname;
    }

    public String getPhoneMasked()
    {
        return phoneMasked;
    }

    public void setPhoneMasked(String phoneMasked)
    {
        this.phoneMasked = phoneMasked;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getRealNameMasked()
    {
        return realNameMasked;
    }

    public void setRealNameMasked(String realNameMasked)
    {
        this.realNameMasked = realNameMasked;
    }

    public String getIdNumberMasked()
    {
        return idNumberMasked;
    }

    public void setIdNumberMasked(String idNumberMasked)
    {
        this.idNumberMasked = idNumberMasked;
    }

    public String getRejectReason()
    {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason)
    {
        this.rejectReason = rejectReason;
    }

    public String getReviewerName()
    {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName)
    {
        this.reviewerName = reviewerName;
    }

    public Date getSubmittedAt()
    {
        return submittedAt;
    }

    public void setSubmittedAt(Date submittedAt)
    {
        this.submittedAt = submittedAt;
    }

    public Date getReviewedAt()
    {
        return reviewedAt;
    }

    public void setReviewedAt(Date reviewedAt)
    {
        this.reviewedAt = reviewedAt;
    }

    public List<String> getMaterialRefs()
    {
        return materialRefs;
    }

    public void setMaterialRefs(List<String> materialRefs)
    {
        this.materialRefs = materialRefs == null ? new ArrayList<>() : materialRefs;
    }

    public String getMaterialRef()
    {
        return materialRef;
    }

    public void setMaterialRef(String materialRef)
    {
        this.materialRef = materialRef;
    }
}
