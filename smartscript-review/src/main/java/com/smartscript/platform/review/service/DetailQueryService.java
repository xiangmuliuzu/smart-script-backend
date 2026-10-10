package com.smartscript.platform.review.service;

import com.smartscript.platform.review.mapper.DetailQueryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/**
 * 明细数据查询Service业务层处理
 *
 * @author smartscript
 */
@Service
public class DetailQueryService {

    @Autowired
    private DetailQueryMapper detailQueryMapper;

    public List<Map<String, Object>> selectWorkDetail(String keyword, String status, String startDate, String endDate) {
        return detailQueryMapper.selectWorkDetail(keyword, status, startDate, endDate);
    }

    public List<Map<String, Object>> selectUserDetail(String keyword, String userType, String startDate, String endDate) {
        return detailQueryMapper.selectUserDetail(keyword, userType, startDate, endDate);
    }

    public List<Map<String, Object>> selectAdRevenue(String startDate, String endDate) {
        return detailQueryMapper.selectAdRevenue(startDate, endDate);
    }
}
