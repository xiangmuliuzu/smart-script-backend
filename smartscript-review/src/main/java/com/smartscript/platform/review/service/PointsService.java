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

    /**
     * 管理员调整积分余额（按目标余额设置，差额计入获得/消耗并写流水）
     *
     * @param userId  用户ID
     * @param balance 目标余额（>=0）
     * @param remark  调整备注（可为空）
     * @return 调整后余额
     */
    @Transactional(rollbackFor = Exception.class)
    public int adjustBalance(Long userId, int balance, String remark) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (balance < 0) {
            throw new IllegalArgumentException("积分余额不能为负数");
        }
        PointsAccount account = pointsAccountMapper.selectByUserId(userId);
        if (account == null) {
            throw new IllegalStateException("该用户积分账户不存在");
        }
        int current = account.getBalance() == null ? 0 : account.getBalance();
        int delta = balance - current;
        if (delta == 0) {
            return current;
        }
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        int updated;
        if (delta > 0) {
            updated = pointsAccountMapper.increaseBalance(userId, delta, today);
        } else {
            updated = pointsAccountMapper.decreaseBalance(userId, -delta);
        }
        if (updated == 0) {
            throw new IllegalStateException("积分账户更新失败 userId=" + userId);
        }
        PointsRecord record = new PointsRecord();
        record.setUserId(userId);
        record.setChangeType(delta > 0 ? "EARN" : "SPEND");
        record.setPoints(Math.abs(delta));
        record.setBalanceAfter(balance);
        record.setSource("REVERSAL");
        String text = (remark == null || remark.trim().isEmpty())
                ? ("管理员调整余额：" + current + " → " + balance)
                : remark.trim();
        record.setRemark(text);
        pointsRecordMapper.insertPointsRecord(record);
        return balance;
    }
}
