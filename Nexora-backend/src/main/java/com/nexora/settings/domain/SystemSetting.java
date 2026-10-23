package com.nexora.settings.domain;

import com.nexora.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_settings")
public class SystemSetting extends AuditableEntity {

    @Column(nullable = false, length = 64)
    private String category;

    @Column(name = "setting_key", nullable = false, length = 128)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    protected SystemSetting() {}

    public SystemSetting(String category, String settingKey, String settingValue) {
        this.category = category;
        this.settingKey = settingKey;
        this.settingValue = settingValue;
    }

    public String getCategory() {
        return category;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }
}
