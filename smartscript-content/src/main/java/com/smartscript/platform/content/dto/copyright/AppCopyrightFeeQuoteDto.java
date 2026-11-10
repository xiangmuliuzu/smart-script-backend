package com.smartscript.platform.content.dto.copyright;

import java.math.BigDecimal;

/** App 版权申请询价结果。 */
public class AppCopyrightFeeQuoteDto
{
    private Long workId;
    private Long configId;
    private String copyrightCenter;
    private String centerName;
    private Integer wordCount;
    private BigDecimal feeAmount;
    private String feeType;
    private String currency = "CNY";

    public Long getWorkId()
    {
        return workId;
    }

    public void setWorkId(Long workId)
    {
        this.workId = workId;
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

    public String getFeeType()
    {
        return feeType;
    }

    public void setFeeType(String feeType)
    {
        this.feeType = feeType;
    }

    public String getCurrency()
    {
        return currency;
    }

    public void setCurrency(String currency)
    {
        this.currency = currency;
    }
}
