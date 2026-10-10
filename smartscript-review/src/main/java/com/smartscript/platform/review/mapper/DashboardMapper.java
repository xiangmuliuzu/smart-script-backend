package com.smartscript.platform.review.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/**
 * 数据总览Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface DashboardMapper {

    /** 指标卡/快捷入口聚合 */
    Map<String, Object> selectOverviewRow();

    /** 作品状态分布 */
    List<Map<String, Object>> selectWorkStatusDist();

    /** 交易趋势：近7日 */
    List<Map<String, Object>> selectTrendWeek();

    /** 交易趋势：近30日 */
    List<Map<String, Object>> selectTrendMonth();

    /** 交易趋势：近12月 */
    List<Map<String, Object>> selectTrendYear();

    /** 最近审核列表 */
    List<Map<String, Object>> selectRecentReviews(@Param("limit") int limit);
}
