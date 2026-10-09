package com.smartscript.platform.content.service;

import com.smartscript.platform.content.dto.AppAiOutlineRequest;
import com.smartscript.platform.content.dto.AppAiOutlineResult;
import com.smartscript.platform.content.dto.AppAiPolishRequest;
import com.smartscript.platform.content.dto.AppAiWriteRecordItem;
import com.smartscript.platform.content.dto.AppAiWriteRequest;
import com.smartscript.platform.content.dto.AppAiWriteResult;
import com.smartscript.platform.content.dto.AppPageResult;

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
     * AI 大纲生成（2.9.13）
     *
     * @param request 大纲请求（四个入参均可空）
     * @return {outline, requestNo, quotaUsed}（大纲来自 AI 服务，并落 sys_ai_request）
     */
    public AppAiOutlineResult outline(AppAiOutlineRequest request);
}