package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.ReviewRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 审核记录Mapper接口
 *
 * @author smartscript
 */
@Mapper
public interface ReviewRecordMapper {

    /**
     * 查询审核记录列表
     */
    List<ReviewRecord> selectReviewRecordList(ReviewRecord reviewRecord);

    /**
     * 查询审核记录详情
     */
    ReviewRecord selectReviewRecordById(@Param("reviewId") Long reviewId);

    /**
     * 新增审核记录
     */
    int insertReviewRecord(ReviewRecord reviewRecord);

    /**
     * 修改审核记录
     */
    int updateReviewRecord(ReviewRecord reviewRecord);

    /**
     * 删除审核记录
     */
    int deleteReviewRecordById(@Param("reviewId") Long reviewId);

    /**
     * 审核通过后把作品置为已上架（lifecycle=approved 的可见性副产物；is_deleted=1 的行不处理）。
     */
    int markWorkOnShelf(@Param("workId") Long workId, @Param("updateBy") String updateBy);

    /**
     * 按状态统计审核记录数
     */
    java.util.List<java.util.Map<String, Object>> selectStatusGroup();
}
