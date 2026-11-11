package com.smartscript.platform.content.service;

import com.smartscript.platform.content.dto.AppAiOutlineRequest;
import com.smartscript.platform.content.dto.AppAiOutlineRecordItem;
import com.smartscript.platform.content.dto.AppAiOutlineResult;
import com.smartscript.platform.content.dto.AppAiPolishRequest;
import com.smartscript.platform.content.dto.AppAiStoryboardRequest;
import com.smartscript.platform.content.dto.AppAiStoryboardResult;
import com.smartscript.platform.content.dto.AppAiWriteRecordItem;
import com.smartscript.platform.content.dto.AppAiWriteRequest;
import com.smartscript.platform.content.dto.AppAiWriteResult;
import com.smartscript.platform.content.dto.AppPageResult;
import java.util.List;
import java.util.Map;

/**
 * AI 辅助创作服务（App 2.9.10 写作 / 2.9.11 润色 / 2.9.12 记录 / 2.9.13 大纲）。
 *
 * 依据：接口文档 2.9.10~2.9.13 + 云端 script_platform_dev 库
 * sys_ai_write_record 表（附件5.1 表3-22）/ sys_ai_request 表（附件5.1 表3-26）。
 *
 * 边界：
 * 1. 归属一律取当前 App 登录身份（IdentityProvider），方法签名不接受 userId 入参。
 * 2. 生成内容一律来自外部 AI 服务（{@link AiServiceClient}），服务不可用时抛异常由控制器转 503，
 *    绝不返回编造内容。
 * 3. 本批**不接额度扣减**（额度账户在 smartscript-review 模块，跨模块不接），
 *    大纲接口的 quota_cost 固定记 1，不写额度流水。
 *
 * @author xiangsipeng
 */
public interface IAiWriteService
{
    /**
     * AI 写作（2.9.10）
     *
     * @param request 写作请求（prompt 必填，调用方已校验）
     * @return {content, recordId}（内容来自 AI 服务，并落 sys_ai_write_record）
     */
    public AppAiWriteResult write(AppAiWriteRequest request);

    /**
     * AI 润色（2.9.11）
     *
     * @param request 润色请求（content 必填，调用方已校验）
     * @return {content, recordId}（润色结果来自 AI 服务，并落 sys_ai_write_record）
     */
    public AppAiWriteResult polish(AppAiPolishRequest request);

    /**
     * AI 写作记录（2.9.12，分页）
     *
     * @param pageNum  页码（<=0 取默认 1）
     * @param pageSize 每页条数（<=0 取默认 20，上限 50）
     * @return {total, list}（仅当前登录身份的记录）
     */
    public AppPageResult<AppAiWriteRecordItem> pageWriteRecords(int pageNum, int pageSize);

    /**
     * 删除当前登录用户自己的若干条 AI 记录（批量）
     *
     * @param recordIds 记录ID集合；为空时直接返回 0
     * @return 实际删除条数
     */
    public int deleteWriteRecords(List<Long> recordIds);

    /**
     * AI 大纲生成（2.9.13）
     *
     * @param request 大纲请求（四个入参均可空）
     * @return {outline, requestNo, quotaUsed}（大纲来自 AI 服务，并落 sys_ai_request）
     */
    public AppAiOutlineResult outline(AppAiOutlineRequest request);

    /**
     * AI 大纲请求记录（分页）
     *
     * @param pageNum  页码（<=0 取默认 1）
     * @param pageSize 每页条数（<=0 取默认 20，上限 50）
     * @return {total, list}（仅当前登录身份的大纲请求记录）
     */
    public AppPageResult<AppAiOutlineRecordItem> pageOutlineRecords(int pageNum, int pageSize);

    /**
     * AI 写作（流式，SSE 打字机输出）。
     *
     * 流程：校验次数 → 流式调用外部 AI（[onDelta] 逐块回调内容增量）→
     * 流成功结束后落 sys_ai_write_record 并扣减次数；流中途失败/中断不落库、不扣次数。
     *
     * @param request  写作请求（prompt 必填，调用方已校验）
     * @param onDelta  内容增量回调（可为空操作）
     * @return {content, recordId}（完整内容来自 AI 服务）
     */
    public AppAiWriteResult writeStream(AppAiWriteRequest request, java.util.function.Consumer<String> onDelta);

    /**
     * AI 润色（流式，SSE 打字机输出）。
     *
     * 流程与 {@link #writeStream} 一致：成功才落库扣次数。
     *
     * @param request  润色请求（content 必填，调用方已校验）
     * @param onDelta  内容增量回调（可为空操作）
     * @return {content, recordId}
     */
    public AppAiWriteResult polishStream(AppAiPolishRequest request, java.util.function.Consumer<String> onDelta);

    /**
     * AI 大纲（流式，SSE 打字机输出）。
     *
     * 流程与 {@link #writeStream} 一致：成功才落库扣次数。
     *
     * @param request  大纲请求（入参均可空）
     * @param onDelta  内容增量回调（可为空操作）
     * @return {outline, requestNo, quotaUsed}
     */
    public AppAiOutlineResult outlineStream(AppAiOutlineRequest request, java.util.function.Consumer<String> onDelta);

    /**
     * AI 分镜（E 模块）：把剧本文本拆解为可直接用于拍摄/出售的分镜脚本。
     *
     * 剧本→分镜一条龙：可作为「AI 剧本」的下一步，产出可直接出售的分镜剧本；
     * 支持导演(skill) 注入（skillId → skill 内容追加到 system prompt）。
     *
     * @param request 分镜请求（script 必填，调用方已校验）
     * @return {content, requestNo, quotaUsed}（分镜来自 AI 服务，并落 sys_ai_request + sys_ai_write_record）
     */
    public AppAiStoryboardResult storyboard(AppAiStoryboardRequest request);

    /**
     * AI 分镜（流式，SSE 打字机输出）。
     *
     * 流程与 {@link #writeStream} 一致：成功才落库扣积分。
     *
     * @param request  分镜请求（script 必填）
     * @param onDelta  内容增量回调（可为空操作）
     * @return {content, requestNo, quotaUsed}
     */
    public AppAiStoryboardResult storyboardStream(AppAiStoryboardRequest request, java.util.function.Consumer<String> onDelta);

    /**
     * 导演(skill) 轻量列表（E 模块）
     *
     * 返回全部正常状态 skill 的 {id, skillName, fileName}，供 PC/App 端
     * 「导演(skill)选择」下拉使用。
     *
     * @return skill 轻量列表
     */
    public List<Map<String, Object>> listEnabledSkills();

    /**
     * 用户端可选模型列表（E 模块）
     *
     * 返回全部启用渠道的 {channelId, channelName, provider, models, defaultModel}
     * （不含 apiKey），供 PC/App 端「模型选择」下拉使用；调用时把选中的模型名
     * 随请求体 model 字段上传，后端按模型路由到对应渠道。
     *
     * @return 渠道 + 模型列表
     */
    public List<Map<String, Object>> listEnabledModels();

    /**
     * 本章摘要 + 台账回写（方案 2：正文定稿后单独一次 AI 调用）。
     *
     * 读本集正文（优先用入参 content，便于前端刚插入正文、尚未保存时立即生成），
     * 让模型产出「本章摘要 + 更新后的七类创作台账」JSON；后端把摘要写入
     * sys_work_chapter.summary，台账按字段合并进 sys_work_ledger（人工内容不被清空）。
     * 调用成功才扣积分（按实际 token）。正文不受任何改动。
     *
     * @param workId    作品ID（必填，且必须属于当前登录用户）
     * @param chapterId 章节ID（必填，且必须属于该作品）
     * @param content   待总结的正文（可为空，为空时读库中正文）
     * @param model     模型名（可选，空则用默认渠道）
     * @return {summary, ledgerUpdated, recordId, tokens}
     */
    public Map<String, Object> summarizeChapter(Long workId, Long chapterId, String content, String model);

    /**
     * 每集连贯性验收（正文定稿后一次小调用）。
     *
     * 固定 5 项判定：人物、因果、连续性、伏笔、结构；≥4 项无 fail 视为通过。
     * 结果落 sys_chapter_review，不改正文、不改台账；调用成功才扣积分。
     *
     * @param workId    作品ID（必填，且属于当前登录用户）
     * @param chapterId 章节ID（必填）
     * @param content   待验收正文（可为空，为空读库中正文）
     * @param model     模型名（可选）
     * @return {pass, score, issues, recordId, tokens}
     */
    public Map<String, Object> reviewChapter(Long workId, Long chapterId, String content, String model);

    /**
     * 生成卷级状态快照（每 10 集一卷，覆盖式当前值）。
     *
     * 读该卷区间各集摘要 + 台账，压成 ≤800 字快照，写 sys_work_volume。
     *
     * @param workId   作品ID（必填，且属于当前登录用户）
     * @param volumeNo 卷号（从 1 开始）
     * @param model    模型名（可选）
     * @return {volumeNo, episodeStart, episodeEnd, snapshot, recordId, tokens}
     */
    public Map<String, Object> buildVolumeSnapshot(Long workId, Integer volumeNo, String model);

    /**
     * 读取作品全部卷快照（写正文时注入「本卷 + 上卷」用）。
     *
     * @param workId 作品ID
     * @return [{volumeNo, episodeStart, episodeEnd, snapshot, updateTime}]
     */
    public List<Map<String, Object>> listVolumes(Long workId);

    /**
     * 手工保存卷快照（无 AI 调用，便于人工修订）。
     *
     * @param workId   作品ID
     * @param volumeNo 卷号
     * @param snapshot 快照文本
     * @return 是否写入成功
     */
    public boolean saveVolume(Long workId, Integer volumeNo, String snapshot);
}