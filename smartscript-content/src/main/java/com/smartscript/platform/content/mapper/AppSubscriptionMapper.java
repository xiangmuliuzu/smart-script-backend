package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.dto.AppSubscriptionItem;

/**
 * 追更订阅 数据层（App 2.8.13 追更订阅 / 2.8.14 我的追更列表）
 *
 * 依据：云端 script_platform_dev 库 sys_subscribe + sys_work + sys_user 表 +
 * 接口文档表 2-106 / 2-107。
 *
 * 反推处理点：
 * 1. 所有语句强制带 user_id 条件，归属由服务层传入的当前登录身份决定。
 * 2. 表上有唯一索引 uk_user_work(user_id, work_id)（实测 non_unique=0），
 *    幂等仍由 insertSubscribeIfAbsent 的 WHERE NOT EXISTS 兜底（与 AppFavoriteMapper 同一写法）。
 * 3. notify_enabled 为 NOT NULL 且无默认值，订阅时显式写 1（默认开启更新提醒）；
 *    本批不做提醒开关（无契约），故不提供改该列的方法。
 * 4. 列表只回「已上架未删除」作品，与书城可见性口径一致；下架后订阅关系保留，重新上架即恢复展示。
 *
 * @author xiangsipeng
 */
public interface AppSubscriptionMapper
{
    /**
     * 幂等订阅：已存在 (user_id, work_id) 时不插入
     *
     * @param userId 订阅人ID（当前登录身份）
     * @param workId 作品ID
     * @return 影响行数（1=新增成功，0=此前已订阅）
     */
    public int insertSubscribeIfAbsent(@Param("userId") Long userId, @Param("workId") Long workId);

    /**
     * 取消订阅（幂等：记录不存在时影响 0 行）
     *
     * @param userId 订阅人ID
     * @param workId 作品ID
     * @return 影响行数
     */
    public int deleteSubscribe(@Param("userId") Long userId, @Param("workId") Long workId);

    /**
     * 统计订阅关系是否存在
     *
     * @param userId 订阅人ID
     * @param workId 作品ID
     * @return 记录数（0 或 1）
     */
    public int countSubscribe(@Param("userId") Long userId, @Param("workId") Long workId);

    /**
     * 我的追更列表（仅上架未删除作品，按订阅时间倒序；分页由调用方 PageHelper 驱动）
     *
     * @param userId 订阅人ID
     * @return 追更项集合
     */
    public List<AppSubscriptionItem> selectSubscriptionWorks(@Param("userId") Long userId);
}