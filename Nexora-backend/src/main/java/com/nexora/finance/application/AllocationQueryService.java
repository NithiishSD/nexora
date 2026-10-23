package com.nexora.finance.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.domain.CustomerBalance;
import com.nexora.finance.domain.PaymentPurpose;
import com.nexora.finance.infrastructure.ObligationQueryRepository;

// What other modules may ask finance about settlement (03-ARCHITECTURE: modules talk through services).
@Service
public class AllocationQueryService {

    private final ObligationQueryRepository obligations;

    public AllocationQueryService(ObligationQueryRepository obligations) {
        this.obligations = obligations;
    }

    // Allocated amount per order item, active (RECORDED) payments only; items without allocations are absent.
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> allocatedByOrderItem(Collection<UUID> itemIds) {
        return obligations.allocatedByOrderItem(itemIds);
    }

    // Allocated amount per purchase, active payments only; purchases without allocations are absent.
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> allocatedByPurchase(Collection<UUID> purchaseIds) {
        return obligations.allocatedByPurchase(purchaseIds);
    }

    // Allocated amount per outsourcing job, active payments only; jobs without allocations are absent.
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> allocatedByJob(Collection<UUID> jobIds) {
        return obligations.allocatedByJob(jobIds);
    }

    // Allocated amount per work record, active payments only; records without allocations are absent.
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> allocatedByWorkRecord(Collection<UUID> recordIds) {
        return obligations.allocatedByWorkRecord(recordIds);
    }

    // A party's opening obligations of one purpose that are still open (active payments only), oldest first.
    @Transactional(readOnly = true)
    public List<OpeningBalanceLine> openOpeningObligations(UUID partyId, PaymentPurpose purpose) {
        return obligations.openingBalances(partyId, purpose.name()).stream()
                .map(o -> new OpeningBalanceLine(o.id(), o.obligationDate(), o.referenceNote(), o.originalAmount(),
                        o.allocated(), o.originalAmount().subtract(o.allocated())))
                .toList();
    }

    // Sum of what is still owed for one purpose (customer receivables, manufacturer or worker payable), all parties.
    @Transactional(readOnly = true)
    public BigDecimal totalOutstanding(PaymentPurpose purpose) {
        return obligations.totalOutstanding(purpose);
    }

    // Sum of every customer's advance (unallocated part of active payments).
    @Transactional(readOnly = true)
    public BigDecimal totalCustomerAdvance() {
        return obligations.totalCustomerAdvance();
    }

    // Every customer with something outstanding or an advance, one aggregate query (customer outstanding report).
    @Transactional(readOnly = true)
    public List<CustomerBalance> customerBalances() {
        return obligations.customerBalances();
    }
}
