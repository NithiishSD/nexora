package com.nexora.finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.nexora.shared.domain.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

// Money moved between two own accounts (FR-FIN-09): one TRANSFER_OUT and one TRANSFER_IN ledger row. Neither revenue
// nor expense; never edited, a mistake is corrected by a transfer the other way.
@Entity
@Table(name = "account_transfers")
public class AccountTransfer extends AuditableEntity {

    @Column(name = "transfer_number", nullable = false, length = 30, updatable = false)
    private String transferNumber;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false, updatable = false)
    private UUID destinationAccountId;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "transfer_date", nullable = false, updatable = false)
    private LocalDate transferDate;

    @Column(columnDefinition = "text", updatable = false)
    private String notes;

    protected AccountTransfer() {
        // for JPA only
    }

    public static AccountTransfer record(String number, UUID sourceAccountId, UUID destinationAccountId,
                                         BigDecimal amount, LocalDate date, String notes) {
        AccountTransfer t = new AccountTransfer();
        t.transferNumber = number;
        t.sourceAccountId = sourceAccountId;
        t.destinationAccountId = destinationAccountId;
        t.amount = amount;
        t.transferDate = date;
        t.notes = notes;
        return t;
    }

    public String getTransferNumber() {
        return transferNumber;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getTransferDate() {
        return transferDate;
    }

    public String getNotes() {
        return notes;
    }
}
