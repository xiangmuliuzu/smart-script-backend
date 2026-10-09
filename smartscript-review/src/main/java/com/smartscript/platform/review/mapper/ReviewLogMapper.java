package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.ReviewLog;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 审核操作日志Mapper
 */
@Mapper
public interface ReviewLogMapper {

    public int insertReviewLog(ReviewLog reviewLog);

    public List<ReviewLog> selectReviewLogList(ReviewLog reviewLog);

    public ReviewLog selectReviewLogById(Long logId);
}
