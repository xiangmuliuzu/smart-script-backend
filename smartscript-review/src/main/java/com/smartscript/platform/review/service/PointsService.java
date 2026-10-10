package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.PointsAccount;
import com.smartscript.platform.review.domain.PointsRecord;
import com.smartscript.platform.review.mapper.PointsAccountMapper;
import com.smartscript.platform.review.mapper.PointsRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 积分Service业务层处理
 *
 * @author smartscript
 */
@Service
public class PointsService {

    @Autowired
    private PointsAccountMapper pointsAccountMapper;

    @Autowired
    private PointsRecordMapper pointsRecordMapper;

    public List<PointsAccount> selectPointsAccountList(PointsAccount pointsAccount) {
        return pointsAccountMapper.selectPointsAccountList(pointsAccount);
    }

    public List<PointsRecord> selectPointsRecordList(PointsRecord pointsRecord) {
        return pointsRecordMapper.selectPointsRecordList(pointsRecord);
    }
}
