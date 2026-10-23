package com.nexora.orders.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.nexora.finance.application.AllocationQueryService;
import com.nexora.orders.api.OrderMapper;
import com.nexora.orders.api.dto.OrderResponse;
import com.nexora.orders.api.dto.OrderSummaryResponse;
import com.nexora.orders.domain.CustomerOrder;
import com.nexora.orders.infrastructure.OrderWorkRepository;
import com.nexora.orders.domain.OrderItem;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.shared.api.PageResponse;

// Builds the order views every order use case returns: the aggregate plus the customer's code and name and the
// amount allocated to each item, each fetched with one query. Runs inside the caller's transaction.
@Component
class OrderViews {

    private final PartyService parties;
    private final AllocationQueryService allocations;
    private final OrderWorkRepository work;
    private final Clock clock;

    OrderViews(PartyService parties, AllocationQueryService allocations, OrderWorkRepository work, Clock clock) {
        this.parties = parties;
        this.allocations = allocations;
        this.work = work;
        this.clock = clock;
    }

    OrderResponse of(CustomerOrder order) {
        return OrderMapper.toResponse(order, parties.getRef(order.getCustomerId()),
                allocatedByItem(itemIds(List.of(order))), work.productionBatches(order.getId()),
                work.outsourcingJobs(order.getId()), LocalDate.now(clock));
    }

    PageResponse<OrderSummaryResponse> page(Page<CustomerOrder> page) {
        Map<UUID, PartyRef> customers = parties.getRefs(page.getContent().stream()
                .map(CustomerOrder::getCustomerId).distinct().toList());
        Map<UUID, BigDecimal> allocated = allocatedByItem(itemIds(page.getContent()));
        LocalDate today = LocalDate.now(clock);
        return PageResponse.from(page.map(o -> OrderMapper.toSummary(o, customers.get(o.getCustomerId()), allocated,
                today)));
    }

    // True when any payment allocation (of an active payment) points at one of the order's items.
    boolean hasAllocations(CustomerOrder order) {
        return allocatedByItem(itemIds(List.of(order))).values().stream().anyMatch(a -> a.signum() > 0);
    }

    // Allocations of active payments per item, from finance in one query.
    private Map<UUID, BigDecimal> allocatedByItem(Collection<UUID> itemIds) {
        return allocations.allocatedByOrderItem(itemIds);
    }

    private static List<UUID> itemIds(List<CustomerOrder> orders) {
        return orders.stream().flatMap(o -> o.getItems().stream()).map(OrderItem::getId).toList();
    }
}
