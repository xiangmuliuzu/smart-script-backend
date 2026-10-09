package com.ruoyi.web.service.pc;

import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.mapper.SysFinanceAbnormalMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class FinanceAbnormalService {

    @Autowired
    private SysFinanceAbnormalMapper abnormalMapper;

    public List<Map<String, Object>> list(String type, String status, String keyword, int page, int size) {
        int offset = (page - 1) * size;
        return abnormalMapper.selectAbnormalList(type, status, keyword, offset, size);
    }

    public long count(String type, String status, String keyword) {
        return abnormalMapper.countAbnormal(type, status, keyword);
    }

    public Map<String, Object> getDetail(Long recordId) {
        if (recordId == null || recordId <= 0) {
            throw new IllegalArgumentException("记录ID无效");
        }
        Map<String, Object> detail = abnormalMapper.selectAbnormalById(recordId);
        if (detail == null) {
            throw new IllegalArgumentException("异常记录不存在");
        }
        return detail;
    }

    @Transactional(rollbackFor = Exception.class)
    public void handle(String type, Long recordId, String solution, String remark) {
        if (recordId == null || recordId <= 0) {
            throw new IllegalArgumentException("记录ID无效");
        }
        if (solution == null || solution.trim().isEmpty()) {
            throw new IllegalArgumentException("处理方案不能为空");
        }
        if (remark == null || remark.trim().isEmpty()) {
            throw new IllegalArgumentException("处理备注不能为空");
        }
        
        String username = SecurityUtils.getUsername();
        int rows = abnormalMapper.updateHandleResult(recordId, solution, remark, username);
        if (rows == 0) {
            throw new IllegalArgumentException("异常记录不存在或已处理");
        }
    }
}
