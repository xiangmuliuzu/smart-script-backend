package com.smartscript.platform.content.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.StringUtils;
import com.smartscript.platform.content.domain.SysCopyrightApplication;
import com.smartscript.platform.content.domain.SysCopyrightSeal;
import com.smartscript.platform.notify.UserInboxNotifier;

/**
 * 印章 / 版权状态变更 → App 站内信。
 *
 * <p>只在业务侧确认「状态真正变更」后调用；启停印章、本地「待提交」不发。
 * 文案与落点主键在此集中拼装，投递走跨模块 {@link UserInboxNotifier}。</p>
 */
@Service
public class RightsStatusNotifyService
{
    private static final Logger log = LoggerFactory.getLogger(RightsStatusNotifyService.class);

    public static final String TYPE_SEAL = "SEAL";
    public static final String TYPE_COPYRIGHT = "COPYRIGHT";

    /** 落点：印章详情。 */
    public static final String BIZ_SEAL = "SEAL";
    /** 落点：版权申请详情。 */
    public static final String BIZ_COPYRIGHT_APPLY = "COPYRIGHT_APPLY";
    /** 落点：版权证书详情。 */
    public static final String BIZ_COPYRIGHT_CERT = "COPYRIGHT_CERT";

    @Autowired(required = false)
    private UserInboxNotifier inboxNotifier;

    /** 印章审核通过 / 未通过 → 印章所属用户。 */
    public void notifySealReviewed(SysCopyrightSeal seal, boolean approved)
    {
        if (seal == null || seal.getUserId() == null || seal.getSealId() == null)
        {
            return;
        }
        String name = blankTo(seal.getSealName(), "未命名印章");
        String title;
        StringBuilder body = new StringBuilder();
        if (approved)
        {
            title = "印章审核已通过";
            body.append("您的印章审核已通过。\n");
            line(body, "印章名称", name);
            body.append("可在「我的印章」中查看详情。");
        }
        else
        {
            title = "印章审核未通过";
            body.append("您的印章审核未通过。\n");
            line(body, "印章名称", name);
            line(body, "驳回原因", blankTo(seal.getRejectReason(), "未填写"));
            body.append("请按驳回原因修改后重新提交。");
        }
        String requestId = "SEAL-REV-" + seal.getSealId() + "-" + (approved ? "OK" : "NG")
                + "-" + timeToken(seal.getReviewTime());
        push(requestId, TYPE_SEAL, title, body.toString(), BIZ_SEAL, str(seal.getSealId()),
                List.of(seal.getUserId()));
    }

    /** 版权进入待补正 → 申请人，进申请详情。 */
    public void notifyCopyrightNeedSupplement(SysCopyrightApplication app, String requirement,
            Date deadline, String requestToken)
    {
        if (app == null || app.getUserId() == null || app.getApplyId() == null)
        {
            return;
        }
        StringBuilder body = new StringBuilder("版权中心要求补充材料，请在截止日前完成补正（不重交官费）。\n");
        line(body, "作品名", workTitleOf(app));
        line(body, "补正要求", blankTo(requirement, "请按版权中心要求补充材料"));
        line(body, "截止日期", formatTime(deadline));
        body.append("请前往申请详情提交补正材料。");
        String token = blankTo(requestToken, str(app.getApplyId()));
        push("CRP-SUPP-" + app.getApplyId() + "-" + token, TYPE_COPYRIGHT, "版权申请需补正",
                body.toString(), BIZ_COPYRIGHT_APPLY, str(app.getApplyId()), List.of(app.getUserId()));
    }

    /** 版权第三方驳回 → 申请人，进申请详情。 */
    public void notifyCopyrightRejected(SysCopyrightApplication app, String reason, String requestToken)
    {
        if (app == null || app.getUserId() == null || app.getApplyId() == null)
        {
            return;
        }
        StringBuilder body = new StringBuilder("版权申请未通过第三方审核。\n");
        line(body, "作品名", workTitleOf(app));
        line(body, "驳回原因", blankTo(reason, "不予登记"));
        body.append("可在申请详情查看说明；如需重新登记请按流程再次提交。");
        String token = blankTo(requestToken, "final");
        push("CRP-REJ-" + app.getApplyId() + "-" + token, TYPE_COPYRIGHT, "版权申请未通过",
                body.toString(), BIZ_COPYRIGHT_APPLY, str(app.getApplyId()), List.of(app.getUserId()));
    }

    /** 版权已发证 → 申请人，进证书页（businessId=证书行 id）。 */
    public void notifyCopyrightCertificated(SysCopyrightApplication app, Long certId, String requestToken)
    {
        if (app == null || app.getUserId() == null || app.getApplyId() == null || certId == null)
        {
            return;
        }
        StringBuilder body = new StringBuilder("恭喜，版权证书已颁发。\n");
        line(body, "作品名", workTitleOf(app));
        if (StringUtils.isNotBlank(app.getCertNo()))
        {
            line(body, "证书号", app.getCertNo());
        }
        body.append("可前往证书详情查看或下载。");
        String token = blankTo(requestToken, str(certId));
        push("CRP-CERT-" + app.getApplyId() + "-" + token, TYPE_COPYRIGHT, "版权证书已颁发",
                body.toString(), BIZ_COPYRIGHT_CERT, str(certId), List.of(app.getUserId()));
    }

    /** 补正逾期 → 申请人，进申请详情。 */
    public void notifyCopyrightSupplementOverdue(SysCopyrightApplication app, Long supplementId)
    {
        if (app == null || app.getUserId() == null || app.getApplyId() == null)
        {
            return;
        }
        StringBuilder body = new StringBuilder("版权补正已超过截止日期，申请已进入逾期待驳回。\n");
        line(body, "作品名", workTitleOf(app));
        body.append("请前往申请详情查看后续处理说明。");
        String token = supplementId == null ? "x" : str(supplementId);
        push("CRP-OVD-" + app.getApplyId() + "-" + token, TYPE_COPYRIGHT, "版权补正已超时",
                body.toString(), BIZ_COPYRIGHT_APPLY, str(app.getApplyId()), List.of(app.getUserId()));
    }

    private void push(String requestId, String type, String title, String content,
            String businessType, String businessId, List<Long> userIds)
    {
        if (inboxNotifier == null)
        {
            log.debug("UserInboxNotifier 未装配，跳过通知 requestId={}", requestId);
            return;
        }
        try
        {
            inboxNotifier.notify(requestId, type, title, content, businessType, businessId, userIds);
        }
        catch (Exception e)
        {
            log.warn("权益状态通知失败 requestId={}: {}", requestId, e.getMessage());
        }
    }

    private static void line(StringBuilder sb, String label, String value)
    {
        if (StringUtils.isBlank(value))
        {
            return;
        }
        sb.append(label).append("：").append(value.trim()).append('\n');
    }

    private static String workTitleOf(SysCopyrightApplication app)
    {
        if (StringUtils.isNotBlank(app.getWorkTitle()))
        {
            return app.getWorkTitle().trim();
        }
        if (StringUtils.isNotBlank(app.getWorkName()))
        {
            return app.getWorkName().trim();
        }
        return app.getWorkId() == null ? "未命名作品" : ("作品#" + app.getWorkId());
    }

    private static String blankTo(String value, String fallback)
    {
        return StringUtils.isBlank(value) ? fallback : value.trim();
    }

    private static String str(Long id)
    {
        return id == null ? "" : String.valueOf(id);
    }

    private static String timeToken(Date time)
    {
        return time == null ? String.valueOf(System.currentTimeMillis()) : String.valueOf(time.getTime());
    }

    private static String formatTime(Date time)
    {
        if (time == null)
        {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(time);
    }
}
