package com.smartscript.platform.content.dto.copyright;

import jakarta.validation.constraints.NotBlank;

/** App 用户提交补正材料（同一申请，不缴费）。 */
public class AppCopyrightSupplementRequest
{
    /** 补正材料 JSON 数组或 URL 列表字符串 */
    @NotBlank(message = "补正材料不能为空")
    private String materialsJson;

    private String remark;

    public String getMaterialsJson()
    {
        return materialsJson;
    }

    public void setMaterialsJson(String materialsJson)
    {
        this.materialsJson = materialsJson;
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
