package com.smartscript.platform.review.controller;

import com.ruoyi.common.annotation.Anonymous;
import com.smartscript.platform.review.domain.*;
import com.smartscript.platform.review.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * App 用户侧福利/AI 查询接口（供 App 端福利中心、AI 机会页展示真实数据）。
 *
 * 与管理端（/api/v1/admin/**）隔离：本类全部 @Anonymous，App 用户 token 即可调用；
 * 数据只按 userId 过滤，天然实现用户隔离。
 *
 * 接口清单：
 *   GET /api/v1/welfare/tasks?userId=          → 任务列表 + 我的完成/领取进度
 *   GET /api/v1/welfare/points/balance?userId= → 积分余额
 *   GET /api/v1/welfare/points/records?userId= → 积分明细
 *   GET /api/v1/ad/list?userId=                → 广告任务列表 + 今日已看次数
 *   GET /api/v1/ai/quota/exchange-records?userId= → 积分兑换 AI 次数记录
 *
 * @author smartscript
 */
@Anonymous
@RestController
@RequestMapping("/api/v1/welfare")
public class AppWelfareController {

    @Autowired
    private TaskConfigMapper taskConfigMapper;

    @Autowired
    private UserTaskMapper userTaskMapper;

    @Autowired
    private PointsAccountMapper pointsAccountMapper;

    @Autowired
    private PointsRecordMapper pointsRecordMapper;

    @Autowired
    private AdConfigMapper adConfigMapper;

    @Autowired
    private AiQuotaRecordMapper aiQuotaRecordMapper;

    /** 统一信封：{code, msg, data} */
    private Map<String, Object> ok(Map<String, Object> data) {
        Map<String, Object> env = new HashMap<>();
        env.put("code", 200);
        env.put("msg", "success");
        env.put("data", data);
        return env;
    }

    private Map<String, Object> fail(int code, String msg) {
        Map<String, Object> env = new HashMap<>();
        env.put("code", code);
        env.put("msg", msg);
        env.put("data", new HashMap<>());
        return env;
    }

    private String fmt(Date d) {
        if (d == null) return null;
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /**
     * 任务列表 + 我的进度（只返回启用任务）
     */
    @GetMapping("/tasks")
    public Map<String, Object> tasks(@RequestParam("userId") Long userId) {
        if (userId == null) return fail(400, "userId 必填");
        List<TaskConfig> all = taskConfigMapper.selectTaskConfigList(new TaskConfig());
        List<Map<String, Object>> list = new ArrayList<>();
        for (TaskConfig t : all) {
            if (t.getStatus() == null || t.getStatus() != 1) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("taskId", t.getTaskId());
            m.put("taskName", t.getTaskName());
            m.put("taskType", t.getTaskType());
            m.put("taskCode", t.getTaskCode());
            m.put("description", t.getDescription());
            m.put("targetCount", t.getTargetCount());
            m.put("rewardPoints", t.getRewardPoints());
            m.put("icon", t.getIcon());
            UserTask ut = userTaskMapper.selectByUserAndTask(userId, t.getTaskId());
            m.put("progress", ut == null || ut.getProgress() == null ? 0 : ut.getProgress());
            m.put("isCompleted", ut == null || ut.getIsCompleted() == null ? 0 : ut.getIsCompleted());
            m.put("isClaimed", ut == null || ut.getIsClaimed() == null ? 0 : ut.getIsClaimed());
            m.put("periodDate", ut == null || ut.getPeriodDate() == null ? null
                    : new SimpleDateFormat("yyyy-MM-dd").format(ut.getPeriodDate()));
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("tasks", list);
        return ok(data);
    }

    /**
     * 积分余额（账户不存在返回 0，不报错）
     */
    @GetMapping("/points/balance")
    public Map<String, Object> pointsBalance(@RequestParam("userId") Long userId) {
        if (userId == null) return fail(400, "userId 必填");
        PointsAccount acc = pointsAccountMapper.selectByUserId(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("balance", acc == null || acc.getBalance() == null ? 0 : acc.getBalance());
        data.put("totalEarned", acc == null || acc.getTotalEarned() == null ? 0 : acc.getTotalEarned());
        data.put("totalSpent", acc == null || acc.getTotalSpent() == null ? 0 : acc.getTotalSpent());
        data.put("todayEarned", acc == null || acc.getTodayEarned() == null ? 0 : acc.getTodayEarned());
        return ok(data);
    }

    /**
     * 积分明细（按时间倒序）
     */
    @GetMapping("/points/records")
    public Map<String, Object> pointsRecords(@RequestParam("userId") Long userId) {
        if (userId == null) return fail(400, "userId 必填");
        PointsRecord q = new PointsRecord();
        q.setUserId(userId);
        List<PointsRecord> records = pointsRecordMapper.selectPointsRecordList(q);
        List<Map<String, Object>> list = new ArrayList<>();
        for (PointsRecord r : records) {
            Map<String, Object> m = new HashMap<>();
            m.put("recordId", r.getRecordId());
            m.put("changeType", r.getChangeType());
            m.put("points", r.getPoints());
            m.put("balanceAfter", r.getBalanceAfter());
            m.put("source", r.getSource());
            m.put("remark", r.getRemark());
            m.put("createTime", fmt(r.getCreatedAt() == null ? r.getCreateTime() : r.getCreatedAt()));
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("records", list);
        return ok(data);
    }

    /**
     * 广告任务列表 + 今日已看次数（防刷计数由广告奖励发放时写入积分流水）
     */
    @GetMapping("/ad/list")
    public Map<String, Object> adList(@RequestParam("userId") Long userId) {
        if (userId == null) return fail(400, "userId 必填");
        // 只返回广告福利任务（category=WELFARE），广告位（开屏/插屏）不展示在福利页
        AdConfig q = new AdConfig();
        q.setCategory("WELFARE");
        List<AdConfig> all = adConfigMapper.selectAdConfigList(q);
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        List<Map<String, Object>> list = new ArrayList<>();
        for (AdConfig ad : all) {
            if (ad.getIsEnabled() == null || ad.getIsEnabled() != 1) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("adId", ad.getAdId());
            m.put("adName", ad.getAdName());
            m.put("adType", ad.getAdType());
            m.put("adPosition", ad.getAdPosition());
            m.put("rewardPoints", ad.getRewardPoints());
            m.put("watchLimit", ad.getWatchLimit());
            m.put("dailyCap", ad.getDailyCap());
            m.put("frequencyLimit", ad.getFrequencyLimit());
            m.put("watchedToday", pointsRecordMapper.countAdDaily(userId, today));
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("ads", list);
        return ok(data);
    }

    /**
     * 积分兑换 AI 次数记录（businessType=exchange，按时间倒序）
     */
    @GetMapping("/ai/quota/exchange-records")
    public Map<String, Object> exchangeRecords(@RequestParam("userId") Long userId) {
        if (userId == null) return fail(400, "userId 必填");
        AiQuotaRecord q = new AiQuotaRecord();
        q.setUserId(userId);
        q.setBusinessType("exchange");
        List<AiQuotaRecord> records = aiQuotaRecordMapper.selectAiQuotaRecordList(q);
        List<Map<String, Object>> list = new ArrayList<>();
        for (AiQuotaRecord r : records) {
            Map<String, Object> m = new HashMap<>();
            m.put("recordId", r.getRecordId());
            m.put("type", r.getType());
            m.put("businessType", r.getBusinessType());
            m.put("amount", r.getAmount());
            m.put("balanceAfter", r.getBalance());
            m.put("remark", r.getRemark());
            m.put("createTime", fmt(r.getCreateTime()));
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("records", list);
        return ok(data);
    }
}
