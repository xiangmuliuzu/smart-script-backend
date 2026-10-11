package com.smartscript.platform.content.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.smartscript.platform.content.domain.SysCopyrightApplication;
import com.smartscript.platform.content.service.ICopyrightCenterApiService;

/**
 * 版权中心API调用服务Mock实现
 * 用于测试和开发阶段，模拟真实版权中心行为
 * 
 * @author SmartScript Team
 */
@Service
public class MockCopyrightCenterApiServiceImpl implements ICopyrightCenterApiService
{
    private static final Logger log = LoggerFactory.getLogger(MockCopyrightCenterApiServiceImpl.class);
    private static final Random random = new Random();
    
    // Mock延迟时间配置（毫秒）
    private static final int CONNECTION_TEST_DELAY_BASE = 500;
    private static final int CONNECTION_TEST_DELAY_RANDOM = 500;
    private static final int SUBMIT_DELAY_BASE = 800;
    private static final int SUBMIT_DELAY_RANDOM = 700;
    private static final int QUERY_DELAY_BASE = 300;
    private static final int QUERY_DELAY_RANDOM = 300;
    private static final int DOWNLOAD_DELAY_BASE = 1000;
    private static final int DOWNLOAD_DELAY_RANDOM = 1000;
    
    // Mock状态概率配置
    private static final int CONNECTION_SUCCESS_PROBABILITY = 90;  // 90% 概率连接成功
    private static final int REVIEWING_PROBABILITY = 60;           // 60% 概率返回审核中
    private static final int CERTIFICATED_PROBABILITY = 90;        // 30% 概率返回已颁证 (90-60)
    // 剩余 10% 概率返回已驳回 (100-90)
    
    // Mock数据前缀
    private static final String EXTERNAL_NO_PREFIX = "EXTMOCK";
    private static final String CERT_NO_PREFIX = "CERT";
    private static final String CERT_URL_TEMPLATE = "https://mock-cert.example.com/cert.pdf";

    @Override
    public boolean testConnection(String apiUrl, String apiKey, String apiSecret)
    {
        log.info("Mock: 测试连接 - apiUrl={}", apiUrl);
        
        try {
            Thread.sleep(CONNECTION_TEST_DELAY_BASE + random.nextInt(CONNECTION_TEST_DELAY_RANDOM));
            
            boolean success = random.nextInt(100) < CONNECTION_SUCCESS_PROBABILITY;
            log.info("Mock: 连接测试结果={}", success);
            return success;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public String submitApplication(SysCopyrightApplication application, String apiUrl, String apiKey, String apiSecret)
    {
        log.info("Mock: 提交版权申请 - workId={}, userId={}", application.getWorkId(), application.getUserId());
        
        try {
            Thread.sleep(SUBMIT_DELAY_BASE + random.nextInt(SUBMIT_DELAY_RANDOM));
            
            String externalNo = EXTERNAL_NO_PREFIX + System.currentTimeMillis();
            log.info("Mock: 申请提交成功，外部编号={}", externalNo);
            return externalNo;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("提交失败", e);
        }
    }

    @Override
    public Map<String, Object> queryApplicationStatus(String externalNo, String apiUrl, String apiKey, String apiSecret)
    {
        log.info("Mock: 查询申请状态 - externalNo={}", externalNo);
        
        try {
            Thread.sleep(QUERY_DELAY_BASE + random.nextInt(QUERY_DELAY_RANDOM));
            
            Map<String, Object> result = new HashMap<>();
            int statusRandom = random.nextInt(100);
            
            if (statusRandom < REVIEWING_PROBABILITY) {
                result.put("status", "reviewing");
                log.info("Mock: 状态=reviewing");
            }
            else if (statusRandom < CERTIFICATED_PROBABILITY) {
                result.put("status", "certificated");
                result.put("certNo", CERT_NO_PREFIX + System.currentTimeMillis());
                result.put("certUrl", CERT_URL_TEMPLATE);
                result.put("certTime", new java.util.Date());
                log.info("Mock: 状态=certificated, certNo={}", result.get("certNo"));
            }
            else {
                result.put("status", "rejected");
                result.put("rejectReason", "材料不完整，请补充作品登记证明");
                log.info("Mock: 状态=rejected");
            }
            
            return result;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("查询失败", e);
        }
    }

    @Override
    public byte[] downloadCertificate(String certNo, String apiUrl, String apiKey, String apiSecret)
    {
        log.info("Mock: 下载证书 - certNo={}", certNo);
        
        try {
            Thread.sleep(DOWNLOAD_DELAY_BASE + random.nextInt(DOWNLOAD_DELAY_RANDOM));
            
            String mockPdf = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n" +
                           "Mock Certificate: " + certNo + "\n%%EOF";
            return mockPdf.getBytes();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("下载失败", e);
        }
    }

    @Override
    public boolean verifyCallbackSignature(String data, String signature, String apiSecret)
    {
        log.info("Mock: 验证回调签名");
        return true;
    }
}
