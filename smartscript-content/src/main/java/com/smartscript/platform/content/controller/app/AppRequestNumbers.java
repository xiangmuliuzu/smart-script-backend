package com.smartscript.platform.content.controller.app;

import java.math.BigDecimal;

final class AppRequestNumbers
{
    private AppRequestNumbers() { }

    static Long positiveLong(Object raw, String field)
    {
        if (raw == null)
        {
            return null;
        }
        try
        {
            long number = new BigDecimal(raw.toString()).longValueExact();
            if (number > 0)
            {
                return number;
            }
        }
        catch (NumberFormatException | ArithmeticException ignored)
        {
            // 小数、超出范围或不是数字都按非法入参返回。
        }
        throw new IllegalArgumentException(field + "必须为正整数");
    }

    static Integer positiveInt(Object raw, String field)
    {
        Long number = positiveLong(raw, field);
        if (number != null && number > Integer.MAX_VALUE)
        {
            throw new IllegalArgumentException(field + "超出范围");
        }
        return number == null ? null : number.intValue();
    }
}
