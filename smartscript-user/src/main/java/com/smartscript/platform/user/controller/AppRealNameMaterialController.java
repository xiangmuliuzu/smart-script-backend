package com.smartscript.platform.user.controller;

import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.smartscript.platform.api.AppApiResponse;
import com.smartscript.platform.user.constant.AppAuthErrorCodes;
import com.smartscript.platform.user.exception.AppAuthException;
import com.smartscript.platform.user.security.AppIdentityContext;
import com.smartscript.platform.user.service.AppRealNameMaterialService;

/** 与 /users/** 共用 App 凭证链，不接受客户端传入用户 ID。 */
@RestController
@RequestMapping("/api/v1/users/me/real-name")
public class AppRealNameMaterialController
{
    private final AppRealNameMaterialService service;

    public AppRealNameMaterialController(AppRealNameMaterialService service)
    {
        this.service = service;
    }

    @PostMapping("/material")
    public AppApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file)
    {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof AppIdentityContext identity))
            throw new AppAuthException(AppAuthErrorCodes.UNAUTHORIZED, 401, "unauthorized");
        return AppApiResponse.ok(service.upload(identity.getUserId(), file));
    }
}
