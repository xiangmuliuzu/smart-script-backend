package com.smartscript.platform.review.service;

import com.smartscript.platform.review.domain.AiQuotaAccount;
import com.smartscript.platform.review.domain.AiQuotaRecord;
import com.smartscript.platform.review.domain.AiRequest;
import com.smartscript.platform.review.domain.PointsAccount;
import com.smartscript.platform.review.domain.PointsRecord;
import com.smartscript.platform.review.mapper.AiQuotaAccountMapper;
import com.smartscript.platform.review.mapper.AiQuotaRecordMapper;
import com.smartscript.platform.review.mapper.AiRequestMapper;
import com.smartscript.platform.review.mapper.PointsAccountMapper;
import com.smartscript.platform.review.mapper.PointsRecordMapper;
import com.smartscript.platform.review.mapper.OperationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AI配额Service业务层处理
 * 在管理页CRUD之上增加业务闭环：次数消耗（防重复扣减）/失败补偿（refund）/余额查询
 *
 * @author smartscript
 */
@Service
public class AiQuotaService {

    private static final Logger log = LoggerFactory.getLogger(AiQuotaService.class);

    @Autowired
    private AiQuotaAccountMapper aiQuotaAccountMapper;

    @Autowired
    private AiQuotaRecordMapper aiQuotaRecordMapper;

    @Autowired
    private AiRequestMapper aiRequestMapper;

    @Autowired
    private PointsAccountMapper pointsAccountMapper;

    @Autowired
    private PointsRecordMapper pointsRecordMapper;

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 【去次数化】1积分对应token数（默认1000 token=1积分；存 sys_config key=ai.token.per.point，管理员可调） */
    private static final int DEFAULT_TOKEN_PER_POINT = 1000;

    /** 读取「1积分=N token」配置：优先 sys_config(ai.token.per.point)，异常/缺失回退默认值 */
    public int getTokenPerPoint() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT config_value FROM sys_config WHERE config_key = 'ai.token.per.point' LIMIT 1");
            if (!rows.isEmpty() && rows.get(0).get("config_value") != null) {
                int v = Integer.parseInt(String.valueOf(rows.get(0).get("config_value")).trim());
                if (v >= 1) return v;
            }
        } catch (Exception e) {
            log.warn("读取 ai.token.per.point 配置失败，使用默认值: {}", e.getMessage());
        }
        return DEFAULT_TOKEN_PER_POINT;
    }

    /** 管理员更新「1积分=N token」（sys_config upsert） */
    public Map<String, Object> updateTokenPerPoint(int tokenPerPoint) {
        Map<String, Object> result = new HashMap<>();
        if (tokenPerPoint < 1) {
            result.put("code", 400);
            result.put("msg", "token/积分 必须 >= 1");
            return result;
        }
        try {
            int rows = jdbcTemplate.update(
                "UPDATE sys_config SET config_value = ?, update_time = NOW() WHERE config_key = 'ai.token.per.point'",
                String.valueOf(tokenPerPoint));
            if (rows == 0) {
                jdbcTemplate.update(
                    "INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_time, remark) "
                    + "VALUES ('AI创作token/积分配置', 'ai.token.per.point', ?, 'Y', NOW(), '1积分可消耗的token数，AI创作按实际token扣积分')",
                    String.valueOf(tokenPerPoint));
            }
            result.put("code", 200);
            result.put("msg", "已更新为 1 积分 = " + tokenPerPoint + " token");
            result.put("tokenPerPoint", tokenPerPoint);
            return result;
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "更新 token/积分 配置失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 查询AI配额账户列表
     */
    public List<AiQuotaAccount> selectAiQuotaAccountList(AiQuotaAccount aiQuotaAccount) {
        return aiQuotaAccountMapper.selectAiQuotaAccountList(aiQuotaAccount);
    }

    /**
     * 查询AI配额账户详情
     */
    public AiQuotaAccount selectAiQuotaAccountById(Long accountId) {
        return aiQuotaAccountMapper.selectAiQuotaAccountById(accountId);
    }

    /**
     * 新增AI配额账户
     */
    public int insertAiQuotaAccount(AiQuotaAccount aiQuotaAccount) {
        return aiQuotaAccountMapper.insertAiQuotaAccount(aiQuotaAccount);
    }

    /**
     * 修改AI配额账户
     */
    public int updateAiQuotaAccount(AiQuotaAccount aiQuotaAccount) {
        return aiQuotaAccountMapper.updateAiQuotaAccount(aiQuotaAccount);
    }

    /**
     * 删除AI配额账户
     */
    public int deleteAiQuotaAccountById(Long accountId) {
        return aiQuotaAccountMapper.deleteAiQuotaAccountById(accountId);
    }

    /**
     * 查询AI配额记录列表
     */
    public List<AiQuotaRecord> selectAiQuotaRecordList(AiQuotaRecord aiQuotaRecord) {
        return aiQuotaRecordMapper.selectAiQuotaRecordList(aiQuotaRecord);
    }

    /**
     * 新增AI配额记录
     */
    public int insertAiQuotaRecord(AiQuotaRecord aiQuotaRecord) {
        return aiQuotaRecordMapper.insertAiQuotaRecord(aiQuotaRecord);
    }

    public int countAccounts() {
        return aiQuotaAccountMapper.countAccounts();
    }

    public int countTodayCalls() {
        return aiQuotaRecordMapper.countTodayCalls();
    }

    public int countFailedCalls() {
        return aiQuotaRecordMapper.countFailedCalls();
    }

    public int countRefundedCalls() {
        return aiQuotaRecordMapper.countRefundedCalls();
    }

    /* ==================== 业务闭环 ==================== */

    /**
     * AI次数消耗（预扣）。防重复扣减：idempotencyKey 唯一约束；并发安全：version 乐观锁。
     *
     * @param userId        用户ID
     * @param capability    AI能力 outline/writing/polish
     * @param quotaCost     消耗次数
     * @param workId        关联作品（可空）
     * @param idempotencyKey 幂等键（同一调用重复请求不重复扣）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> consume(Long userId, String capability, Integer quotaCost, Long workId, String idempotencyKey) {
        Map<String, Object> result = new HashMap<>();
        if (userId == null || quotaCost == null || quotaCost <= 0 || !StringUtils.hasText(capability) || !StringUtils.hasText(idempotencyKey)) {
            result.put("code", 400);
            result.put("msg", "参数不完整：userId/capability/quotaCost/idempotencyKey 必填，quotaCost>0");
            return result;
        }
        AiQuotaAccount account = aiQuotaAccountMapper.selectAiQuotaAccountByUserId(userId);
        if (account == null) {
            // 首次使用自动建账户（初始0次，需先获取次数）
            account = new AiQuotaAccount();
            account.setUserId(userId);
            account.setAvailableQuota(BigDecimal.ZERO);
            account.setReservedQuota(BigDecimal.ZERO);
            account.setTotalEarned(BigDecimal.ZERO);
            account.setTotalConsumed(BigDecimal.ZERO);
            account.setTotalRefunded(BigDecimal.ZERO);
            account.setVersion(0L);
            aiQuotaAccountMapper.insertAiQuotaAccount(account);
            account.setAvailableQuota(BigDecimal.ZERO);
            account.setVersion(0L);
        }
        BigDecimal cost = BigDecimal.valueOf(quotaCost);
        if (account.getAvailableQuota().compareTo(cost) < 0) {
            result.put("code", 400);
            result.put("msg", "AI次数不足，当前可用: " + account.getAvailableQuota().intValue() + " 次");
            return result;
        }
        Long version = account.getVersion() == null ? 0L : account.getVersion();
        BigDecimal before = account.getAvailableQuota();
        // 1) 先插流水（幂等键唯一，重复请求在此抛异常回滚，不重复扣减）
        AiQuotaRecord record = new AiQuotaRecord();
        record.setRecordNo(genNo("QR"));
        record.setUserId(userId);
        record.setType("consume");
        record.setAmount(cost);
        record.setBeforeBalance(before);
        record.setBalance(before.subtract(cost));
        record.setBusinessType(capability);
        record.setRelatedId(workId == null ? null : String.valueOf(workId));
        record.setIdempotencyKey(idempotencyKey);
        record.setCreateTime(new Date());
        record.setRemark("AI调用扣减: " + capability);
        try {
            aiQuotaRecordMapper.insertAiQuotaRecord(record);
        } catch (DuplicateKeyException e) {
            result.put("code", 200);
            result.put("msg", "重复请求（幂等已处理），未重复扣减");
            result.put("balance", before.intValue());
            return result;
        }
        // 2) 乐观锁扣减
        int rows = aiQuotaAccountMapper.updateConsume(userId, cost, version);
        if (rows == 0) {
            throw new IllegalStateException("并发冲突或余额不足，扣减失败");
        }
        // 3) 写AI调用请求流水
        writeRequest(userId, capability, quotaCost, workId, "consumed", null);
        logOperation("ai_quota", "consume", "user", String.valueOf(userId), null, userId, "AI调用扣减" + quotaCost + "次");
        result.put("code", 200);
        result.put("msg", "AI次数扣减成功");
        result.put("balance", before.subtract(cost).intValue());
        result.put("consumed", quotaCost);
        return result;
    }

    /**
     * AI次数失败补偿（refund）。调用失败时回补，幂等防重复补偿。
     *
     * @param userId         用户ID
     * @param amount         回补次数
     * @param workId         关联作品（可空）
     * @param idempotencyKey 幂等键
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refund(Long userId, Integer amount, Long workId, String idempotencyKey) {
        Map<String, Object> result = new HashMap<>();
        if (userId == null || amount == null || amount <= 0 || !StringUtils.hasText(idempotencyKey)) {
            result.put("code", 400);
            result.put("msg", "参数不完整：userId/amount/idempotencyKey 必填，amount>0");
            return result;
        }
        AiQuotaAccount account = aiQuotaAccountMapper.selectAiQuotaAccountByUserId(userId);
        if (account == null) {
            // 账户不存在按 0 返回（与积分账户语义一致），App 端无需感知
            result.put("code", 200);
            result.put("msg", "ok");
            result.put("availableQuota", 0);
            result.put("totalEarned", 0);
            result.put("totalConsumed", 0);
            result.put("totalRefunded", 0);
            result.put("tokenPerPoint", getTokenPerPoint());
            result.put("records", new ArrayList<>());
            return result;
        }
        BigDecimal refundAmount = BigDecimal.valueOf(amount);
        Long version = account.getVersion() == null ? 0L : account.getVersion();
        BigDecimal before = account.getAvailableQuota();
        AiQuotaRecord record = new AiQuotaRecord();
        record.setRecordNo(genNo("RF"));
        record.setUserId(userId);
        record.setType("refund");
        record.setAmount(refundAmount);
        record.setBeforeBalance(before);
        record.setBalance(before.add(refundAmount));
        record.setBusinessType("failed");
        record.setRelatedId(workId == null ? null : String.valueOf(workId));
        record.setIdempotencyKey(idempotencyKey);
        record.setCreateTime(new Date());
        record.setRemark("AI调用失败补偿");
        try {
            aiQuotaRecordMapper.insertAiQuotaRecord(record);
        } catch (DuplicateKeyException e) {
            result.put("code", 200);
            result.put("msg", "重复请求（幂等已补偿），未重复回补");
            result.put("balance", before.intValue());
            return result;
        }
        int rows = aiQuotaAccountMapper.updateRefund(userId, refundAmount, version);
        if (rows == 0) {
            throw new IllegalStateException("并发冲突，补偿失败");
        }
        writeRequest(userId, "refund", amount, workId, "refunded", null);
        logOperation("ai_quota", "refund", "user", String.valueOf(userId), null, userId, "AI调用失败补偿" + amount + "次");
        result.put("code", 200);
        result.put("msg", "AI次数补偿成功");
        result.put("balance", before.add(refundAmount).intValue());
        result.put("refunded", amount);
        return result;
    }

    /* ==================== 预留锁定业务（reserve → confirm / release） ==================== */

    /**
     * 预留锁定：发起AI任务时，将本次消耗次数从"可用"转入"预留"。
     * 防重复锁定：idempotencyKey 唯一约束；并发安全：version 乐观锁 + 余额校验。
     *
     * @param userId         用户ID
     * @param capability     AI能力 outline/writing/polish
     * @param quotaCost      锁定次数
     * @param workId         关联作品（可空）
     * @param idempotencyKey 幂等键（同一调用重复请求不重复锁定）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reserve(Long userId, String capability, Integer quotaCost, Long workId, String idempotencyKey) {
        Map<String, Object> result = new HashMap<>();
        if (userId == null || quotaCost == null || quotaCost <= 0 || !StringUtils.hasText(capability) || !StringUtils.hasText(idempotencyKey)) {
            result.put("code", 400);
            result.put("msg", "参数不完整：userId/capability/quotaCost/idempotencyKey 必填，quotaCost>0");
            return result;
        }
        AiQuotaAccount account = aiQuotaAccountMapper.selectAiQuotaAccountByUserId(userId);
        if (account == null) {
            // 账户不存在按 0 返回（与积分账户语义一致），App 端无需感知
            result.put("code", 200);
            result.put("msg", "ok");
            result.put("availableQuota", 0);
            result.put("totalEarned", 0);
            result.put("totalConsumed", 0);
            result.put("totalRefunded", 0);
            result.put("records", new ArrayList<>());
            return result;
        }
        BigDecimal cost = BigDecimal.valueOf(quotaCost);
        if (account.getAvailableQuota().compareTo(cost) < 0) {
            result.put("code", 400);
            result.put("msg", "AI次数不足，当前可用: " + account.getAvailableQuota().intValue() + " 次");
            return result;
        }
        Long version = account.getVersion() == null ? 0L : account.getVersion();
        BigDecimal before = account.getAvailableQuota();
        // 1) 先插流水（幂等键唯一，重复请求在此抛异常回滚，不重复锁定）
        AiQuotaRecord record = new AiQuotaRecord();
        record.setRecordNo(genNo("RS"));
        record.setUserId(userId);
        record.setType("reserve");
        record.setAmount(cost);
        record.setBeforeBalance(before);
        record.setBalance(before.subtract(cost));
        record.setBusinessType(capability);
        record.setRelatedId(workId == null ? null : String.valueOf(workId));
        record.setIdempotencyKey(idempotencyKey);
        record.setCreateTime(new Date());
        record.setRemark("AI调用预留锁定: " + capability);
        try {
            aiQuotaRecordMapper.insertAiQuotaRecord(record);
        } catch (DuplicateKeyException e) {
            result.put("code", 200);
            result.put("msg", "重复请求（幂等已锁定），未重复预留");
            result.put("availableQuota", before.intValue());
            return result;
        }
        // 2) 乐观锁锁定（可用→预留）
        int rows = aiQuotaAccountMapper.updateReserve(userId, cost, version);
        if (rows == 0) {
            throw new IllegalStateException("并发冲突或余额不足，预留锁定失败");
        }
        writeRequest(userId, capability, quotaCost, workId, "reserved", null);
        logOperation("ai_quota", "reserve", "user", String.valueOf(userId), null, userId, "AI调用预留锁定" + quotaCost + "次");
        result.put("code", 200);
        result.put("msg", "AI次数预留成功");
        result.put("availableQuota", before.subtract(cost).intValue());
        result.put("reservedQuota", (account.getReservedQuota() == null ? BigDecimal.ZERO : account.getReservedQuota()).add(cost).intValue());
        result.put("reserved", quotaCost);
        return result;
    }

    /**
     * 确认消耗：AI任务成功后，预留转为累计消耗。
     * 防重复确认：idempotencyKey 唯一约束；并发安全：version 乐观锁 + 预留余额校验。
     *
     * @param userId         用户ID
     * @param amount         确认消耗次数
     * @param workId         关联作品（可空）
     * @param idempotencyKey 幂等键
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirm(Long userId, Integer amount, Long workId, String idempotencyKey) {
        Map<String, Object> result = new HashMap<>();
        if (userId == null || amount == null || amount <= 0 || !StringUtils.hasText(idempotencyKey)) {
            result.put("code", 400);
            result.put("msg", "参数不完整：userId/amount/idempotencyKey 必填，amount>0");
            return result;
        }
        AiQuotaAccount account = aiQuotaAccountMapper.selectAiQuotaAccountByUserId(userId);
        if (account == null) {
            // 账户不存在按 0 返回（与积分账户语义一致），App 端无需感知
            result.put("code", 200);
            result.put("msg", "ok");
            result.put("availableQuota", 0);
            result.put("totalEarned", 0);
            result.put("totalConsumed", 0);
            result.put("totalRefunded", 0);
            result.put("records", new ArrayList<>());
            return result;
        }
        BigDecimal cost = BigDecimal.valueOf(amount);
        BigDecimal reserved = account.getReservedQuota() == null ? BigDecimal.ZERO : account.getReservedQuota();
        if (reserved.compareTo(cost) < 0) {
            result.put("code", 400);
            result.put("msg", "预留次数不足，当前预留: " + reserved.intValue() + " 次，请先调用 reserve 锁定");
            return result;
        }
        Long version = account.getVersion() == null ? 0L : account.getVersion();
        BigDecimal before = reserved;
        // 1) 先插流水（幂等键唯一，重复请求不重复确认）
        AiQuotaRecord record = new AiQuotaRecord();
        record.setRecordNo(genNo("CF"));
        record.setUserId(userId);
        record.setType("confirm");
        record.setAmount(cost);
        record.setBeforeBalance(before);
        record.setBalance(before.subtract(cost));
        record.setBusinessType("consume");
        record.setRelatedId(workId == null ? null : String.valueOf(workId));
        record.setIdempotencyKey(idempotencyKey);
        record.setCreateTime(new Date());
        record.setRemark("AI调用确认消耗（预留转消耗）");
        try {
            aiQuotaRecordMapper.insertAiQuotaRecord(record);
        } catch (DuplicateKeyException e) {
            result.put("code", 200);
            result.put("msg", "重复请求（幂等已确认），未重复消耗");
            result.put("reservedQuota", before.intValue());
            return result;
        }
        // 2) 乐观锁确认（预留→累计消耗）
        int rows = aiQuotaAccountMapper.updateConfirm(userId, cost, version);
        if (rows == 0) {
            throw new IllegalStateException("并发冲突或预留不足，确认消耗失败");
        }
        writeRequest(userId, "confirm", amount, workId, "consumed", null);
        logOperation("ai_quota", "confirm", "user", String.valueOf(userId), null, userId, "AI调用确认消耗" + amount + "次");
        result.put("code", 200);
        result.put("msg", "AI次数确认消耗成功");
        result.put("reservedQuota", before.subtract(cost).intValue());
        result.put("totalConsumed", (account.getTotalConsumed() == null ? BigDecimal.ZERO : account.getTotalConsumed()).add(cost).intValue());
        result.put("consumed", amount);
        return result;
    }

    /**
     * 释放预留：AI任务失败/取消时，预留退回可用并计入累计补偿。
     * 防重复释放：idempotencyKey 唯一约束；并发安全：version 乐观锁 + 预留余额校验。
     *
     * @param userId         用户ID
     * @param amount         释放次数
     * @param workId         关联作品（可空）
     * @param idempotencyKey 幂等键
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> release(Long userId, Integer amount, Long workId, String idempotencyKey) {
        Map<String, Object> result = new HashMap<>();
        if (userId == null || amount == null || amount <= 0 || !StringUtils.hasText(idempotencyKey)) {
            result.put("code", 400);
            result.put("msg", "参数不完整：userId/amount/idempotencyKey 必填，amount>0");
            return result;
        }
        AiQuotaAccount account = aiQuotaAccountMapper.selectAiQuotaAccountByUserId(userId);
        if (account == null) {
            result.put("code", 404);
            result.put("msg", "AI次数账户不存在: " + userId);
            return result;
        }
        BigDecimal cost = BigDecimal.valueOf(amount);
        BigDecimal reserved = account.getReservedQuota() == null ? BigDecimal.ZERO : account.getReservedQuota();
        if (reserved.compareTo(cost) < 0) {
            result.put("code", 400);
            result.put("msg", "预留次数不足，当前预留: " + reserved.intValue() + " 次，无法释放");
            return result;
        }
        Long version = account.getVersion() == null ? 0L : account.getVersion();
        BigDecimal before = reserved;
        // 1) 先插流水（幂等键唯一，重复请求不重复释放）
        AiQuotaRecord record = new AiQuotaRecord();
        record.setRecordNo(genNo("RL"));
        record.setUserId(userId);
        record.setType("release");
        record.setAmount(cost);
        record.setBeforeBalance(before);
        record.setBalance(before.subtract(cost));
        record.setBusinessType("failed");
        record.setRelatedId(workId == null ? null : String.valueOf(workId));
        record.setIdempotencyKey(idempotencyKey);
        record.setCreateTime(new Date());
        record.setRemark("AI调用释放预留（失败回补）");
        try {
            aiQuotaRecordMapper.insertAiQuotaRecord(record);
        } catch (DuplicateKeyException e) {
            result.put("code", 200);
            result.put("msg", "重复请求（幂等已释放），未重复回补");
            result.put("reservedQuota", before.intValue());
            return result;
        }
        // 2) 乐观锁释放（预留→可用+累计补偿）
        int rows = aiQuotaAccountMapper.updateRelease(userId, cost, version);
        if (rows == 0) {
            throw new IllegalStateException("并发冲突或预留不足，释放失败");
        }
        writeRequest(userId, "release", amount, workId, "refunded", null);
        logOperation("ai_quota", "release", "user", String.valueOf(userId), null, userId, "AI调用释放预留（失败补偿）" + amount + "次");
        result.put("code", 200);
        result.put("msg", "AI次数释放成功");
        result.put("availableQuota", account.getAvailableQuota().add(cost).intValue());
        result.put("reservedQuota", before.subtract(cost).intValue());
        result.put("refunded", amount);
        return result;
    }

    /**
     * 查询用户AI次数账户与最近流水
     */
    public Map<String, Object> balance(Long userId) {
        Map<String, Object> result = new HashMap<>();
        if (userId == null) {
            result.put("code", 400);
            result.put("msg", "userId必填");
            return result;
        }
        AiQuotaAccount account = aiQuotaAccountMapper.selectAiQuotaAccountByUserId(userId);
        if (account == null) {
            // 账户不存在按 0 返回（与积分账户语义一致），App 端无需感知
            result.put("code", 200);
            result.put("msg", "ok");
            result.put("availableQuota", 0);
            result.put("totalEarned", 0);
            result.put("totalConsumed", 0);
            result.put("totalRefunded", 0);
            result.put("records", new ArrayList<>());
            return result;
        }
        AiQuotaRecord query = new AiQuotaRecord();
        query.setUserId(userId);
        List<AiQuotaRecord> records = aiQuotaRecordMapper.selectAiQuotaRecordList(query);
        result.put("code", 200);
        result.put("msg", "ok");
        result.put("availableQuota", account.getAvailableQuota().intValue());
        result.put("totalEarned", account.getTotalEarned().intValue());
        result.put("totalConsumed", account.getTotalConsumed().intValue());
        result.put("totalRefunded", account.getTotalRefunded().intValue());
        result.put("tokenPerPoint", getTokenPerPoint());
        result.put("records", records.size() > 10 ? records.subList(0, 10) : records);
        return result;
    }

    /**
     * 【去次数化】积分兑换 AI 次数已停用：AI 创作直接消耗积分（按实际 token 换算）。
     * 保留方法签名与幂等键参数，统一返回停用提示，App/PC 端不再提供兑换入口。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> exchange(Long userId, Integer points, String idempotencyKey) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("msg", "AI次数兑换已停用，AI创作按实际token消耗直接扣积分");
        return result;
    }

    private void logOperation(String module, String operation, String targetType, String targetId,
                               String targetName, Long operatorId, String remark) {
        try {
            com.smartscript.platform.review.domain.OperationLog log = new com.smartscript.platform.review.domain.OperationLog();
            log.setModule(module);
            log.setOperation(operation);
            log.setTargetType(targetType);
            log.setTargetId(targetId);
            log.setTargetName(targetName);
            log.setOperatorId(operatorId == null ? 0L : operatorId);
            log.setOperatorName(String.valueOf(operatorId));
            log.setStatus("success");
            log.setRemark(remark);
            operationLogMapper.insertOperationLog(log);
        } catch (Exception e) {
            log.warn("写操作日志失败: {}", e.getMessage());
        }
    }

    private void writeRequest(Long userId, String capability, Integer quotaCost, Long workId, String status, String errorCode) {
        try {
            AiRequest req = new AiRequest();
            req.setRequestNo(genNo("RQ"));
            req.setUserId(userId);
            req.setWorkId(workId);
            req.setCapability(capability);
            req.setQuotaCost(quotaCost);
            req.setStatus(status);
            req.setErrorCode(errorCode);
            req.setStartedAt(new Date());
            req.setFinishedAt(new Date());
            req.setCreatedAt(new Date());
            aiRequestMapper.insertAiRequest(req);
        } catch (Exception e) {
            log.warn("写AI请求流水失败: {}", e.getMessage());
        }
    }

    private String genNo(String prefix) {
        return prefix + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
