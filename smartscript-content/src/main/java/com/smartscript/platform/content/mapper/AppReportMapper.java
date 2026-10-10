package com.smartscript.platform.content.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 内容举报 数据层（App 2.8.17 内容举报）
 *
 * 依据：云端 script_platform_dev 库 sys_report 表 + 接口文档表 2-110。
 *
 * 反推处理点：
 * 1. 只写新增，无查询/更新：本批不做举报进度回查（无对应契约）。
 * 2. user_id 由服务层取当前登录身份传入，请求体不接受 userId，避免冒名举报。
 * 3. status 由服务层传初始态 'pending'；handler_id/handle_result 留给后台处置流程（本批不写）。
 * 4. report_id 为 bigint 自增，Service 在**同一事务内**用 selectLastInsertId 取回，
 *    避免跨连接取到他人 last_insert_id。
 *
 * @author xiangsipeng
 */
public interface AppReportMapper
{
    /**
     * 新增举报
     *
     * @param userId      举报人ID（当前登录身份）
     * @param targetType  举报对象类型（≤30 字）
     * @param targetId    举报对象ID
     * @param reason      举报原因（≤50 字）
     * @param description 补充说明（可空，≤500 字）
     * @param status      状态（初始态 pending）
     * @return 影响行数
     */
    public int insertReport(@Param("userId") Long userId, @Param("targetType") String targetType,
            @Param("targetId") Long targetId, @Param("reason") String reason,
            @Param("description") String description, @Param("status") String status);

    /**
     * 取当前连接最近一次插入生成的自增ID（须与 insertReport 在同一事务/连接内调用）
     *
     * @return 最近生成的自增ID
     */
    public Long selectLastInsertId();
}