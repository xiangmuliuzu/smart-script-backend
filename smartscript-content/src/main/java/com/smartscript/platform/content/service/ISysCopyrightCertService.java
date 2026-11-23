package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysCopyrightCert;
import com.smartscript.platform.content.domain.SysCopyrightApplication;

public interface ISysCopyrightCertService
{
    List<SysCopyrightCert> selectCertList(String status, String keyword);
    
    List<SysCopyrightCert> selectCertListByUserId(Long userId, String status, String keyword);
    
    SysCopyrightCert selectCertById(Long certId);
    
    byte[] downloadCert(Long certId);

    /** 从版权申请自动创建电子证书；已存在时返回 0/1 语义兼容旧调用。 */
    int createCertFromApplication(SysCopyrightApplication application);

    /**
     * 从版权申请创建/取回电子证书。
     * 已存在同作品证书时返回既有行；新建时返回带 certId 的行。
     */
    SysCopyrightCert ensureCertFromApplication(SysCopyrightApplication application);
}
