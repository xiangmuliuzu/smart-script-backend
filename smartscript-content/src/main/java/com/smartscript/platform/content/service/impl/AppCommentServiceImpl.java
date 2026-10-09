package com.smartscript.platform.content.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.smartscript.platform.content.dto.AppCommentInsert;
import com.smartscript.platform.content.dto.AppCommentItem;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.mapper.AppCommentMapper;
import com.smartscript.platform.content.service.IAppCommentService;
import com.smartscript.platform.identity.IdentityProvider;

/**
 * 剧集评论 服务实现（App 2.8.10 / 2.8.11）
 *
 * 依据：接口文档 2.8.10 / 2.8.11 + 云端 script_platform_dev 库 sys_comment 表。
 *
 * 反推处理点：
 * 1. 身份不做空值兜底：/content/episodes/{id}/comments 为两段路径，未命中
 *    /content/episodes/* 单段公开白名单，过滤器已保证非游客，currentUserId() 不会为 null。
 * 2. parent_id 为 NOT NULL：一级评论写 0（不用 null），与前端「parent_id=0 为一级」约定一致。
 * 3. 计数显式维护（无触发器）：插入成功后自增剧集 comment_count；回复时自增父评论 reply_count。
 * 4. 分页 total 必须在包装前取（PageInfo 只对 PageHelper 返回的 Page 生效）。
 *
 * @author xiangsipeng
 */
@Service
public class AppCommentServiceImpl implements IAppCommentService
{
    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 每页条数上限 */
    private static final int PAGE_SIZE_MAX = 50;

    /** 一级评论的 parent_id 约定值 */
    private static final long ROOT_PARENT_ID = 0L;

    @Autowired
    private AppCommentMapper commentMapper;

    @Autowired
    private IdentityProvider identityProvider;

    @Override
    public AppPageResult<AppCommentItem> pageComments(Long episodeId, int pageNum, int pageSize)
    {
        int safePageNum = pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, PAGE_SIZE_MAX);
        PageHelper.startPage(safePageNum, safePageSize);
        List<AppCommentItem> rows = commentMapper.selectCommentsByEpisode(episodeId);
        long total = new PageInfo<>(rows).getTotal();
        return AppPageResult.of(total, rows);
    }

    @Override
    public boolean parentExistsInEpisode(Long parentId, Long episodeId)
    {
        if (parentId == null || parentId <= 0 || episodeId == null)
        {
            return false;
        }
        return commentMapper.countCommentInEpisode(parentId, episodeId) > 0;
    }

    @Override
    @Transactional
    public Long createComment(Long episodeId, Long parentId, String content)
    {
        Long userId = identityProvider.currentUserId();
        if (userId == null)
        {
            return null;
        }
        long parent = (parentId == null || parentId <= 0) ? ROOT_PARENT_ID : parentId;
        AppCommentInsert insert = new AppCommentInsert();
        insert.setEpisodeId(episodeId);
        insert.setUserId(userId);
        insert.setParentId(parent);
        insert.setContent(content);
        insert.setCreateBy(String.valueOf(userId));
        if (commentMapper.insertComment(insert) <= 0)
        {
            return null;
        }
        commentMapper.incrementEpisodeCommentCount(episodeId);
        if (parent != ROOT_PARENT_ID)
        {
            commentMapper.incrementParentReplyCount(parent);
        }
        return insert.getCommentId();
    }
}