package com.smartscript.platform.user.service;

import java.util.Properties;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.exception.AppAuthException;

@Service
public class RegistrationEmailSender
{
    private final String username;
    private final String password;

    public RegistrationEmailSender(@Value("${APP_AUTH_EMAIL_USERNAME:eyhereal@163.com}") String username,
            @Value("${APP_AUTH_EMAIL_PASSWORD:}") String password)
    {
        this.username = username;
        this.password = password;
    }

    public void send(String email, String code, int ttlSeconds)
    {
        if (username.isBlank() || password.isBlank())
            throw new AppAuthException(AppAuthErrorCodes.SYSTEM_ERROR, 503, "邮箱验证码服务暂不可用");
        try
        {
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.163.com");
            props.put("mail.smtp.port", "465");
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.checkserveridentity", "true");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");
            Session session = Session.getInstance(props);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username, "剧云策", "UTF-8"));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(email, true));
            message.setSubject("剧云策验证码", "UTF-8");
            String html = "<div style='background:#f6f4ef;padding:32px;font-family:Arial,sans-serif;color:#1b1a18'>"
                    + "<div style='max-width:520px;margin:auto;background:#fff;padding:32px;border:1px solid #e5e0d6'>"
                    + "<h2 style='color:#2c4a52;margin:0 0 20px'>剧云策</h2><p>您好，您正在进行账号验证。</p>"
                    + "<p>您的验证码是：</p><p style='font-size:30px;letter-spacing:8px;font-weight:bold;color:#2c4a52'>" + code + "</p>"
                    + "<p>验证码在 " + Math.max(1, ttlSeconds / 60) + " 分钟内有效，请勿转发给他人。</p>"
                    + "<p style='font-size:12px;color:#777'>如果这不是您的操作，请忽略本邮件。</p></div></div>";
            message.setContent(html, "text/html; charset=UTF-8");
            message.saveChanges();
            try (Transport transport = session.getTransport("smtp"))
            {
                transport.connect("smtp.163.com", username, password);
                transport.sendMessage(message, message.getAllRecipients());
            }
        }
        catch (Exception e)
        {
            throw new AppAuthException(AppAuthErrorCodes.SYSTEM_ERROR, 503, "邮件发送失败，请稍后重试");
        }
    }
}
