package com.smartscript.platform.content.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.smartscript.platform.content.dto.AppAiWriteRecordInsert;
import com.smartscript.platform.content.dto.AppAiWriteRecordItem;

/**
 * AI 写作记录 数据层（App 2.9.10 写作 / 2.9.11 润色 / 2.9.12 记录列表）。
 *
 * 依据：云端 script_platform_dev 库 sys_ai_write_record 表（附件5.1 表3-22）+ 接口文档 2.9.10~2.9.12。
 *
 * 反推处理点：
 * 1. record_id 为 bigint auto_increment，insert 用 useGeneratedKeys 回填插入对象的主键。
 * 2. 所有查询强制带 user_id 条件，归属由服务层传入的当前登录身份决定。
 * 3. 分页由调用方 PageHelper 驱动，SQL 内不写 limit；按 created_at 倒序。
 *
 * 命名说明：刻意不叫 SysAiWriteRecordMapper —— MapperScanner 按类名注册 bean，
 * 后续模块落地同表 Mapper 会与本类冲突（参见 AppSearchHistoryMapper 的同类说明）。
 *
 * @author xiangsipeng
 */
public interface AppAiWriteRecordMapper
{
    /**
     * 新增一条 AI 写作记录（回填自增主键到入参对象 recordId）
     *
     * @param row 写入参数（recordId 由数据库生成后回填）
     * @return 影响行数
     */
    public int insertWriteRecord(AppAiWriteRecordInsert row);

    /**
     * 查询某用户的 AI 写作记录（按创建时间倒序；分页由 PageHelper 驱动）
     *
     * @param userId 用户ID
     * @return 记录条目集合
     */
    public List<AppAiWriteRecordItem> selectWriteRecordsByUser(@Param("userId") Long userId);
}