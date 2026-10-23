package com.nexora.orders.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.inventory.application.InventoryQueryService;
import com.nexora.inventory.application.InventoryService;
import com.nexora.inventory.application.MovementCommand;
import com.nexora.inventory.application.MovementResult;
import com.nexora.inventory.domain.MovementReferenceType;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;
import com.nexora.orders.api.dto.CustomerReturnResponse;
import com.nexora.orders.api.dto.RecordCustomerReturnRequest;
import com.nexora.orders.domain.CustomerOrder;
import com.nexora.orders.domain.CustomerReturn;
import com.nexora.orders.domain.OrderItem;
import com.nexora.orders.domain.OrderStatus;
import com.nexora.orders.infrastructure.CustomerOrderRepository;
import com.nexora.orders.infrastructure.CustomerReturnRepository;
import com.nexora.shared.domain.Money;
import com.nexora.shared.domain.Weight;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.InvalidStateTransitionException;
import com.nexora.shared.error.ResourceNotFoundException;
import com.nexora.shared.numbering.BusinessNumberGenerator;

// Customer return use cases (FR-ORD-20, FR-INV-17): record return with stock movement, list returns.
@Service
public class OrderReturnService {

    private static final Logger log = LoggerFactory.getLogger(OrderReturnService.class);

    private final CustomerOrderRepository orders;
    private final CustomerReturnRepository customerReturns;
    private final InventoryService inventory;
    private final InventoryQueryService inventoryQuery;
    private final BusinessNumberGenerator numbers;

    public OrderReturnService(CustomerOrderRepository orders, CustomerReturnRepository customerReturns,
                              InventoryService inventory, InventoryQueryService inventoryQuery,
                              BusinessNumberGenerator numbers) {
        this.orders = orders;
        this.customerReturns = customerReturns;
        this.inventory = inventory;
        this.inventoryQuery = inventoryQuery;
        this.numbers = numbers;
    }

    // Lock order (ADR-028): order row -> balance rows -> CRN number -> MOV number.
    @Transactional
    public CustomerReturnResponse recordReturn(UUID orderId, RecordCustomerReturnRequest request) {
        CustomerOrder order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.COMPLETED) {
            throw new InvalidStateTransitionException("Order " + order.getOrderNumber(),
                    order.getStatus(), "RETURNED");
        }

        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(request.orderItemId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("OrderItem", request.orderItemId()));

        BigDecimal alreadyReturned = customerReturns.sumReturnedWeightByItemId(item.getId());
        BigDecimal remaining = item.getRequiredWeightKg().subtract(alreadyReturned);
        BigDecimal returnKg = Weight.round(request.returnedWeightKg());

        if (returnKg.compareTo(remaining) > 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_INVENTORY,
                    "Cannot return " + returnKg + " kg; only " + remaining + " kg still returnable for line "
                            + item.getLineNo() + ".");
        }

        String returnNumber = numbers.next("CRN");
        CustomerReturn ret = CustomerReturn.of(order, item, returnNumber, request.returnDate(),
                returnKg, request.usable(), request.reason(), request.notes());
        ret = customerReturns.saveAndFlush(ret);

        StockLocation toLocation;
        if (request.usable()) {
            boolean trading = item.getProductType() == ProductType.RAW_SILK
                    || item.getProductType() == ProductType.VUDA_WARP;
            toLocation = trading ? StockLocation.RAW_STOCK : StockLocation.FINISHED_STOCK;
        } else {
            toLocation = StockLocation.WASTAGE;
        }

        MovementCommand cmd = new MovementCommand(
                item.getProductType(), null,
                MovementType.CUSTOMER_RETURN,
                StockLocation.CUSTOMER, toLocation,
                returnKg, request.returnDate(),
                MovementReferenceType.CUSTOMER_RETURN, ret.getId(),
                order.getOrderNumber(),
                request.usable() ? null : "CUSTOMER_RETURN_DAMAGED",
                request.notes()
        );
        MovementResult mov = inventory.recordMovement(cmd);

        log.info("event=customer_return_recorded returnNumber={} orderNumber={} weight={} usable={} toLocation={}",
                returnNumber, order.getOrderNumber(), returnKg, request.usable(), toLocation);

        BigDecimal creditedAmount = Money.round(returnKg.multiply(item.getRatePerKg()));
        return new CustomerReturnResponse(
                ret.getId(), ret.getReturnNumber(), order.getId(), item.getId(),
                item.getLineNo(), item.getProductType(), ret.getReturnDate(),
                ret.getReturnedWeightKg(), item.getRatePerKg(), creditedAmount,
                ret.isUsable(), mov.movementNumber(), ret.getReason(), ret.getNotes(),
                ret.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<CustomerReturnResponse> getReturns(UUID orderId) {
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        List<CustomerReturn> list = customerReturns.findByOrderIdOrderByCreatedAtAsc(orderId);
        if (list.isEmpty()) {
            return List.of();
        }

        List<UUID> returnIds = list.stream().map(CustomerReturn::getId).toList();
        Map<UUID, String> movNumbers = inventoryQuery.movementNumbersByReference(
                MovementReferenceType.CUSTOMER_RETURN, MovementType.CUSTOMER_RETURN, returnIds);

        return list.stream().map(ret -> {
            OrderItem item = ret.getOrderItem();
            BigDecimal credited = Money.round(ret.getReturnedWeightKg().multiply(item.getRatePerKg()));
            String movNum = movNumbers.getOrDefault(ret.getId(), "");
            return new CustomerReturnResponse(
                    ret.getId(), ret.getReturnNumber(), order.getId(), item.getId(),
                    item.getLineNo(), item.getProductType(), ret.getReturnDate(),
                    ret.getReturnedWeightKg(), item.getRatePerKg(), credited,
                    ret.isUsable(), movNum, ret.getReason(), ret.getNotes(),
                    ret.getCreatedAt()
            );
        }).toList();
    }
}
