package com.smartscript.platform.user.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.dto.AuthSessionDto;
import com.smartscript.platform.user.dto.CurrentUserDto;
import com.smartscript.platform.user.dto.QrLoginTicket;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.security.AppIdentityContext;

/**
 * 扫码登录票据：PC 出码 → App 扫码 → App 确认 → PC 轮询取令牌。
 *
 * <p>安全口径：</p>
 * <ul>
 *   <li>二维码里只有一次性票据明文；Redis 键里只出现其 SHA-256，键被读走也换不出可用票据。</li>
 *   <li>令牌不进 Redis：确认只记「谁确认了」，PC 轮询命中 CONFIRMED 时原子读删票据后才签发令牌。</li>
 *   <li>状态单向推进（WAITING → SCANNED → CONFIRMED），已在 Redis 里原子完成，
 *       重复扫码、他人确认、过期后确认都被拒绝，而不是先断言再读改写。</li>
 *   <li>票据一次性：任何一次轮询取到 CONFIRMED 都会删除票据，第二次轮询只会看到失效。</li>
 * </ul>
 */
@Service
public class QrLoginService
{
    private final QrLoginTickets tickets;
    private final AppAuthProperties properties;
    private final AppAuthenticationService authenticationService;

    public QrLoginService(QrLoginTickets tickets, AppAuthProperties properties,
            AppAuthenticationService authenticationService)
    {
        this.tickets = tickets;
        this.properties = properties;
        this.authenticationService = authenticationService;
    }

    /** PC 登录页出码。 */
    public Map<String, Object> create(String portal, String browser, String os)
    {
        String normalizedPortal = QrLoginTicket.PORTAL_ENTERPRISE.equals(portal)
                ? QrLoginTicket.PORTAL_ENTERPRISE : QrLoginTicket.PORTAL_CREATOR;
        QrLoginTicket ticket = new QrLoginTicket();
        ticket.setStatus(QrLoginTicket.STATUS_WAITING);
        ticket.setPortal(normalizedPortal);
        ticket.setBrowser(trimToMax(browser, 60));
        ticket.setOs(trimToMax(os, 40));

        int ttl = ticketTtl();
        // 32 字节随机足以抵御猜测，且不依赖若依 IdUtils 的实现细节。
        String qrToken = com.smartscript.platform.user.util.AppHashes.randomToken(32);
        tickets.save(qrToken, ticket, ttl);

        Map<String, Object> result = ticketView(ticket);
        result.put("qrToken", qrToken);
        result.put("expiresIn", ttl);
        result.put("pollInterval", properties.getQrLogin().getPollIntervalSeconds());
        return result;
    }

    /** App 扫码：把票据推进到 SCANNED，并记下是谁扫的。 */
    public Map<String, Object> scan(AppIdentityContext identity, String qrToken)
    {
        QrLoginTicket ticket = requireTicket(qrToken);
        if (!QrLoginTicket.STATUS_WAITING.equals(ticket.getStatus()))
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "二维码已被扫描或已失效，请刷新后重试");
        }

        QrLoginTicket scanned = new QrLoginTicket();
        scanned.setStatus(QrLoginTicket.STATUS_SCANNED);
        scanned.setPortal(ticket.getPortal());
        scanned.setBrowser(ticket.getBrowser());
        scanned.setOs(ticket.getOs());
        scanned.setScannedUserId(identity.getUserId());
        scanned.setScannedNickname(identity.getNickName());
        scanned.setScannedPhoneMasked(identity.getPhoneMasked());
        // 只推进状态，剩余有效期沿用原票（扫码不续命）。
        if (!tickets.savePreservingTtl(qrToken, scanned))
        {
            throw expired();
        }
        return ticketView(scanned);
    }

    /** App 确认登录。准入校验按登录端分别执行，失败文案与网页登录保持一致。 */
    public Map<String, Object> confirm(AppIdentityContext identity, String qrToken)
    {
        QrLoginTicket ticket = requireTicket(qrToken);
        if (!QrLoginTicket.STATUS_SCANNED.equals(ticket.getStatus()))
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400,
                    QrLoginTicket.STATUS_WAITING.equals(ticket.getStatus())
                            ? "二维码已刷新，请重新扫描"
                            : "二维码已失效，请刷新后重试");
        }
        if (!identity.getUserId().equals(ticket.getScannedUserId()))
        {
            // 票据只能由扫它的那个账号确认，换号确认一律拒绝。
            throw new AppAuthException(AppAuthErrorCodes.DOMAIN_OR_PERMISSION, 403, "请使用扫码的账号确认登录");
        }

        CurrentUserDto user = authenticationService.me(identity);
        checkPortalAdmission(ticket.getPortal(), user);

        QrLoginTicket confirmed = new QrLoginTicket();
        confirmed.setStatus(QrLoginTicket.STATUS_CONFIRMED);
        confirmed.setPortal(ticket.getPortal());
        confirmed.setBrowser(ticket.getBrowser());
        confirmed.setOs(ticket.getOs());
        confirmed.setScannedUserId(user.getUserId());
        confirmed.setScannedNickname(ticket.getScannedNickname());
        confirmed.setScannedPhoneMasked(ticket.getScannedPhoneMasked());
        confirmed.setConfirmedUserId(user.getUserId());
        // 确认后换一段短窗口：PC 必须尽快取走，超出即作废。
        tickets.save(qrToken, confirmed, consumeTtl());
        return ticketView(confirmed);
    }

    /** App 取消：把自己扫过、尚未确认的票据作废。 */
    public Map<String, Object> cancel(AppIdentityContext identity, String qrToken)
    {
        QrLoginTicket ticket = requireTicket(qrToken);
        if (!QrLoginTicket.STATUS_SCANNED.equals(ticket.getStatus())
                || !identity.getUserId().equals(ticket.getScannedUserId()))
        {
            // 已确认或并非本人扫的票：不改变状态，只回报当前状态。
            return ticketView(ticket);
        }
        tickets.delete(qrToken);
        QrLoginTicket cancelled = new QrLoginTicket();
        cancelled.setStatus(QrLoginTicket.STATUS_CANCELLED);
        return ticketView(cancelled);
    }

    /**
     * PC 轮询。
     *
     * <p>WAITING / SCANNED 只回状态；命中 CONFIRMED 时先原子取走票据，再签发令牌，
     * 保证一张票最多换出一份登录态。取票账号若已不可用，按停用口径拒绝。</p>
     */
    public Map<String, Object> poll(String qrToken, String ip)
    {
        QrLoginTicket ticket = requireTicket(qrToken);
        if (!QrLoginTicket.STATUS_CONFIRMED.equals(ticket.getStatus()))
        {
            if (!tickets.acquirePollSlot(qrToken))
            {
                throw new AppAuthException(AppAuthErrorCodes.PARAM, 429, "操作过于频繁，请稍后再试");
            }
            return ticketView(ticket);
        }

        QrLoginTicket consumed = tickets.take(qrToken);
        if (consumed == null || consumed.getConfirmedUserId() == null)
        {
            // 并发轮询中另一方已取走：按失效处理，绝不再签一份。
            throw expired();
        }
        AuthSessionDto session = authenticationService.issueQrLoginSession(consumed.getConfirmedUserId(), ip);

        Map<String, Object> result = ticketView(consumed);
        result.put("token", session.getToken());
        result.put("user", session.getUser());
        return result;
    }

    /**
     * PC 侧拒绝：把票据作废。
     *
     * 扫码登录的已知风险是「陌生人抢扫」——对方扫了你的码并确认，你的浏览器就会登进他的账号。
     * 票据令牌只在发起出码的那个浏览器手里，因此以「持有令牌」为凭据即可作废，
     * 无需身份。扫码后网页会显示对方昵称，用户据此判断是不是自己，一点即可拒绝。
     */
    public Map<String, Object> abort(String qrToken)
    {
        QrLoginTicket ticket = requireTicket(qrToken);
        tickets.delete(qrToken);
        QrLoginTicket aborted = new QrLoginTicket();
        aborted.setStatus(QrLoginTicket.STATUS_CANCELLED);
        aborted.setPortal(ticket.getPortal());
        return ticketView(aborted);
    }

    private Map<String, Object> ticketView(QrLoginTicket ticket)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", ticket.getStatus());
        result.put("portal", ticket.getPortal());
        result.put("browser", ticket.getBrowser());
        result.put("os", ticket.getOs());
        result.put("nickname", ticket.getScannedNickname());
        result.put("phoneMasked", ticket.getScannedPhoneMasked());
        return result;
    }

    private QrLoginTicket requireTicket(String qrToken)
    {
        if (qrToken == null || qrToken.isBlank() || qrToken.length() > 128)
        {
            throw new AppAuthException(AppAuthErrorCodes.PARAM, 400, "二维码参数不正确，请刷新后重试");
        }
        QrLoginTicket ticket = tickets.get(qrToken);
        if (ticket == null)
        {
            throw expired();
        }
        return ticket;
    }

    private static AppAuthException expired()
    {
        return new AppAuthException(AppAuthErrorCodes.PARAM, 400, "二维码已失效，请在浏览器里刷新后重试");
    }

    private int ticketTtl()
    {
        int ttl = properties.getQrLogin().getTicketTtlSeconds();
        return ttl > 0 ? ttl : 120;
    }

    private int consumeTtl()
    {
        int ttl = properties.getQrLogin().getConsumeTtlSeconds();
        return ttl > 0 ? ttl : 60;
    }

    private static String trimToMax(String value, int max)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /** 登录端准入：与 web 端 assertUser 同一套口径（账号域 + 作者能力）。 */
    private static void checkPortalAdmission(String portal, CurrentUserDto user)
    {
        if (user == null || user.getUserId() == null)
        {
            throw new AppAuthException(AppAuthErrorCodes.ACCOUNT_DISABLED, 403, "账号暂时无法使用，请联系客服或管理员");
        }
        String type = user.getUserType();
        if (!"01".equals(type) && !"02".equals(type) && !"03".equals(type))
        {
            throw new AppAuthException(AppAuthErrorCodes.DOMAIN_OR_PERMISSION, 403,
                    "请使用普通用户账号登录，管理员请前往运维管理端");
        }
        if (QrLoginTicket.PORTAL_CREATOR.equals(portal) && !user.isAuthorCapability())
        {
            throw new AppAuthException(AppAuthErrorCodes.DOMAIN_OR_PERMISSION, 403,
                    "账号未开通创作者能力，暂时无法登录创作者端");
        }
    }
}
