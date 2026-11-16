package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.PointsRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 积分流水Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface PointsRecordMapper {

    List<PointsRecord> selectPointsRecordList(PointsRecord pointsRecord);

    /** 新增积分流水 */
    int insertPointsRecord(PointsRecord pointsRecord);

    /** 统计某用户某广告某天的领取次数（source=AD 且 related_id=adId） */
    int countAdClaims(@Param("userId") Long userId,
                      @Param("adId") Long adId,
                      @Param("date") String date);

    /** 统计某用户某天全部广告领取次数（source=AD） */
    int countAdDaily(@Param("userId") Long userId,
                     @Param("date") String date);
}
