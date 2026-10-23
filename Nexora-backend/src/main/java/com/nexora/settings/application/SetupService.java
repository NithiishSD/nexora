package com.nexora.settings.application;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.identity.application.OwnerBootstrap;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;

@Service
@Transactional
public class SetupService {

    private static final Logger log = LoggerFactory.getLogger(SetupService.class);

    private final OwnerBootstrap ownerBootstrap;
    private final SystemSettingService settingsService;

    public SetupService(OwnerBootstrap ownerBootstrap, SystemSettingService settingsService) {
        this.ownerBootstrap = ownerBootstrap;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true)
    public boolean isInitialized() {
        return ownerBootstrap.hasUsers();
    }

    @Transactional(readOnly = true)
    public String getBusinessName() {
        return settingsService.getRawValue("BUSINESS", "name", "SmartSilk Enterprises");
    }

    public void initializeSystem(String username, String password, String businessName, String phone, String city) {
        if (ownerBootstrap.hasUsers()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "System has already been initialized with an owner account");
        }

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.MALFORMED_REQUEST, "Username and password are required");
        }

        try {
            boolean created = ownerBootstrap.createOwnerIfNoUsers(username, password);
            if (!created) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "System has already been initialized");
            }
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.MALFORMED_REQUEST,
                    "Password must be 12-72 characters long, contain at least one letter and one number, and not match the username");
        }

        if (businessName != null && !businessName.isBlank()) {
            settingsService.updateCategory("BUSINESS", Map.of(
                    "name", businessName.trim(),
                    "phone", phone != null ? phone.trim() : "",
                    "city", city != null ? city.trim() : ""
            ));
        }

        log.info("event=first_run_setup_completed username={} businessName={}", username, businessName);
    }
}
