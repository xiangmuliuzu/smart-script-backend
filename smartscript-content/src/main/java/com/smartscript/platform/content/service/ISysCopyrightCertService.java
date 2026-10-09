package com.smartscript.platform.content.service;

import java.util.List;
import com.smartscript.platform.content.domain.SysCopyrightCert;

public interface ISysCopyrightCertService
{
    List<SysCopyrightCert> selectCertList(String status, String keyword);
    
    SysCopyrightCert selectCertById(Long certId);
    
    byte[] downloadCert(Long certId);
}
