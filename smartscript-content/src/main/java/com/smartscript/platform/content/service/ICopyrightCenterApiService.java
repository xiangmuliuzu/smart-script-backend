package com.smartscript.platform.content.service;

import com.smartscript.platform.content.domain.SysCopyrightApplication;

/**
 * 版权中心API调用服务接口
 * 
 * @author SmartScript Team
 */
public interface ICopyrightCenterApiService
{
    /**
     * 测试连接
     * 
     * @param apiUrl API地址
     * @param apiKey API密钥
     * @param apiSecret API秘密
     * @return 是否连接成功
     */
    boolean testConnection(String apiUrl, String apiKey, String apiSecret);

    /**
     * 提交版权申请
     * 
     * @param application 版权申请对象
     * @param apiUrl API地址
     * @param apiKey API密钥
     * @param apiSecret API秘密
     * @return 外部申请编号
     */
    String submitApplication(SysCopyrightApplication application, String apiUrl, String apiKey, String apiSecret);

    /**
     * 查询申请状态
     * 
     * @param externalNo 外部申请编号
     * @param apiUrl API地址
     * @param apiKey API密钥
     * @param apiSecret API秘密
     * @return 状态信息（包含status、certNo、certUrl等）
     */
    java.util.Map<String, Object> queryApplicationStatus(String externalNo, String apiUrl, String apiKey, String apiSecret);

    /**
     * 下载证书
     * 
     * @param certNo 证书编号
     * @param apiUrl API地址
     * @param apiKey API密钥
     * @param apiSecret API秘密
     * @return 证书文件字节数组
     */
    byte[] downloadCertificate(String certNo, String apiUrl, String apiKey, String apiSecret);

    /**
     * 验证回调签名
     * 
     * @param data 回调数据
     * @param signature 签名
     * @param apiSecret API秘密
     * @return 签名是否有效
     */
    boolean verifyCallbackSignature(String data, String signature, String apiSecret);
}
