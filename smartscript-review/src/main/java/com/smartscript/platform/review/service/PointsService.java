package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.PointsAccount;
import com.smartscript.platform.review.domain.PointsRecord;
import com.smartscript.platform.review.mapper.PointsAccountMapper;
import com.smartscript.platform.review.mapper.PointsRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
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

    /**
     * 给用户发放积分（公共方法：任务/广告/兑换/补偿共用）
     *
     * @param userId     用户ID
     * @param points     积分数（>0）
     * @param source     来源 TASK-任务 AD-广告 EXCHANGE-兑换 REVERSAL-补偿
     * @param relatedId  关联业务ID（任务ID/广告ID等）
     * @param remark     备注
     * @return 发放后余额；用户账户不存在时自动创建
     */
    @Transactional(rollbackFor = Exception.class)
    public int grantPoints(Long userId, int points, String source, Long relatedId, String remark) {
        if (userId == null || points <= 0) {
            throw new IllegalArgumentException("userId与points必须为正");
        }
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        // 账户不存在则自动创建（余额0）
        PointsAccount account = pointsAccountMapper.selectByUserId(userId);
        if (account == null) {
            PointsAccount na = new PointsAccount();
            na.setUserId(userId);
            na.setLastUpdateDate(new Date());
            pointsAccountMapper.insertAccount(na);
        }
        int updated = pointsAccountMapper.increaseBalance(userId, points, today);
        if (updated == 0) {
            throw new IllegalStateException("积分账户更新失败 userId=" + userId);
        }
        PointsAccount fresh = pointsAccountMapper.selectByUserId(userId);
        int balanceAfter = fresh.getBalance();
        // 写流水
        PointsRecord record = new PointsRecord();
        record.setUserId(userId);
        record.setChangeType("EARN");
        record.setPoints(points);
        record.setBalanceAfter(balanceAfter);
        record.setSource(source);
        record.setRelatedId(relatedId);
        record.setRemark(remark);
        pointsRecordMapper.insertPointsRecord(record);
        return balanceAfter;
    }
}
