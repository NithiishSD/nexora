package com.nexora.outsourcing.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.nexora.finance.application.AllocationQueryService;
import com.nexora.inventory.api.dto.MovementResponse;
import com.nexora.inventory.application.InventoryQueryService;
import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.orders.application.OrderItemRef;
import com.nexora.orders.application.OrderService;
import com.nexora.outsourcing.api.dto.JobResponse;
import com.nexora.outsourcing.domain.OutsourcingJob;
import com.nexora.outsourcing.domain.OutsourcingOrderItem;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.domain.Money;
import com.nexora.shared.domain.PaymentStatus;
import com.nexora.shared.domain.Weight;

// Builds JobResponse: the aggregate plus its manufacturer (parties), the order numbers of its links (orders), the
// allocations of manufacturer payments (finance) and, on the detail, its receipts (inventory), one query each.
@Component
class JobViews {

    private final PartyService parties;
    private final OrderService orders;
    private final AllocationQueryService allocations;
    private final InventoryQueryService inventory;

    JobViews(PartyService parties, OrderService orders, AllocationQueryService allocations,
             InventoryQueryService inventory) {
        this.parties = parties;
        this.orders = orders;
        this.allocations = allocations;
        this.inventory = inventory;
    }

    // Detail: with its receipts.
    JobResponse of(OutsourcingJob job) {
        return rows(List.of(job), receipts(job)).getFirst();
    }

    // List rows: without receipts (E-OUT-02).
    PageResponse<JobResponse> page(Page<OutsourcingJob> page) {
        Map<UUID, JobResponse> rows = rows(page.getContent(), null).stream()
                .collect(Collectors.toMap(JobResponse::id, r -> r));
        return PageResponse.from(page.map(j -> rows.get(j.getId())));
    }

    // One receipt = its OUTSOURCE_RECEIPT movement plus the WASTAGE movement written with it. Every receipt moves warp
    // (received > 0), so a WASTAGE row always belongs to the receipt just before it in write order.
    private List<JobResponse.Receipt> receipts(OutsourcingJob job) {
        List<JobResponse.Receipt> receipts = new ArrayList<>();
        for (MovementResponse m : inventory.movementsOf(MovementReferenceType.OUTSOURCING_JOB, job.getId())) {
            if (m.movementType() == MovementType.OUTSOURCE_RECEIPT) {
                receipts.add(new JobResponse.Receipt(m.movementDate(), m.quantityKg(), Weight.zero(), m.movementNumber(),
                        null));
            } else if (m.movementType() == MovementType.WASTAGE && !receipts.isEmpty()) {
                JobResponse.Receipt last = receipts.removeLast();
                receipts.add(new JobResponse.Receipt(last.receiptDate(), last.receivedWeightKg(), m.quantityKg(),
                        last.receiptMovementNumber(), m.movementNumber()));
            }
        }
        return receipts;
    }

    private List<JobResponse> rows(List<OutsourcingJob> jobs, List<JobResponse.Receipt> receipts) {
        Map<UUID, PartyRef> manufacturers = parties.getRefs(jobs.stream().map(OutsourcingJob::getManufacturerId)
                .distinct().toList());
        Map<UUID, OrderItemRef> refs = orders.itemRefs(jobs.stream().flatMap(j -> j.getOrderItems().stream())
                .map(OutsourcingOrderItem::getOrderItemId).distinct().toList());
        Map<UUID, BigDecimal> allocated = allocations.allocatedByJob(jobs.stream().map(OutsourcingJob::getId).toList());
        return jobs.stream().map(j -> {
            BigDecimal payable = j.payableAmount();
            BigDecimal paid = allocated.getOrDefault(j.getId(), Money.zero());
            BigDecimal outstanding = payable.subtract(paid);
            return new JobResponse(j.getId(), j.getJobNumber(), manufacturers.get(j.getManufacturerId()),
                    j.getProductType(), j.getIssuedWeightKg(), j.getReceivedWeightKg(), j.getWastageWeightKg(),
                    j.outsideWeightKg(), j.getRatePerKg(), payable, paid, outstanding,
                    PaymentStatus.of(paid, outstanding), j.getIssueDate(), j.getReceivedDate(), j.getStatus(),
                    j.getOrderItems().stream().map(l -> {
                        OrderItemRef ref = refs.get(l.getOrderItemId());
                        return new JobResponse.Link(l.getOrderItemId(), ref.orderId(), ref.orderNumber(), ref.lineNo(),
                                l.getAllocatedWeightKg());
                    }).toList(),
                    receipts, j.getNotes(), j.getCreatedAt(), j.getUpdatedAt(), j.getCreatedBy());
        }).toList();
    }
}
