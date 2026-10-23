package com.nexora.settings.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.settings.domain.SystemSetting;
import com.nexora.settings.infrastructure.SystemSettingRepository;

@Service
@Transactional
public class SystemSettingService {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "token",
            "api_key",
            "secret",
            "password",
            "private_key",
            "credential",
            "access_key",
            "client_secret"
    );

    private final SystemSettingRepository repository;
    private final java.time.Clock clock;

    public SystemSettingService(SystemSettingRepository repository, java.time.Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<String, Map<String, String>> getAllSettingsMasked() {
        List<SystemSetting> all = repository.findAll();
        Map<String, Map<String, String>> result = new HashMap<>();

        for (SystemSetting setting : all) {
            String cat = setting.getCategory().toUpperCase(Locale.ROOT);
            result.putIfAbsent(cat, new HashMap<>());
            String val = setting.getSettingValue();
            if (val != null && isSensitive(setting.getSettingKey())) {
                val = "******";
            }
            result.get(cat).put(setting.getSettingKey(), val);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, String> getCategorySettingsMasked(String category) {
        String catUpper = category.toUpperCase(Locale.ROOT);
        List<SystemSetting> list = repository.findByCategory(catUpper);
        Map<String, String> result = new HashMap<>();
        for (SystemSetting s : list) {
            String val = s.getSettingValue();
            if (val != null && isSensitive(s.getSettingKey())) {
                val = "******";
            }
            result.put(s.getSettingKey(), val);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public String getRawValue(String category, String key, String defaultValue) {
        return repository.findByCategoryAndSettingKey(category.toUpperCase(Locale.ROOT), key)
                .map(SystemSetting::getSettingValue)
                .orElse(defaultValue);
    }

    public void updateCategory(String category, Map<String, String> values) {
        String catUpper = category.toUpperCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();

            // Skip updating if value is masked placeholder
            if ("******".equals(val)) {
                continue;
            }

            repository.findByCategoryAndSettingKey(catUpper, key)
                    .ifPresentOrElse(
                            existing -> existing.setSettingValue(val),
                            () -> repository.save(new SystemSetting(catUpper, key, val))
                    );
        }
    }

    public Map<String, Object> createBackupExport() {
        Map<String, Object> backup = new HashMap<>();
        backup.put("timestamp", clock.instant().toString());
        backup.put("version", "1.0.0");
        backup.put("settings", getAllSettingsMasked());
        return backup;
    }

    private boolean isSensitive(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.stream().anyMatch(lower::contains);
    }
}
