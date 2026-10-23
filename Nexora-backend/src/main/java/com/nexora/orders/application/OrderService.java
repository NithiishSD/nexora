package com.nexora.orders.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.application.AdvanceAllocationService;
import com.nexora.inventory.domain.ProductType;
import com.nexora.orders.api.dto.CreateOrderRequest;
import com.nexora.orders.api.dto.OrderItemRequest;
import com.nexora.orders.api.dto.OrderResponse;
import com.nexora.orders.api.dto.OrderSummaryResponse;
import com.nexora.orders.api.dto.UpdateOrderRequest;
import com.nexora.orders.domain.CustomerOrder;
import com.nexora.shared.audit.AuditAction;
import com.nexora.shared.audit.AuditService;
import com.nexora.orders.domain.OrderItem;
import com.nexora.orders.domain.OrderLine;
import com.nexora.orders.domain.OrderStatus;
import com.nexora.orders.infrastructure.CustomerOrderRepository;
import com.nexora.orders.infrastructure.OrderSpecifications;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.shared.api.ListParameters;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.FieldValidationException;
import com.nexora.shared.error.InvalidProductForOperationException;
import com.nexora.shared.error.ResourceNotFoundException;
import com.nexora.shared.numbering.BusinessNumberGenerator;

// Customer order use cases: create, view, list, edit and the status actions (delivery is in OrderDeliveryService). Each public method is one use case = one transaction.
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // Allowed sort properties (E-ORD-02).
    private static final Set<String> SORTABLE = Set.of("orderDate", "orderNumber", "requiredDate", "createdAt");
    // A batch or job may serve an order that is owed and not yet delivered (05 section 13, BatchLink).
    private static final Set<OrderStatus> WORKABLE = EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.IN_PROGRESS,
            OrderStatus.READY);

    private final CustomerOrderRepository orders;
    private final PartyService parties;
    private final BusinessNumberGenerator numbers;
    private final OrderViews views;
    private final Clock clock;
    private final AuditService audit;
    private final AdvanceAllocationService advances;

    public OrderService(CustomerOrderRepository orders, PartyService parties, BusinessNumberGenerator numbers,
                        OrderViews views, Clock clock, AuditService audit, AdvanceAllocationService advances) {
        this.audit = audit;
        this.advances = advances;
        this.orders = orders;
        this.parties = parties;
        this.numbers = numbers;
        this.views = views;
        this.clock = clock;
    }

    // E-ORD-01. The ORD number is the last lock taken (ADR-028); if the order is rejected after it, the counter
    // increment rolls back with everything else (gap-free numbers).
    @Transactional
    public OrderResponse create(CreateOrderRequest r) {
        // 404 unknown, 422 INVALID_PARTY_ROLE (not a customer), 422 PARTY_INACTIVE (FR-ORD-01 AC4/AC5)
        parties.requireActiveWithRole(r.customerId(), PartyRole.CUSTOMER);
        List<OrderLine> lines = toLines(r.items());
        String number = numbers.next("ORD");
        CustomerOrder order = orders.save(CustomerOrder.place(number, r.customerId(), r.orderDate(), r.requiredDate(),
                blankToNull(r.notes()), lines));
        log.info("event=order_created number={} total={}", number, order.totalAmount());
        return views.of(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return views.of(find(id));
    }

    // E-ORD-02. Items of the page are batch-loaded (@BatchSize) and customers fetched in one query (no N+1).
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> list(UUID customerId, OrderStatus status, ProductType productType,
                                                   LocalDate from, LocalDate to, Boolean pending, Boolean late,
                                                   Pageable pageable) {
        ListParameters.requireSortable(pageable, SORTABLE);
        ListParameters.requireDateRange(from, to);
        Specification<CustomerOrder> filter = (root, query, cb) -> cb.conjunction();
        for (Specification<CustomerOrder> part : Arrays.asList(OrderSpecifications.customer(customerId),
                OrderSpecifications.status(status), OrderSpecifications.product(productType),
                OrderSpecifications.from(from), OrderSpecifications.to(to), OrderSpecifications.pending(pending),
                OrderSpecifications.late(late, LocalDate.now(clock)))) {
            if (part != null) {
                filter = filter.and(part);
            }
        }
        return views.page(orders.findAll(filter, pageable));
    }

    // E-ORD-04: edit while PLACED/CONFIRMED, unlocked and unpaid. The customer row is locked first (ADR-027/028), so
    // a payment cannot allocate to the order between the "no allocations" check and the new amounts.
    @Transactional
    public OrderResponse update(UUID id, UpdateOrderRequest r) {
        CustomerOrder order = lockOrder(id);
        order.edit(r.requiredDate() != null ? r.requiredDate() : order.getRequiredDate(),
                r.notes() == null ? order.getNotes() : blankToNull(r.notes()),
                r.items() == null ? null : toLines(r.items()),
                views.hasAllocations(order));
        log.info("event=order_updated number={} total={}", order.getOrderNumber(), order.totalAmount());
        return views.of(orders.saveAndFlush(order));               // flush: the response shows the new updatedAt
    }

    // E-ORD-05: PLACED -> CONFIRMED; from now on the items are owed (assumption Q2), and the customer's advance pays
    // them at once, oldest payment first (FR-PAY-05). The party lock taken by lockOrder keeps payments out meanwhile.
    @Transactional
    public OrderResponse confirm(UUID id) {
        CustomerOrder order = lockOrder(id);
        order.confirm();
        orders.flush();                                            // finance's SQL must see the order as owed
        advances.applyAdvance(order.getCustomerId(), order.getItems().stream().map(OrderItem::getId).toList());
        log.info("event=order_confirmed number={}", order.getOrderNumber());
        return views.of(orders.saveAndFlush(order));
    }

    // E-ORD-06: refused while locked (ORDER_LOCKED) or while payments are allocated (reverse them first).
    @Transactional
    public OrderResponse cancel(UUID id, String reason) {
        CustomerOrder order = lockOrder(id);
        order.cancel(views.hasAllocations(order));
        audit.record(AuditAction.ORDER_CANCELLED, "ORDER", order.getId(), reason,
                Map.of("orderNumber", order.getOrderNumber(), "totalAmount", order.totalAmount().toPlainString()));
        log.info("event=order_cancelled number={} reason={}", order.getOrderNumber(), reason);
        return views.of(orders.saveAndFlush(order));
    }

    // E-ORD-07: IN_PROGRESS -> READY, or CONFIRMED -> READY when filled from stock.
    @Transactional
    public OrderResponse markReady(UUID id) {
        CustomerOrder order = lockOrder(id);
        order.markReady();
        log.info("event=order_ready number={}", order.getOrderNumber());
        return views.of(orders.saveAndFlush(order));
    }

    // E-ORD-09: DELIVERED -> COMPLETED, whatever the payment status.
    @Transactional
    public OrderResponse complete(UUID id) {
        CustomerOrder order = lockOrder(id);
        order.complete();
        log.info("event=order_completed number={}", order.getOrderNumber());
        return views.of(orders.saveAndFlush(order));
    }

    // ---- contract for production and outsourcing (03-ARCHITECTURE 5.5) -------------------------------------

    // The order items a batch or job of this warp type may serve, checked in the order 400 listed twice -> 404 unknown
    // -> 422 other warp type -> 409 order not owed or already delivered (05 section 13, BatchLink). "what" names the
    // work in messages ("batch", "job").
    @Transactional(readOnly = true)
    public void checkWorkLinks(ProductType productType, List<UUID> itemIds, String what) {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < itemIds.size(); i++) {
            if (!seen.add(itemIds.get(i))) {
                throw new FieldValidationException("orderItems[" + i + "].orderItemId", "The same item is listed twice.");
            }
        }
        Map<UUID, OrderItemRef> refs = itemRefs(seen);
        for (UUID id : itemIds) {
            OrderItemRef ref = refs.get(id);
            if (ref == null) {
                throw new ResourceNotFoundException("Order item", id);
            }
            if (ref.productType() != productType) {
                throw new InvalidProductForOperationException(ref.orderNumber() + " line " + ref.lineNo() + " is "
                        + ref.productType() + "; this " + what + " makes " + productType + ".");
            }
            if (!WORKABLE.contains(ref.orderStatus())) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Order " + ref.orderNumber() + " is "
                        + ref.orderStatus() + "; only confirmed orders not yet delivered can be worked on.");
            }
        }
    }

    // References of order items, by id; unknown ids are simply absent (the caller decides 404).
    @Transactional(readOnly = true)
    public Map<UUID, OrderItemRef> itemRefs(Collection<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return orders.findItemsWithOrder(itemIds).stream().collect(Collectors.toMap(OrderItem::getId,
                i -> new OrderItemRef(i.getId(), i.getOrder().getId(), i.getOrder().getOrderNumber(), i.getLineNo(),
                        i.getProductType(), i.getOrder().getStatus())));
    }

    // The item ids of one order (filter "batches serving this order").
    @Transactional(readOnly = true)
    public List<UUID> itemIdsOf(UUID orderId) {
        return orders.findItemIds(orderId);
    }

    // Called by production (batch start) and outsourcing (job issue) inside their use case: every order of the given
    // items becomes IN_PROGRESS and locked (FR-ORD-08). Orders are locked in ascending id (ADR-028: aggregates after
    // the caller's own aggregate, before stock balances), so two batches serving the same orders cannot deadlock.
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForProduction(Collection<UUID> orderItemIds) {
        if (orderItemIds.isEmpty()) {
            return;
        }
        for (CustomerOrder order : orders.findOrdersOfItemsForUpdate(orderItemIds)) {
            order.lockForWork();
            log.info("event=order_locked_for_work number={} status={}", order.getOrderNumber(), order.getStatus());
        }
        orders.flush();
    }

    // Lock order (ADR-028): the customer's party row, then the order row. Status changes of one order therefore
    // run one after the other, and never in between a payment's allocation to this customer.
    private CustomerOrder lockOrder(UUID id) {
        UUID customerId = orders.findCustomerId(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
        parties.lockForSettlement(customerId);
        return orders.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    CustomerOrder find(UUID id) {
        return orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    static List<OrderLine> toLines(List<OrderItemRequest> items) {
        return items.stream().map(i -> new OrderLine(i.productType(), i.requiredWeightKg(), i.ratePerKg(),
                blankToNull(i.warpSpecification()), blankToNull(i.notes()))).toList();
    }

    static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
