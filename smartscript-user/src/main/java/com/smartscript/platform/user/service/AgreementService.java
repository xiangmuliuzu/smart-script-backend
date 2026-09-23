package com.smartscript.platform.user.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.smartscript.platform.user.config.AppAuthProperties;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.domain.AppUserConsent;
import com.smartscript.platform.user.dto.AgreementAcceptanceDto;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserConsentMapper;

@Service
public class AgreementService
{
    public static final String USER_AGREEMENT = "USER_AGREEMENT";
    public static final String PRIVACY_POLICY = "PRIVACY_POLICY";

    private final AppAuthProperties properties;
    private final AppUserConsentMapper consentMapper;

    public AgreementService(AppAuthProperties properties, AppUserConsentMapper consentMapper)
    {
        this.properties = properties;
        this.consentMapper = consentMapper;
    }

    public List<Map<String, Object>> currentAgreements()
    {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(agreement(USER_AGREEMENT, properties.getAgreement().getUserVersion(),
                properties.getAgreement().getUserTitle(), properties.getAgreement().getUserUrl()));
        list.add(agreement(PRIVACY_POLICY, properties.getAgreement().getPrivacyVersion(),
                properties.getAgreement().getPrivacyTitle(), properties.getAgreement().getPrivacyUrl()));
        return list;
    }

    private Map<String, Object> agreement(String type, String version, String title, String url)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("version", version);
        m.put("title", title);
        m.put("url", url);
        m.put("effectiveAt", "2026-09-21T00:00:00Z");
        return m;
    }

    public void validateCurrent(List<AgreementAcceptanceDto> acceptances)
    {
        String userV = properties.getAgreement().getUserVersion();
        String privacyV = properties.getAgreement().getPrivacyVersion();
        boolean userOk = false;
        boolean privacyOk = false;
        if (acceptances != null)
        {
            for (AgreementAcceptanceDto a : acceptances)
            {
                if (a == null || a.getType() == null || a.getVersion() == null)
                {
                    continue;
                }
                if (USER_AGREEMENT.equals(a.getType()) && userV.equals(a.getVersion()))
                {
                    userOk = true;
                }
                if (PRIVACY_POLICY.equals(a.getType()) && privacyV.equals(a.getVersion()))
                {
                    privacyOk = true;
                }
            }
        }
        if (!userOk || !privacyOk)
        {
            throw new AppAuthException(AppAuthErrorCodes.AGREEMENT_MISMATCH, 409, "agreement version mismatch");
        }
    }

    public void recordAcceptances(Long userId, List<AgreementAcceptanceDto> acceptances, String ip, String deviceId)
    {
        validateCurrent(acceptances);
        Date now = new Date();
        for (AgreementAcceptanceDto a : acceptances)
        {
            AppUserConsent c = new AppUserConsent();
            c.setUserId(userId);
            c.setAgreementType(a.getType());
            c.setAgreementVersion(a.getVersion());
            c.setAcceptedAt(now);
            c.setIp(ip);
            c.setDeviceId(deviceId);
            consentMapper.insertConsent(c);
        }
    }

    public boolean hasCurrentConsents(Long userId)
    {
        String userV = properties.getAgreement().getUserVersion();
        String privacyV = properties.getAgreement().getPrivacyVersion();
        return consentMapper.countAcceptance(userId, USER_AGREEMENT, userV) > 0
                && consentMapper.countAcceptance(userId, PRIVACY_POLICY, privacyV) > 0;
    }
}
