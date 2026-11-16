package com.smartscript.platform.content.service;

import com.smartscript.platform.content.dto.AppWorkRatingDto;

/**
 * 作品读者评分服务（App 书城读者评分通道）
 *
 * 依据：设计文档只定义了作品平均分列 sys_work.rating（附件5.1 表3-14），
 * 读者侧无评分表也无提交接口；本服务按「一人一评、可覆盖」补齐，
 * 存储落在 sys_work_rating（迁移脚本
 * sql/migrations/b/B_20261014_001__create_work_rating_table.sql）。
 *
 * 边界：归属一律取当前登录身份，写路径不接收 userId；可见性与 2.7.2 作品详情同口径
 * （未上架 / 已删除 / 不存在均不可评分）。提交后把 ROUND(AVG(score),1) 回写
 * sys_work.rating，App 详情页、书城列表、评分榜与 PC 排行榜据此同步。
 *
 * @author xiangsipeng
 */
public interface IAppWorkRatingService
{
    /**
     * 查询作品评分态（当前身份视角，接口：GET /api/v1/content/works/{workId}/rating）
     *
     * @param workId 作品ID
     * @return 评分载荷 {workId, rating, ratingCount, myScore}；
     *         作品未上架/已删除/不存在返回 null，由控制层按 404 处理
     */
    public AppWorkRatingDto getRating(Long workId);

    /**
     * 提交评分（接口：POST /api/v1/content/works/{workId}/rating）
     *
     * 一人一评：同一身份重复提交为覆盖更新（分值变更，评分数不增长）；
     * 随后回写作品平均分并返回最新聚合。
     *
     * @param workId 作品ID
     * @param score  评分（2~10，每星 2 分；已由控制层校验，本方法不再兜底）
     * @return 提交后的评分载荷；作品未上架/已删除/不存在返回 null，由控制层按 404 处理
     */
    public AppWorkRatingDto rate(Long workId, Integer score);
}
