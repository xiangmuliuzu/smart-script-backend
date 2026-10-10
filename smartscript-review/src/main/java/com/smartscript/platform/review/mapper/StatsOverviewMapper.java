package com.smartscript.platform.review.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/**
 * 运营统计Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface StatsOverviewMapper {

    /** 概览卡片聚合 */
    Map<String, Object> selectOverview();

    /** 近N天用户/作品趋势（按日期） */
    List<Map<String, Object>> selectTrend(@Param("days") int days);

    /** 创作者作品排行 */
    List<Map<String, Object>> selectCreatorRank(@Param("limit") int limit);
}
