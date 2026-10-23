package com.nexora.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.nexora.inventory.application.InventoryThresholdService;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.notifications.NotificationTestSupport;

class LowStockAlertJobIntegrationTest extends NotificationTestSupport {

    @Autowired
    private LowStockAlertJob lowStockJob;

    @Autowired
    private InventoryThresholdService thresholdService;

    @Test
    @DisplayName("FR-NTF-07 job creates low-stock notifications with dedupe key and prevents duplicate on same day")
    void job_createsLowStockNotifications_withDedupe() {
        // Set threshold of 50.000 kg for RAW_SILK in RAW_STOCK
        thresholdService.setThreshold(ProductType.RAW_SILK, StockLocation.RAW_STOCK, new BigDecimal("50.000"));

        // Stock is currently 0 kg (below 50 kg)
        int created = lowStockJob.run(today());
        assertThat(created).isEqualTo(1);

        // Verify notification row in database
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM notifications WHERE notification_type = 'LOW_STOCK'");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();
        assertThat(row.get("notification_type")).isEqualTo("LOW_STOCK");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat((String) row.get("message")).contains("Low stock alert: RAW_SILK at RAW_STOCK")
                .contains("minimum threshold: 50.000 kg");
        assertThat((String) row.get("dedupe_key")).isEqualTo("LOW_STOCK:RAW_SILK:RAW_STOCK:" + today());

        // Second run on the same day creates 0 notifications due to dedupe key
        int secondRun = lowStockJob.run(today());
        assertThat(secondRun).isZero();
    }
}
