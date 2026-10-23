package com.nexora.orders.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.inventory.application.InventoryQueryService;
import com.nexora.inventory.application.InventoryService;
import com.nexora.inventory.application.MovementCommand;
import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.orders.api.dto.DeliverItemRequest;
import com.nexora.orders.api.dto.DeliverOrderRequest;
import com.nexora.orders.api.dto.OrderResponse;
import com.nexora.orders.domain.CustomerOrder;
import com.nexora.orders.domain.OrderItem;
import com.nexora.orders.infrastructure.CustomerOrderRepository;
import com.nexora.shared.domain.Weight;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.ResourceNotFoundException;

// E-ORD-08 and E-ORD-10: delivery operations with inventory movements.
@Service
public class OrderDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(OrderDeliveryService.class);

    private final CustomerOrderRepository orders;
    private final InventoryService inventory;
    private final InventoryQueryService inventoryQuery;
    private final OrderViews views;

    public OrderDeliveryService(CustomerOrderRepository orders, InventoryService inventory,
                                InventoryQueryService inventoryQuery, OrderViews views) {
        this.orders = orders;
        this.inventory = inventory;
        this.inventoryQuery = inventoryQuery;
        this.views = views;
    }

    // Lock order (ADR-028): order row -> balance rows -> MOV numbers. The party row is not needed: delivery does not
    // depend on payments. Every item leaves whole; when any item lacks stock the inventory throws 409
    // INSUFFICIENT_INVENTORY and the whole transaction rolls back, so no item moves and the order stays READY.
    @Transactional
    public OrderResponse deliver(UUID id, DeliverOrderRequest r) {
        CustomerOrder order = orders.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Order", id));
        order.deliver(r.deliveryDate());                       // 409 INVALID_STATE_TRANSITION unless READY, before any stock lock
        String notes = r.notes() == null || r.notes().isBlank() ? null : r.notes().strip();
        List<MovementCommand> movements = order.getItems().stream().map(i -> movement(order, i, r, notes)).toList();
        inventory.recordMovements(movements);
        log.info("event=order_delivered number={} items={}", order.getOrderNumber(), movements.size());
        return views.of(orders.saveAndFlush(order));
    }

    // E-ORD-10: Per-item or partial delivery.
    @Transactional
    public OrderResponse deliverItem(UUID orderId, UUID itemId, DeliverItemRequest r) {
        CustomerOrder order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Order item", itemId));

        BigDecimal alreadyDelivered = inventoryQuery.getDeliveredWeight(MovementReferenceType.ORDER_ITEM, item.getId());
        BigDecimal remaining = item.getRequiredWeightKg().subtract(alreadyDelivered);
        BigDecimal deliverKg = Weight.round(r.quantityKg());

        if (deliverKg.compareTo(remaining) > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Cannot deliver " + deliverKg + " kg; only " + remaining + " kg remaining to be delivered for line "
                            + item.getLineNo() + ".");
        }

        String notes = r.notes() == null || r.notes().isBlank() ? null : r.notes().strip();
        boolean trading = item.getProductType() == ProductType.RAW_SILK || item.getProductType() == ProductType.VUDA_WARP;
        MovementCommand cmd = new MovementCommand(item.getProductType(), null,
                trading ? MovementType.DIRECT_SALE : MovementType.DELIVERY,
                trading ? StockLocation.RAW_STOCK : StockLocation.FINISHED_STOCK, StockLocation.CUSTOMER,
                deliverKg, r.deliveryDate(), MovementReferenceType.ORDER_ITEM, item.getId(),
                order.getOrderNumber(), null, notes);
        inventory.recordMovement(cmd);

        boolean fullyDelivered = alreadyDelivered.add(deliverKg).compareTo(item.getRequiredWeightKg()) >= 0;
        order.deliverItem(item, fullyDelivered, r.deliveryDate());

        log.info("event=order_item_delivered order={} item={} kg={}", order.getOrderNumber(), item.getLineNo(), deliverKg);
        return views.of(orders.saveAndFlush(order));
    }

    // Warp leaves finished stock (DELIVERY); raw silk and vuda warp are trading stock sold from RAW_STOCK
    // (DIRECT_SALE, assumption Q7, plan decision R2).
    private static MovementCommand movement(CustomerOrder order, OrderItem item, DeliverOrderRequest r, String notes) {
        boolean trading = item.getProductType() == ProductType.RAW_SILK || item.getProductType() == ProductType.VUDA_WARP;
        return new MovementCommand(item.getProductType(), null,
                trading ? MovementType.DIRECT_SALE : MovementType.DELIVERY,
                trading ? StockLocation.RAW_STOCK : StockLocation.FINISHED_STOCK, StockLocation.CUSTOMER,
                item.getRequiredWeightKg(), r.deliveryDate(), MovementReferenceType.ORDER_ITEM, item.getId(),
                order.getOrderNumber(), null, notes);
    }
}
