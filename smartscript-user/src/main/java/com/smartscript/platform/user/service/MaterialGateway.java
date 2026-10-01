package com.smartscript.platform.user.service;

import java.io.IOException;
import java.io.InputStream;
import com.smartscript.platform.user.exception.AppAdminException;

/**
 * A4 材料网关：把数据库中的材料引用解析为**真实可读的字节流**。
 *
 * 存在的意义：契约要求材料只能通过短时授权访问，而不是给客户端一个永久地址。
 * 因此「引用 → 内容」这一步必须由服务端完成，客户端永远拿不到存储路径。
 *
 * 存储位置约束（安全）：
 *   - 材料**不得**放在 `RuoYiConfig.getProfile()` 之下——该目录由
 *     `ResourcesConfig` 直接映射为公开静态资源（/profile/**），放进去等于公开。
 *   - 本实现使用独立根目录 `app.admin.material-storage-path`，
 *     并拒绝任何越出该根目录的引用（路径穿越防护）。
 *
 * 后续接入：A5 若改为对象存储，只需替换本接口实现，
 * 授权与流式端点、页面与用例均无需改动。
 */
public interface MaterialGateway
{
    /**
     * 判断引用在存储中是否真实存在。
     *
     * @param rawRef 数据库中的材料引用
     */
    boolean exists(String rawRef);

    /**
     * 打开引用的字节流。
     *
     * @param rawRef 数据库中的材料引用
     * @return 输入流，由调用方负责关闭
     * @throws AppAdminException 引用非法、越界或不存在时抛出（对外按不存在处理）
     */
    InputStream open(String rawRef) throws IOException;

    /** 内容长度（字节）；未知时返回 -1。 */
    long size(String rawRef);

    /** 内容类型（如 image/png、application/pdf）；未知时返回 application/octet-stream。 */
    String contentType(String rawRef);
}
