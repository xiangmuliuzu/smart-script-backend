package com.smartscript.platform.content.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import com.smartscript.platform.content.domain.SysCopyrightCenterConfig;

class CopyrightFeeCalculatorTest
{
    private final CopyrightFeeCalculator calculator = new CopyrightFeeCalculator();

    @Test
    void nationalWordTiers()
    {
        SysCopyrightCenterConfig center = new SysCopyrightCenterConfig();
        center.setCenterCode("national");
        center.setCenterName("国家版权中心");
        center.setConfigJson(
                "{\"feeType\":\"national_word\",\"wordTiers\":["
                        + "{\"max\":100,\"fee\":100},"
                        + "{\"max\":5000,\"fee\":150},"
                        + "{\"max\":10000,\"fee\":200},"
                        + "{\"max\":null,\"fee\":300}]}");

        assertEquals("100.00", calculator.quote(center, 50).getFeeAmount().toPlainString());
        assertEquals("150.00", calculator.quote(center, 5000).getFeeAmount().toPlainString());
        assertEquals("200.00", calculator.quote(center, 8000).getFeeAmount().toPlainString());
        assertEquals("300.00", calculator.quote(center, 120000).getFeeAmount().toPlainString());
    }

    @Test
    void localFlatDefault50()
    {
        SysCopyrightCenterConfig center = new SysCopyrightCenterConfig();
        center.setCenterCode("local_guizhou");
        center.setCenterName("贵州地方版权平台");
        center.setConfigJson("{\"feeType\":\"local_flat\",\"flatFee\":50}");

        assertEquals("50.00", calculator.quote(center, 99999).getFeeAmount().toPlainString());
        assertEquals(CopyrightFeeCalculator.FEE_TYPE_LOCAL_FLAT, calculator.quote(center, 1).getFeeType());
    }
}
