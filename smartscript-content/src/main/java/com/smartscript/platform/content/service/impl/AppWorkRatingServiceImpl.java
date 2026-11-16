package com.smartscript.platform.content.service.impl;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.dto.AppWorkDto;
import com.smartscript.platform.content.dto.AppWorkRatingDto;
import com.smartscript.platform.content.mapper.AppWorkRatingMapper;
import com.smartscript.platform.content.mapper.SysContentWorkMapper;
import com.smartscript.platform.content.service.IAppWorkRatingService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 读者评分服务实现（App 书城读者评分通道）
 *
 * 依据：sys_work_rating 表（迁移脚本 B_20261014_001）+ sys_work.rating（附件5.1 表3-14）。
 *
 * 反推处理点：
 * 1. 身份不做空值兜底：本组接口未登记 App 凭证域公开白名单（GET 亦已声明为 authenticated），
 *    过滤器已保证非游客，因此 currentUserId() 不会为 null（IdentityProvider 约定：
 *    无认证信息才返回游客）。
 * 2. 可见性复用 {@link SysContentWorkMapper#selectAppWorkById}，不在本服务另拼
 *    「已上架 + 未删除」条件，避免与 2.7.2 详情口径漂移。
 * 3. 平均分回写与读取同源：getRating/rate 返回的 rating 直接取自 sys_work.rating
 *    （即详情/列表/榜单所读同一列），避免出现「本接口算一套、展示读另一套」的双口径。
 *
 * @author xiangsipeng
 */
@Service
public class AppWorkRatingServiceImpl implements IAppWorkRatingService
{
    @Autowired
    private AppWorkRatingMapper ratingMapper;

    @Autowired
    private SysContentWorkMapper workMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    public AppWorkRatingDto getRating(Long workId)
    {
        AppWorkDto work = visibleWork(workId);
        if (work == null)
        {
            return null;
        }
        return build(work, ratingMapper.selectMyScore(identityProvider.currentUserId(), workId));
    }

    @Override
    public AppWorkRatingDto rate(Long workId, Integer score)
    {
        AppWorkDto work = visibleWork(workId);
        if (work == null)
        {
            return null;
        }
        Long userId = identityProvider.currentUserId();
        // 一人一评：唯一键 uk_work_user 落覆盖更新，重复提交不新增记录
        ratingMapper.upsertRating(userId, workId, score);
        // 回写作品平均分（App 详情页 / 书城列表 / 评分榜 / PC 排行榜同读该列）
        ratingMapper.refreshWorkRating(workId);
        // 重新读取作品，使返回的 rating 为回写后的最新值
        AppWorkDto refreshed = visibleWork(workId);
        return build(refreshed == null ? work : refreshed, score);
    }

    /**
     * 可见性判定：未上架 / 已删除 / 不存在返回 null（与 2.7.2 作品详情同口径）
     */
    private AppWorkDto visibleWork(Long workId)
    {
        if (workId == null)
        {
            return null;
        }
        return workMapper.selectAppWorkById(workId);
    }

    /**
     * 组装评分载荷：rating 取 sys_work.rating（无人评分时为 0），评分数取记录数
     *
     * @param work    可见作品（非空）
     * @param myScore 当前身份评分；null=未评分
     */
    private AppWorkRatingDto build(AppWorkDto work, Integer myScore)
    {
        AppWorkRatingDto dto = new AppWorkRatingDto();
        dto.setWorkId(work.getWorkId());
        dto.setRating(work.getRating() == null ? BigDecimal.ZERO : work.getRating());
        dto.setRatingCount(ratingMapper.countByWork(work.getWorkId()));
        dto.setMyScore(myScore);
        return dto;
    }
}
