package com.ruoyi.framework.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * /profile 私有材料的鉴权链接签发器。
 *
 * <p>合同原件、分页图、签章图和最终签署 PDF 里是双方真实姓名与成交金额，
 * 而 {@code /profile/**} 在安全配置里是匿名可访问的：只要地址被猜到或转发出去，材料就永久公开。
 * 目录结构收敛到 {@code upload/contract/...} 并随机命名解决了「猜」，这个类解决「转发后长期可读」——
 * 下发给前端的地址带过期时间与签名，过期即失效。</p>
 *
 * <p>地址只在出口（接口响应）拼装时签名，库里存的仍是干净相对路径；
 * 服务端自己读文件走 {@link ProfileStorageService#materializeUrl}，与查询串无关，
 * 所以签名不影响 PDF 渲染、盖章与回源逻辑。</p>
 */
@Component
public class ProfileAccessSigner
{
    private static final Logger log = LoggerFactory.getLogger(ProfileAccessSigner.class);

    /** 过期时间参数名。 */
    public static final String PARAM_EXPIRE = "exp";

    /** 签名参数名。 */
    public static final String PARAM_SIGNATURE = "sig";

    /**
     * 链接有效期（秒）。
     *
     * <p>取 6 小时：够用户打开合同页、翻页选签章位置、再点下载，不至于用着用着图裂；
     * 同时把「链接被转发/落到浏览器历史里」的暴露窗口从永久压到半天。</p>
     */
    private static final long TTL_SECONDS = 6 * 3600L;

    /** 需要鉴权的对象前缀：新版分层目录与旧版平铺目录都要盖住，否则历史合同仍可匿名读。 */
    private static final String[] GUARDED_PREFIXES = { "contract/", "upload/contract/" };

    private static final String PROFILE_PREFIX = "/profile/";

    private static final java.util.regex.Pattern HTML_IMAGE_SRC = java.util.regex.Pattern
            .compile("src=\"" + PROFILE_PREFIX + "[^\"]+\"");

    private static final Base64.Encoder SIG_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final byte[] secret;

    public ProfileAccessSigner(@Value("${token.secret:}") String secret)
    {
        // TokenSecretRequiredConfig 已保证运行期密钥非空且够强；这里再兜一次，密钥缺失绝不能退化成匿名可读
        String value = secret == null ? "" : secret.trim();
        if (value.isEmpty())
        {
            throw new IllegalStateException("token.secret is required to sign private profile links");
        }
        this.secret = value.getBytes(StandardCharsets.UTF_8);
    }

    /** 该对象是否属于需要鉴权的私有材料。 */
    public static boolean isGuarded(String key)
    {
        if (key == null)
        {
            return false;
        }
        for (String prefix : GUARDED_PREFIXES)
        {
            if (key.startsWith(prefix))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 给相对地址追加有效期与签名；非合同材料、外部地址与已签名地址原样返回。
     */
    public String sign(String url)
    {
        if (url == null)
        {
            return url;
        }
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0)
        {
            // 已带查询串说明已经签过（或不该由这里管），重复签会互相覆盖
            return path;
        }
        if (!path.startsWith(PROFILE_PREFIX))
        {
            return path;
        }
        String key = path.substring(PROFILE_PREFIX.length());
        if (!isGuarded(key))
        {
            return path;
        }
        long expire = Instant.now().getEpochSecond() + TTL_SECONDS;
        String signature = mac(key, expire);
        return signature == null ? path
                : path + "?" + PARAM_EXPIRE + "=" + expire + "&" + PARAM_SIGNATURE + "=" + signature;
    }

    /**
     * 给 HTML 正文里内联的 {@code src="/profile/..."} 图片地址批量签名。
     *
     * <p>合同正文（contract_content）里嵌了签章图片地址，详情接口原样透出时同样要签，
     * 否则前端拿到的是匿名地址。</p>
     */
    public String signHtml(String html)
    {
        if (html == null || html.isEmpty())
        {
            return html;
        }
        java.util.regex.Matcher matcher = HTML_IMAGE_SRC.matcher(html);
        StringBuilder out = new StringBuilder(html.length());
        int cursor = 0;
        while (matcher.find())
        {
            String attribute = matcher.group();
            String url = attribute.substring("src=\"".length(), attribute.length() - 1);
            String signed = sign(url);
            if (signed.equals(url))
            {
                continue;
            }
            out.append(html, cursor, matcher.start()).append("src=\"").append(signed).append('"');
            cursor = matcher.end();
        }
        return cursor == 0 ? html : out.append(html, cursor, html.length()).toString();
    }

    /**
     * 校验访问凭证：签名对得上且没过期。
     *
     * @param key     对象 key（不含 /profile 前缀与查询串）
     * @param expire  {@code exp} 参数原文
     * @param signature {@code sig} 参数原文
     */
    public boolean verified(String key, String expire, String signature)
    {
        if (expire == null || signature == null || signature.isEmpty())
        {
            return false;
        }
        long until;
        try
        {
            until = Long.parseLong(expire.trim());
        }
        catch (NumberFormatException e)
        {
            return false;
        }
        if (until < Instant.now().getEpochSecond())
        {
            return false;
        }
        String expected = mac(key, until);
        // 定长 base64url，直接比字节即可，避免把签名长度当信息泄露
        return expected != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.trim().getBytes(StandardCharsets.UTF_8));
    }

    private String mac(String key, long expire)
    {
        try
        {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            // 只签 key + 过期时间：换文件名或延长时间都得重新签
            byte[] raw = mac.doFinal((key + "\n" + expire).getBytes(StandardCharsets.UTF_8));
            return SIG_ENCODER.encodeToString(raw);
        }
        catch (Exception e)
        {
            log.warn("合同材料鉴权签名计算失败: {}", e.getMessage());
            return null;
        }
    }
}
