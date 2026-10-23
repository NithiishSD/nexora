package com.nexora.inventory.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs the reconciliation check every night (FR-INV-09) and writes one ERROR line per difference, so monitoring
// can alert on it. It only reads and logs: a difference is investigated by a person, never "fixed" automatically.
@Component
public class InventoryReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(InventoryReconciliationJob.class);

    private final InventoryReconciliationService reconciliation;

    public InventoryReconciliationJob(InventoryReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    // 02:30 business time every day (a quiet hour); the time can be changed without a code change.
    @Scheduled(cron = "${nexora.inventory.reconciliation-cron:0 30 2 * * *}", zone = "Asia/Kolkata")
    public void run() {
        List<InventoryReconciliationService.Difference> differences = reconciliation.findDifferences();
        for (InventoryReconciliationService.Difference d : differences) {
            log.error("event=inventory_balance_mismatch product={} location={} expectedKg={} actualKg={}",
                    d.productType(), d.location(), d.expectedKg(), d.actualKg());
        }
        log.info("event=inventory_reconciliation_done differences={}", differences.size());
    }
}
