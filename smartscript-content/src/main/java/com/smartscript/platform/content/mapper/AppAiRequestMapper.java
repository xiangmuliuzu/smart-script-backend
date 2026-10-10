package com.smartscript.platform.content.mapper;

import java.util.Date;
import org.apache.ibatis.annotations.Param;

/**
 * AI 请求 数据层（App 2.9.13 AI 大纲生成）。
 *
 * 依据：云端 script_platform_dev 库 sys_ai_request 表（附件5.1 表3-26）+ 接口文档 2.9.13。
 *
 * 反推处理点：
 * 1. request_no 由服务层生成（表上有唯一索引 uk_request_no）；
 *    request_id 为 auto_increment，本接口契约不返回，故不回填。
 * 2. created_at / updated_at 均 NOT NULL 且无默认值（实测 column_default 为空），故显式写 now()。
 * 3. 表上有触发器 trg_sys_ai_request_bu_finished_time（仅 BEFORE UPDATE），
 *    insert 不触发，故 finished_at 由本层显式写入。
 *
 * 命名说明：刻意不叫 SysAiRequestMapper —— MapperScanner 按类名注册 bean，避免与后续模块冲突。
 *
 * @author xiangsipeng
 */
public interface AppAiRequestMapper
{
    /**
     * 新增一条 AI 请求记录
     *
     * @param requestNo 请求编号（服务层生成，全局唯一）
     * @param userId    归属用户ID（当前登录身份）
     * @param workId    关联作品ID（可空）
     * @param capability 能力标识（如 outline）
     * @param quotaCost 消耗额度
     * @param status    状态（如 success）
     * @param provider  AI 服务提供方
     * @param promptHash 提示词摘要（sha256 十六进制）
     * @param startedAt 开始时间
     * @param finishedAt 结束时间
     * @return 影响行数
     */
    public int insertAiRequest(@Param("requestNo") String requestNo,
                               @Param("userId") Long userId,
                               @Param("workId") Long workId,
                               @Param("capability") String capability,
                               @Param("quotaCost") int quotaCost,
                               @Param("status") String status,
                               @Param("provider") String provider,
                               @Param("promptHash") String promptHash,
                               @Param("startedAt") Date startedAt,
                               @Param("finishedAt") Date finishedAt);
}