package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.domain.SysCopyrightCert;

public interface SysCopyrightCertMapper
{
    List<SysCopyrightCert> selectCertList(@Param("status") String status, @Param("keyword") String keyword);
    
    SysCopyrightCert selectCertById(@Param("certId") Long certId);
}
