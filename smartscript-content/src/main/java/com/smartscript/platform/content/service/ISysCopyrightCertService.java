package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysCopyrightCert;
import com.smartscript.platform.content.domain.SysCopyrightApplication;

public interface ISysCopyrightCertService
{
    List<SysCopyrightCert> selectCertList(String status, String keyword);
    
    SysCopyrightCert selectCertById(Long certId);
    
    byte[] downloadCert(Long certId);
    
    /**
     * 从版权申请自动创建电子证书
     * 
     * @param application 版权申请对象
     * @return 创建的记录数
     */
    int createCertFromApplication(SysCopyrightApplication application);
}
