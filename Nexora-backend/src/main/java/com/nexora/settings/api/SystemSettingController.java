package com.nexora.settings.api;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.settings.api.dto.UpdateSettingsRequest;
import com.nexora.settings.application.SystemSettingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/settings")
public class SystemSettingController {

    private final SystemSettingService settingService;

    public SystemSettingController(SystemSettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public Map<String, Map<String, String>> getAllSettings() {
        return settingService.getAllSettingsMasked();
    }

    @GetMapping("/{category}")
    @PreAuthorize("hasAuthority('FINANCE_VIEW')")
    public Map<String, String> getCategorySettings(@PathVariable String category) {
        return settingService.getCategorySettingsMasked(category);
    }

    @PutMapping("/{category}")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    public Map<String, String> updateCategory(
            @PathVariable String category,
            @Valid @RequestBody UpdateSettingsRequest request) {
        settingService.updateCategory(category, request.settings());
        return settingService.getCategorySettingsMasked(category);
    }

    @PostMapping("/backup/export")
    @PreAuthorize("hasAuthority('FINANCE_MANAGE')")
    public Map<String, Object> exportBackup() {
        return settingService.createBackupExport();
    }
}
