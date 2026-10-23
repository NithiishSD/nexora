package com.nexora.settings.api.dto;

import java.util.Map;
import jakarta.validation.constraints.NotNull;

public record UpdateSettingsRequest(
        @NotNull(message = "Settings map must not be null")
        Map<String, String> settings
) {}
