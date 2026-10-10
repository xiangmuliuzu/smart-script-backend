package com.smartscript.platform.user.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * A5 通知偏好保存请求（契约 §1.5）。
 *
 * 只提交需要改动的组合；未提交的组合保持原值。
 */
public class NotificationPreferenceUpdateRequest
{
    @NotEmpty
    private List<NotificationPreferenceDto> preferences;

    public List<NotificationPreferenceDto> getPreferences()
    {
        return preferences;
    }

    public void setPreferences(List<NotificationPreferenceDto> preferences)
    {
        this.preferences = preferences;
    }
}
