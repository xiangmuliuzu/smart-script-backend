package com.smartscript.platform.content.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import java.util.Date;

public class SysCopyrightCert extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long certId;
    private Long workId;
    private Long userId;

    @Excel(name = "证书编号")
    private String certNo;

    @Excel(name = "区块链哈希")
    private String blockchainHash;

    private String certUrl;

    @Excel(name = "状态")
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "申请时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date applyTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "签发时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date certTime;

    private String workName;
    private String userName;

    public Long getCertId() { return certId; }
    public void setCertId(Long certId) { this.certId = certId; }

    public Long getWorkId() { return workId; }
    public void setWorkId(Long workId) { this.workId = workId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCertNo() { return certNo; }
    public void setCertNo(String certNo) { this.certNo = certNo; }

    public String getBlockchainHash() { return blockchainHash; }
    public void setBlockchainHash(String blockchainHash) { this.blockchainHash = blockchainHash; }

    public String getCertUrl() { return certUrl; }
    public void setCertUrl(String certUrl) { this.certUrl = certUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getApplyTime() { return applyTime; }
    public void setApplyTime(Date applyTime) { this.applyTime = applyTime; }

    public Date getCertTime() { return certTime; }
    public void setCertTime(Date certTime) { this.certTime = certTime; }

    public String getWorkName() { return workName; }
    public void setWorkName(String workName) { this.workName = workName; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("certId", getCertId())
                .append("workId", getWorkId())
                .append("userId", getUserId())
                .append("certNo", getCertNo())
                .append("status", getStatus())
                .toString();
    }
}
