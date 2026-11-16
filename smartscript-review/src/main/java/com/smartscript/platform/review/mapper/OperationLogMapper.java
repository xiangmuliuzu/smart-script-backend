package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 操作日志Mapper
 *
 * @author smartscript
 */
@Mapper
public interface OperationLogMapper {

    /** 新增操作日志 */
    int insertOperationLog(OperationLog operationLog);

    /** 条件查询操作日志列表 */
    List<OperationLog> selectOperationLogList(OperationLog operationLog);

    /** 按模块+操作+目标查询（用于幂等/审计） */
    int countByModuleAndOperation(@Param("module") String module,
                                  @Param("operation") String operation,
                                  @Param("targetId") String targetId);
}
