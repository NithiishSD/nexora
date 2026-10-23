package com.nexora.orders.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.inventory.domain.ProductType;
import com.nexora.orders.api.dto.CancelOrderRequest;
import com.nexora.orders.api.dto.CreateOrderRequest;
import com.nexora.orders.api.dto.CustomerReturnResponse;
import com.nexora.orders.api.dto.DeliverItemRequest;
import com.nexora.orders.api.dto.DeliverOrderRequest;
import com.nexora.orders.api.dto.OrderResponse;
import com.nexora.orders.api.dto.OrderSummaryResponse;
import com.nexora.orders.api.dto.RecordCustomerReturnRequest;
import com.nexora.orders.api.dto.UpdateOrderRequest;
import com.nexora.documents.api.dto.DocumentResponse;
import com.nexora.orders.application.OrderBillService;
import com.nexora.orders.application.OrderDeliveryService;
import com.nexora.orders.application.OrderReturnService;
import com.nexora.orders.application.OrderService;
import com.nexora.orders.domain.OrderStatus;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.idempotency.Idempotent;

import jakarta.validation.Valid;

// HTTP only: validate, call one service method, choose the status. No DELETE and no PUT: orders are history
// (FR-ORD-17 -> 405).
@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

    private final OrderService orderService;
    private final OrderDeliveryService deliveryService;
    private final OrderReturnService returnService;
    private final OrderBillService billService;

    OrderController(OrderService orderService, OrderDeliveryService deliveryService,
                    OrderReturnService returnService, OrderBillService billService) {
        this.orderService = orderService;
        this.deliveryService = deliveryService;
        this.returnService = returnService;
        this.billService = billService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + created.id())).body(created);
    }

    // GET /api/v1/orders?customerId=&status=&productType=&from=&to=&pending=&late=&page=&size=&sort=
    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    PageResponse<OrderSummaryResponse> list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean pending,
            @RequestParam(required = false) Boolean late,
            @PageableDefault(size = 20, sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return orderService.list(customerId, status, productType, from, to, pending, late, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    OrderResponse get(@PathVariable UUID id) {
        return orderService.get(id);
    }

    // E-ORD-04: only while PLACED/CONFIRMED, unlocked and unpaid.
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    OrderResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateOrderRequest request) {
        return orderService.update(id, request);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    OrderResponse confirm(@PathVariable UUID id) {
        return orderService.confirm(id);
    }

    // A business action, not a delete: the order stays as history with status CANCELLED.
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    OrderResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelOrderRequest request) {
        return orderService.cancel(id, request.reason());
    }

    @PostMapping("/{id}/ready")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    OrderResponse ready(@PathVariable UUID id) {
        return orderService.markReady(id);
    }

    // Idempotency-Key required: a retried delivery must not move the stock twice.
    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    @Idempotent
    OrderResponse deliver(@PathVariable UUID id, @Valid @RequestBody DeliverOrderRequest request) {
        return deliveryService.deliver(id, request);
    }

    // E-ORD-10: per-item or partial delivery. Idempotency-Key required.
    @PostMapping("/{id}/items/{itemId}/deliveries")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    @Idempotent
    OrderResponse deliverItem(@PathVariable UUID id, @PathVariable UUID itemId,
                              @Valid @RequestBody DeliverItemRequest request) {
        return deliveryService.deliverItem(id, itemId, request);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    OrderResponse complete(@PathVariable UUID id) {
        return orderService.complete(id);
    }

    @PostMapping("/{id}/returns")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    @Idempotent
    ResponseEntity<CustomerReturnResponse> recordReturn(@PathVariable UUID id,
                                                        @Valid @RequestBody RecordCustomerReturnRequest request) {
        CustomerReturnResponse created = returnService.recordReturn(id, request);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + id + "/returns/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}/returns")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    List<CustomerReturnResponse> getReturns(@PathVariable UUID id) {
        return returnService.getReturns(id);
    }

    // E-ORD-12: Generate the sales bill (PDF document) for a delivered or completed order.
    @PostMapping("/{id}/bill")
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    ResponseEntity<DocumentResponse> generateBill(@PathVariable UUID id) {
        DocumentResponse doc = billService.generateBill(id);
        return ResponseEntity.created(URI.create("/api/v1/documents/" + doc.id())).body(doc);
    }
}
