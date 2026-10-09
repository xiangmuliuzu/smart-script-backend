package com.smartscript.platform.content.dto;

/**
 * AI 润色请求（B 模块 2.9.11 表2-121）。
 *
 * 依据：接口文档 2.9.11 输入参数。契约入参 {content 必填, style 可选}。
 *
 * @author xiangsipeng
 */
public class AppAiPolishRequest
{
    /** 待润色内容（必填） */
    private String content;

    /** 润色风格（可选） */
    private String style;

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getStyle()
    {
        return style;
    }

    public void setStyle(String style)
    {
        this.style = style;
    }
}