package com.smartscript.platform.user.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.constant.AppUserErrorCodes;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.mapper.AppUserCenterMapper;

/** 用户实名材料写入审核端已有的私有目录，引用只允许原上传用户提交。 */
@Service
public class AppRealNameMaterialService
{
    private final AppUserCenterMapper centerMapper;
    private final LocalFileMaterialGateway gateway;

    public AppRealNameMaterialService(AppUserCenterMapper centerMapper, LocalFileMaterialGateway gateway)
    {
        this.centerMapper = centerMapper;
        this.gateway = gateway;
    }

    public Map<String, Object> upload(Long userId, MultipartFile file)
    {
        AppRealNameService.requireSubmittable(centerMapper.selectLatestRealNameStatus(userId));
        if (file == null || file.isEmpty())
            throw invalid("请选择证件照片");
        if (file.getSize() > AppFileUploadService.MAX_AVATAR_BYTES)
            throw new AppAuthException(AppUserErrorCodes.PAYLOAD_TOO_LARGE, 413,
                    AppUserErrorCodes.PAYLOAD_TOO_LARGE_TEXT);

        Path target = null;
        boolean created = false;
        try
        {
            byte[] content = file.getBytes();
            if (content.length == 0) throw invalid("请选择证件照片");
            if (content.length > AppFileUploadService.MAX_AVATAR_BYTES)
                throw new AppAuthException(AppUserErrorCodes.PAYLOAD_TOO_LARGE, 413,
                        AppUserErrorCodes.PAYLOAD_TOO_LARGE_TEXT);
            String format = AppFileUploadService.validateImageContent(content);
            Path directory = gateway.root().resolve("realname").resolve(userId.toString());
            Files.createDirectories(directory);
            if (!directory.toRealPath().startsWith(gateway.root().toRealPath()))
                throw new IOException("Invalid material directory");
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + format;
            target = directory.resolve(filename);
            Files.createFile(target);
            created = true;
            Files.write(target, content, StandardOpenOption.WRITE);
            return Map.of("materialRef", "realname/" + userId + "/" + filename);
        }
        catch (IOException e)
        {
            if (created && target != null)
            {
                try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            }
            throw new AppAuthException(AppAuthErrorCodes.SYSTEM_ERROR, 500, "材料上传失败，请稍后重试");
        }
    }

    public void requireOwnedMaterial(Long userId, String reference)
    {
        if (reference == null || !reference.matches("realname/" + userId + "/[a-f0-9]{32}\\.(jpg|png|gif|bmp)"))
            throw invalid("请上传本人证件照片后提交");
        try
        {
            Path file = gateway.root().resolve(reference);
            if (!Files.isRegularFile(file) || !file.toRealPath().startsWith(gateway.root().toRealPath()))
                throw invalid("证件照片不存在，请重新上传");
        }
        catch (IOException e)
        {
            throw invalid("证件照片不存在，请重新上传");
        }
    }

    private static AppAuthException invalid(String message)
    {
        return new AppAuthException(AppUserErrorCodes.PARAM, 400, message);
    }
}
