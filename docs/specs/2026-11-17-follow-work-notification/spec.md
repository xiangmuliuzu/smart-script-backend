# Spec：关注创作者发布新作品通知

日期：2026-11-17
范围：`smart-script-backend`（消息/用户域、审核域）、`smart-script-app`（消息中心）、
`smart-script-web-admin`（消息类型选项）。
需求口径已由用户确认：**审核合并进系统，消息中心原「审核」位置换成「关注」**；
通知在**审核通过、作品自动上架时**发送。

---

## 1. 目标

用户关注某创作者后，该创作者的作品通过审核并上架（读者可见）时，关注者收到一条站内消息：

- 消息中心「关注」分类可见，未读数计入总角标；
- 标题/正文包含创作者与作品信息，详情页可直接跳到作品详情；
- 消息可单独关闭（通知偏好「关注动态」），关闭后不再投递该类站内信。

## 2. 消息类型调整

| 旧类型 | 新类型 | 说明 |
| --- | --- | --- |
| `SYSTEM` | `SYSTEM` | 系统消息 + 原 `REVIEW` 的实名/创作者申请审核结果 |
| `REVIEW` | 移除 | 生产者改投 `SYSTEM`；存量数据迁移为 `SYSTEM`；偏好项移除 |
| `TRANSACTION` | 不变 | 交易域 |
| `BENEFIT` | 不变 | 福利 |
| — | `FOLLOW` | 新增：关注的创作者发布新作品 |

App 消息中心筛选项顺序：全部 / 系统 / 关注 / 交易 / 福利。

## 3. 触发与投递

1. 审核通过有两个入口，均调用 `ReviewRecordMapper.markWorkOnShelf`：
   - 人工：`ReviewService.operateReview`（`pending → approved`）；
   - AI 高分直通：`AiReviewService.score`（`ai_reviewing → approved`）。
2. 仅当 `markWorkOnShelf` 实际影响 1 行（作品存在且未删除）时，发布
   `WorkPublishedEvent(workId, authorId, workTitle, authorName)`。
3. `smartscript-review` 不依赖 `smartscript-user`，事件类定义在
   `com.smartscript.platform.review.event`，由 `smartscript-user` 监听。
4. 监听器使用 `@TransactionalEventListener(AFTER_COMMIT)`：审核事务回滚不发送；
   监听器内用 `REQUIRES_NEW` 独立事务投递并吞掉异常，通知失败不影响审核。
5. 投递服务 `WorkPublishedNotifyService`：
   - 幂等键 `WORK-PUB-{workId}`，同一作品只发一次；作者下架后重新上架不重复通知；
   - 粉丝查询过滤：账号已删除/停用、非 App 账号域、与作者存在任一方向拉黑关系；
   - 按收件人「站内信 × FOLLOW」偏好过滤已关闭者（稀疏存储，未配置即开启）；
   - 消息主体 1 行 + 收件记录按 500 一批写入，避免单条 SQL 过大；
   - 通过现有 `RealtimePushService` 在事务提交后推送，在线客户端立即刷新
     （按「实时提醒 × FOLLOW」偏好过滤）。
6. 无粉丝时直接返回，不落空消息。

## 4. 消息字段

| 字段 | 值 |
| --- | --- |
| `type` | `FOLLOW` |
| `title` | 「{昵称}」发布了新作品；昵称缺失时为「关注的创作者发布了新作品」 |
| `body` | 您关注的创作者「{昵称}」发布了新作品。\n· 作品：《{标题}》\n可前往作品详情查看。 |
| `business_type` | `WORK` |
| `business_id` | `{workId}` |
| `audience` | `NULL`（通用通知，全端可见；不区分买卖角色） |
| `request_id` | `WORK-PUB-{workId}` |

## 5. 数据库

不新增表、不新增列。仅一个数据迁移：

- `UPDATE user_notification SET type='SYSTEM' WHERE type='REVIEW'`；
- `DELETE FROM user_notification_preference WHERE type='REVIEW'`（旧开关失效，统一由「系统」控制）。

## 6. App 改动

- `MessageType`：移除 `review`，新增 `follow('FOLLOW','关注')`；
- `PreferenceType`：移除 `review`，新增 `follow('FOLLOW','关注动态')`，系统项文案改为
  「平台消息、公告和审核结果」；
- 消息详情：`FOLLOW + businessType=WORK` 显示「查看作品」，跳 `workDetailUrl(workId)`；
- 消息列表行：关注消息显示可跳转角标；
- 本地通知轮询无需改动（按消息标题/摘要弹窗，天然覆盖新类型）。

## 7. 明确不做

- 不做关注/取关的即时通知（「谁关注了你」）；
- 不做作品重新上架通知；
- 不做离线推送（沿用现有 App 存活期本地通知 + 前台轮询）；
- 不改 PC 两端的消息页面（关注动态作为通用通知同样进入 PC 收件箱，保持未读数与列表口径一致）。
