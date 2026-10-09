package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.Report;
import com.smartscript.platform.review.mapper.ReportMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 举报/违规记录Service业务层处理
 */
@Service
public class ReportService {

    @Autowired
    private ReportMapper reportMapper;

    public List<Report> selectReportList(Report report) {
        return reportMapper.selectReportList(report);
    }

    public Report selectReportById(Long reportId) {
        return reportMapper.selectReportById(reportId);
    }

    public int insertReport(Report report) {
        return reportMapper.insertReport(report);
    }

    public int updateReport(Report report) {
        return reportMapper.updateReport(report);
    }

    public int deleteReportById(Long reportId) {
        return reportMapper.deleteReportById(reportId);
    }
}
