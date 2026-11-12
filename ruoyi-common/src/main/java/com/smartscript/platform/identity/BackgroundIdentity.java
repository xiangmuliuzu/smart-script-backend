package com.smartscript.platform.identity;

/**
 * 后台线程身份（线程绑定）。
 *
 * 用途：批量生成队列这类「脱离 HTTP 请求」的后台任务，无法从 SecurityContext 取到 App 主体，
 * 由任务线程在执行前绑定发起人 userId，执行结束清理。
 *
 * 约束：只能由受信的后台执行器写入，且必须在 finally 中 {@link #clear()}，
 * 线程池复用时不清理会把身份泄漏给下一个任务。
 *
 * @author smartscript
 */
public final class BackgroundIdentity
{
    private static final ThreadLocal<Long> HOLDER = new ThreadLocal<>();

    private BackgroundIdentity()
    {
    }

    /** 绑定后台任务发起人。 */
    public static void set(Long userId)
    {
        if (userId == null)
        {
            HOLDER.remove();
            return;
        }
        HOLDER.set(userId);
    }

    /** 当前线程绑定的发起人（无则 null）。 */
    public static Long get()
    {
        return HOLDER.get();
    }

    /** 清理绑定（后台任务结束必须调用）。 */
    public static void clear()
    {
        HOLDER.remove();
    }
}
