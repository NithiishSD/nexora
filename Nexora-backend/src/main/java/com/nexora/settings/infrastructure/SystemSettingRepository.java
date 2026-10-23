package com.nexora.settings.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexora.settings.domain.SystemSetting;

@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, UUID> {
    List<SystemSetting> findByCategory(String category);
    Optional<SystemSetting> findByCategoryAndSettingKey(String category, String settingKey);
}
