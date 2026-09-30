package com.smartscript.platform.user.constant;

import java.util.HashMap;
import java.util.Map;

/**
 * A4 创作者擅长类型字典。
 *
 * D1 记录：项目内没有既有的创作类型字典（sys_work.genre_id 指向内容模块的
 * 题材表，不在 smartscript_full_init.sql 管辖内），字典来源待与内容模块
 * 协作确认。确认前本表保持空：未登记代码的名称回退为代码本身，不虚构
 * 类型名称；确认后在 register() 登记映射，接口契约 {code, name} 不变。
 */
public final class CreatorSpecialtyDictionary
{
    private static final Map<String, String> NAMES = new HashMap<>();

    static
    {
        // 待内容模块题材字典确认后登记，例如：
        // NAMES.put("G001", "都市");
    }

    private CreatorSpecialtyDictionary()
    {
    }

    /** 未登记代码回退为代码本身，不抛错、不返回 null。 */
    public static String nameOf(String code)
    {
        if (code == null)
        {
            return "";
        }
        return NAMES.getOrDefault(code, code);
    }
}
