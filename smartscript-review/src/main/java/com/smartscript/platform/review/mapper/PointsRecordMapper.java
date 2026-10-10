package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.PointsRecord;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 积分流水Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface PointsRecordMapper {

    List<PointsRecord> selectPointsRecordList(PointsRecord pointsRecord);
}
