package com.smartscript.platform.review.mapper;

import com.smartscript.platform.review.domain.Report;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 举报/违规记录 Mapper
 */
public interface ReportMapper {

    List<Report> selectReportList(Report report);

    Report selectReportById(@Param("reportId") Long reportId);

    int insertReport(Report report);

    int updateReport(Report report);

    int deleteReportById(@Param("reportId") Long reportId);
}
