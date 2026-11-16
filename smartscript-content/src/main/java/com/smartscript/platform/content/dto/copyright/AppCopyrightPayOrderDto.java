package com.smartscript.platform.content.dto.copyright;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/** App 版权申请待支付单。 */
public class AppCopyrightPayOrderDto
{
    private Long payOrderId;
    private String payNo;
    private Long workId;
    private String workName;
    private Long configId;
    private String copyrightCenter;
    private String centerName;
    private Integer wordCount;
    private BigDecimal feeAmount;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date expireTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    private Long applyId;

    public Long getPayOrderId()
    {
        return payOrderId;
    }

    public void setPayOrderId(Long payOrderId)
    {
        this.payOrderId = payOrderId;
    }

    public String getPayNo()
    {
        return payNo;
    }

    public void setPayNo(String payNo)
    {
        this.payNo = payNo;
    }

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
    }

    public String getWorkName()
    {
        return workName;
    }

    public void setWorkName(String workName)
    {
        this.workName = workName;
    }

    public Long getConfigId()
    {
        return configId;
    }

    public void setConfigId(Long configId)
    {
        this.configId = configId;
    }

    public String getCopyrightCenter()
    {
        return copyrightCenter;
    }

    public void setCopyrightCenter(String copyrightCenter)
    {
        this.copyrightCenter = copyrightCenter;
    }

    public String getCenterName()
    {
        return centerName;
    }

    public void setCenterName(String centerName)
    {
        this.centerName = centerName;
    }

    public Integer getWordCount()
    {
        return wordCount;
    }

    public void setWordCount(Integer wordCount)
    {
        this.wordCount = wordCount;
    }

    public BigDecimal getFeeAmount()
    {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount)
    {
        this.feeAmount = feeAmount;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Date getExpireTime()
    {
        return expireTime;
    }

    public void setExpireTime(Date expireTime)
    {
        this.expireTime = expireTime;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Long getApplyId()
    {
        return applyId;
    }

    public void setApplyId(Long applyId)
    {
        this.applyId = applyId;
    }
}
