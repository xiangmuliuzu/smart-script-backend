package com.smartscript.platform.content.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

/**
 * 作品读者评分 数据层（App 书城读者评分通道）
 *
 * 依据：云端 script_platform_dev 库 sys_work_rating 表（迁移脚本
 * sql/migrations/b/B_20261014_001__create_work_rating_table.sql）。
 * 设计文档未定义读者评分表与提交接口，本通道按「一人一评、可覆盖」补齐。
 *
 * 反推处理点：
 * 1. 归属一律由服务层传入当前登录身份，所有语句强制带 user_id 条件，
 *    不接收请求体中的 userId，避免读写他人评分。
 * 2. (work_id, user_id) 有唯一键 uk_work_user，「覆盖更新」由
 *    INSERT ... ON DUPLICATE KEY UPDATE 落地（同一用户重复评分不新增记录）。
 * 3. sys_work.rating（decimal(3,1) NOT NULL）由 {@link #refreshWorkRating} 显式回写，
 *    不新增触发器：与既有 refreshWorkAggregates 同一约定（聚合列由代码维护）。
 *
 * @author xiangsipeng
 */
public interface AppWorkRatingMapper
{
    /**
     * 提交/覆盖评分（一人一评：已存在则只更新分值）
     *
     * @param userId 评分人ID（当前登录身份）
     * @param workId 作品ID
     * @param score  评分（2~10，每星 2 分；越界由控制层拒绝）
     * @return 影响行数（1=新增或更新成功，2=更新且值未变时 MySQL 的返回，均视为成功）
     */
    public int upsertRating(@Param("userId") Long userId, @Param("workId") Long workId, @Param("score") Integer score);

    /**
     * 查询当前身份对该作品的评分
     *
     * @param userId 评分人ID
     * @param workId 作品ID
     * @return 评分（2~10，每星 2 分）；未评分返回 null
     */
    public Integer selectMyScore(@Param("userId") Long userId, @Param("workId") Long workId);

    /**
     * 统计作品的评分数（去重后的评分人数）
     *
     * @param workId 作品ID
     * @return 评分数（0 表示无人评分）
     */
    public int countByWork(@Param("workId") Long workId);

    /**
     * 作品平均分
     *
     * @param workId 作品ID
     * @return ROUND(AVG(score), 1)；无人评分返回 null
     */
    public BigDecimal selectAvgRating(@Param("workId") Long workId);

    /**
     * 回写作品平均分到 sys_work.rating
     *
     * 无评分时回落到 0（该列 NOT NULL）。子查询取自 sys_work_rating（与 sys_work 不同表），
     * 不触发 MySQL 同表更新限制。
     *
     * @param workId 作品ID
     * @return 影响行数（作品不存在时为 0）
     */
    public int refreshWorkRating(@Param("workId") Long workId);
}
