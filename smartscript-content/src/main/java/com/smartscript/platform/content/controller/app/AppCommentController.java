package com.smartscript.platform.content.controller.app;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.content.dto.AppCommentCreateRequest;
import com.smartscript.platform.content.dto.AppCommentItem;
import com.smartscript.platform.content.dto.AppEpisodeLikeResult;
import com.smartscript.platform.content.dto.AppPageResult;
import com.smartscript.platform.content.service.IAppCommentService;
import com.smartscript.platform.content.service.IAppEpisodeLikeService;
import com.smartscript.platform.content.service.IAppPlayService;

/**
 * App 剧集评论与点赞（B 模块，接口文档 2.8.10 表2-103 / 2.8.11 表2-104 / 2.8.12 表2-105）。
 *
 * 鉴权：全部为 App 私有接口，需 App Access Token。路径均为两段
 * （/episodes/{id}/comments、/episodes/{id}/like），不命中 /content/episodes/* 单段公开白名单，
 * 授权层按 authenticated 拒绝游客，无需改动 AppAuthSecurityConfig。
 *
 * 路径映射：文档写的是 /api/episodes/:id/comments 等，B 模块既有接口统一收拢在
 * App 凭证域 /api/v1/content/** 下，故落此前缀。
 *
 * 归属：评论人/点赞人一律取当前登录身份，不接收请求体 userId。
 * 剧集存在性复用 {@link IAppPlayService#getEpisode}（与 2.8.3 剧集详情同一可见性口径）。
 *
 * @author xiangsipeng
 */
@RestController
@RequestMapping("/api/v1/content/episodes/{episodeId}")
public class AppCommentController
{
    /** 剧集不存在 */
    private static final int CODE_NOT_FOUND = 404;

    /** 入参非法（内容为空/超长、回复的评论无效） */
    private static final int CODE_BAD_REQUEST = 400;

    /** 服务端失败 */
    private static final int CODE_SYSTEM_ERROR = 500;

    /** 评论内容长度上限（与 sys_comment.content varchar(500) 一致） */
    private static final int CONTENT_MAX_LENGTH = 500;

    /** 评论默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final IAppCommentService commentService;

    private final IAppEpisodeLikeService likeService;

    private final IAppPlayService playService;

    public AppCommentController(IAppCommentService commentService,
            IAppEpisodeLikeService likeService, IAppPlayService playService)
    {
        this.commentService = commentService;
        this.likeService = likeService;
        this.playService = playService;
    }

    /**
     * 评论列表（2.8.10，分页，含嵌套回复的平铺数据）
     *
     * @param episodeId 剧集ID
     * @param page      页码（可选，默认 1）
     * @param pageSize  每页条数（可选，默认 20，上限 50）
     * @return data = {total, list}；list 元素含 parentId（0=一级），前端按 parentId 组两层
     */
    @GetMapping("/comments")
    public AppApiResponse<AppPageResult<AppCommentItem>> comments(
            @PathVariable Long episodeId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize)
    {
        if (playService.getEpisode(episodeId) == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "剧集不存在");
        }
        int pageNum = page == null ? 1 : page;
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        return AppApiResponse.ok(commentService.pageComments(episodeId, pageNum, size));
    }

    /**
     * 发表评论 / 回复（2.8.11）
     *
     * @param episodeId 剧集ID
     * @param request   body {content 必填≤500, parentId 可选}
     * @return data = {commentId, message}；剧集不存在 404，content 非法或 parentId 跨剧集 400
     */
    @PostMapping("/comments")
    public AppApiResponse<Map<String, Object>> createComment(
            @PathVariable Long episodeId,
            @RequestBody(required = false) AppCommentCreateRequest request)
    {
        if (playService.getEpisode(episodeId) == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "剧集不存在");
        }
        String content = request == null ? null : request.getContent();
        content = content == null ? null : content.trim();
        if (content == null || content.isEmpty())
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "content 不能为空");
        }
        if (content.length() > CONTENT_MAX_LENGTH)
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "content 长度不能超过 " + CONTENT_MAX_LENGTH);
        }
        Long parentId = request.getParentId();
        if (parentId != null && parentId > 0 && !commentService.parentExistsInEpisode(parentId, episodeId))
        {
            return AppApiResponse.fail(CODE_BAD_REQUEST, "回复的评论不存在或不属于该剧集");
        }
        Long commentId = commentService.createComment(episodeId, parentId, content);
        if (commentId == null)
        {
            return AppApiResponse.fail(CODE_SYSTEM_ERROR, "评论失败，请稍后重试");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("commentId", commentId);
        data.put("message", "评论成功");
        return AppApiResponse.ok(data);
    }

    /**
     * 点赞（2.8.12，幂等）
     *
     * @param episodeId 剧集ID
     * @return data = {liked:true, likeCount}；剧集不存在 404
     */
    @PostMapping("/like")
    public AppApiResponse<AppEpisodeLikeResult> like(@PathVariable Long episodeId)
    {
        if (playService.getEpisode(episodeId) == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "剧集不存在");
        }
        return AppApiResponse.ok(likeService.like(episodeId));
    }

    /**
     * 取消点赞（2.8.12，幂等）
     *
     * @param episodeId 剧集ID
     * @return data = {liked:false, likeCount}；剧集不存在 404
     */
    @DeleteMapping("/like")
    public AppApiResponse<AppEpisodeLikeResult> unlike(@PathVariable Long episodeId)
    {
        if (playService.getEpisode(episodeId) == null)
        {
            return AppApiResponse.fail(CODE_NOT_FOUND, "剧集不存在");
        }
        return AppApiResponse.ok(likeService.unlike(episodeId));
    }
}