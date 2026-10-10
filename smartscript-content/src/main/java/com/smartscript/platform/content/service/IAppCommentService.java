package com.smartscript.platform.content.service;

import com.smartscript.platform.content.dto.AppCommentItem;
import com.smartscript.platform.content.dto.AppPageResult;

/**
 * 剧集评论 服务层（App 2.8.10 列表 / 2.8.11 发表）
 *
 * 依据：接口文档 2.8.10 / 2.8.11 + 云端 script_platform_dev 库 sys_comment 表。
 *
 * 边界：
 *   - 全部为 App 私有接口（路径两段，未命中公开白名单），归属一律取
 *     {@code IdentityProvider.currentUserId()}，不接收请求体 userId。
 *   - 只做单表写入与最小计数派生（剧集 comment_count、父评论 reply_count），
 *     不含审核、屏蔽等状态流转（status 由管理端维护）。
 *
 * @author xiangsipeng
 */
public interface IAppCommentService
{
    /**
     * 剧集评论列表（2.8.10，分页）
     *
     * 口径：episode_id + status=1，平铺下发（含 parent_id），按创建顺序升序。
     *
     * @param episodeId 剧集ID
     * @param pageNum   页码（从 1 起）
     * @param pageSize  每页条数
     * @return 分页结果（total + list）
     */
    public AppPageResult<AppCommentItem> pageComments(Long episodeId, int pageNum, int pageSize);

    /**
     * 校验父评论是否属于指定剧集（2.8.11 回复前置校验）
     *
     * @param parentId  父评论ID
     * @param episodeId 剧集ID
     * @return true 同剧集且可见；false 不存在或跨剧集
     */
    public boolean parentExistsInEpisode(Long parentId, Long episodeId);

    /**
     * 发表评论 / 回复（2.8.11）
     *
     * 事务内：写入 sys_comment，并自增剧集 comment_count；parentId &gt; 0 时再自增父评论 reply_count。
     *
     * @param episodeId 剧集ID
     * @param parentId  父评论ID（0 或缺省为一级评论）
     * @param content   评论内容（控制层已校验非空且 ≤500）
     * @return 新评论ID；写入失败返回 null
     */
    public Long createComment(Long episodeId, Long parentId, String content);
}