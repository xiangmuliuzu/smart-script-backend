package com.smartscript.platform.user.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * A5 实名提交请求（契约 §1.3）。
 *
 * 服务端只把掩码与材料引用落库；明文姓名与证件号不写日志、不落库、不回传。
 */
public class RealNameSubmitRequest
{
    @NotBlank
    private String realName;

    @NotBlank
    private String idNumber;

    private List<String> materialRefs;

    public String getRealName()
    {
        return realName;
    }

    public void setRealName(String realName)
    {
        this.realName = realName;
    }

    public String getIdNumber()
    {
        return idNumber;
    }

    public void setIdNumber(String idNumber)
    {
        this.idNumber = idNumber;
    }

    public List<String> getMaterialRefs()
    {
        return materialRefs;
    }

    public void setMaterialRefs(List<String> materialRefs)
    {
        this.materialRefs = materialRefs;
    }
}
