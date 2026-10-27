package com.smartscript.platform.content.service.impl;

import java.util.function.Consumer;

/**
 * 流式增量转发限长器：累计已转发字符数，超过上限后停止转发并置截断标记。
 *
 * 用途：AI 流式输出逐块回调 delta 时，把回调包一层本限长器，保证推给
 * 前端/落库的内容不超过管理员配置的上限（防平台亏损）。AI 上游仍会被读完，
 * 保证 SSE 连接正常结束，只是超限部分不再转发。
 *
 * 纯逻辑类，便于单元测试。
 *
 * @author xiangsipeng
 */
public class DeltaLimitTruncator
{
    private final Consumer<String> sink;

    private final int maxChars;

    private int emitted;

    private boolean truncated;

    /**
     * @param sink     真正的增量接收者（转发给前端）
     * @param maxChars 上限字符数；0 表示不限
     */
    public DeltaLimitTruncator(Consumer<String> sink, int maxChars)
    {
        this.sink = sink;
        this.maxChars = Math.max(0, maxChars);
        this.emitted = 0;
        this.truncated = false;
    }

    /**
     * 处理一个 AI 增量；超限时只转发剩余额度并置截断标记，其后不再转发。
     */
    public void accept(String delta)
    {
        if (delta == null || delta.isEmpty() || truncated)
        {
            return;
        }
        if (maxChars == UNLIMITED)
        {
            emitted += delta.length();
            sink.accept(delta);
            return;
        }
        int room = maxChars - emitted;
        if (room <= 0)
        {
            truncated = true;
            return;
        }
        if (delta.length() <= room)
        {
            emitted += delta.length();
            sink.accept(delta);
        }
        else
        {
            String part = delta.substring(0, room);
            emitted += room;
            sink.accept(part);
            truncated = true;
        }
    }

    /** 已转发的字符数。 */
    public int emittedChars()
    {
        return emitted;
    }

    /** 是否发生截断。 */
    public boolean isTruncated()
    {
        return truncated;
    }

    /** 统一截断到上限（同步路径与落库一致）；0 或未超限原样返回。 */
    public static String truncate(String content, int maxChars)
    {
        if (content == null)
        {
            return "";
        }
        if (maxChars <= 0 || content.length() <= maxChars)
        {
            return content;
        }
        return content.substring(0, maxChars);
    }

    private static final int UNLIMITED = 0;
}
