package com.smartscript.platform.review.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/**
 * 明细数据查询Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface DetailQueryMapper {

    /** 作品明细（分页） */
    List<Map<String, Object>> selectWorkDetail(@Param("keyword") String keyword,
                                               @Param("status") String status,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate);

    /** 用户明细（分页） */
    List<Map<String, Object>> selectUserDetail(@Param("keyword") String keyword,
                                               @Param("userType") String userType,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate);

    /** 广告收益明细（预留） */
    List<Map<String, Object>> selectAdRevenue(@Param("startDate") String startDate,
                                              @Param("endDate") String endDate);
}
