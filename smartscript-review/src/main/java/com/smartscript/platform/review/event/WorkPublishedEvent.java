package com.smartscript.platform.review.event;

/**
 * 作品发布事件：审核通过后作品自动上架（读者可见）时发布。
 *
 * <p>审核模块不依赖用户模块，关注者通知由用户模块监听本事件完成，
 * 避免为了发通知让 review → user 形成反向依赖。</p>
 *
 * @param workId    作品主键（sys_work.work_id）
 * @param authorId  作者用户主键（sys_work.author_id）
 * @param workTitle 作品标题（审核记录关联查询得到，可能为空）
 * @param authorName 作者昵称（审核记录关联查询得到，可能为空）
 */
public record WorkPublishedEvent(Long workId, Long authorId, String workTitle, String authorName)
{
}
