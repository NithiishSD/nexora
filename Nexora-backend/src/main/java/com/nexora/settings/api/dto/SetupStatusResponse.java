package com.nexora.settings.api.dto;

public record SetupStatusResponse(
        boolean initialized,
        String businessName
) {}
