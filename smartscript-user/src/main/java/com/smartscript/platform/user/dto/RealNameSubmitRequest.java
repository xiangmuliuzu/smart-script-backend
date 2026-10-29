package com.smartscript.platform.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A5 实名提交请求（契约 §1.3）。
 *
 * 证件号只保存掩码，核验通过后姓名快照用于合同；明文不写入日志。
 */
public class RealNameSubmitRequest
{
    @NotBlank
    private String realName;

    @NotBlank
    private String idNumber;

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

}
