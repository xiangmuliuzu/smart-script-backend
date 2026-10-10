package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.SysFinanceAbnormal;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 财务异常Mapper接口
 */
public interface SysFinanceAbnormalMapper {

    /**
     * 查询财务异常列表
     */
    List<Map<String, Object>> selectAbnormalList(@Param("type") String type,
                                                  @Param("status") String status,
                                                  @Param("keyword") String keyword,
                                                  @Param("offset") int offset,
                                                  @Param("limit") int limit);

    /**
     * 统计财务异常数量
     */
    long countAbnormal(@Param("type") String type,
                       @Param("status") String status,
                       @Param("keyword") String keyword);

    /**
     * 根据ID查询异常详情
     */
    Map<String, Object> selectAbnormalById(@Param("abnormalId") Long abnormalId);

    /**
     * 插入异常记录
     */
    int insertAbnormal(SysFinanceAbnormal abnormal);

    /**
     * 更新处理结果
     */
    int updateHandleResult(@Param("abnormalId") Long abnormalId,
                          @Param("solution") String solution,
                          @Param("remark") String remark,
                          @Param("handleBy") String handleBy);
}
