package com.smartscript.platform.user.util;

/**
 * A5 敏感字段掩码。
 *
 * 与 {@link AppHashes#maskPhone} 同属展示层脱敏：规则集中在此，
 * 避免各服务各写一套掩码导致对外口径不一致。
 *
 * 掩码结果可落库（user_real_name_auth 只存掩码列），
 * 因此这些方法产出的字符串不得可逆推出原文。
 */
public final class AppMasks
{
    private AppMasks() {
    }

    /**
     * 姓名掩码：保留首字符，其余以 * 代替。
     *
     * 单字符姓名返回原字符（无法在不泄露的情况下继续脱敏）。
     * 例：王小明 -> 王**；欧阳 -> 欧*。
     */
    public static String maskRealName(String realName)
    {
        if (realName == null || realName.isBlank())
        {
            return null;
        }
        String trimmed = realName.trim();
        if (trimmed.length() == 1)
        {
            return trimmed;
        }
        StringBuilder sb = new StringBuilder(trimmed.length());
        sb.append(trimmed.charAt(0));
        for (int i = 1; i < trimmed.length(); i++)
        {
            sb.append('*');
        }
        return sb.toString();
    }

    /**
     * 身份证号掩码：保留前 6 位（行政区划）与后 4 位，中间以 * 代替。
     *
     * 长度不足 11 位时只保留首位与末位，避免短号码被完整还原。
     * 例：110101199001011234 -> 110101********1234。
     */
    public static String maskIdNumber(String idNumber)
    {
        if (idNumber == null || idNumber.isBlank())
        {
            return null;
        }
        String trimmed = idNumber.trim();
        if (trimmed.length() >= 11)
        {
            int middle = trimmed.length() - 10;
            StringBuilder sb = new StringBuilder(trimmed.length());
            sb.append(trimmed, 0, 6);
            for (int i = 0; i < middle; i++)
            {
                sb.append('*');
            }
            sb.append(trimmed, trimmed.length() - 4, trimmed.length());
            return sb.toString();
        }
        if (trimmed.length() <= 2)
        {
            return "*";
        }
        StringBuilder sb = new StringBuilder(trimmed.length());
        sb.append(trimmed.charAt(0));
        for (int i = 1; i < trimmed.length() - 1; i++)
        {
            sb.append('*');
        }
        sb.append(trimmed.charAt(trimmed.length() - 1));
        return sb.toString();
    }
}
