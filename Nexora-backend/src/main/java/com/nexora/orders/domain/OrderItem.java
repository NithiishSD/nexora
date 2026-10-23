package com.nexora.orders.domain;

import java.math.BigDecimal;

import com.nexora.inventory.domain.ProductType;
import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.domain.Money;
import com.nexora.shared.domain.Weight;
import com.nexora.shared.error.FieldValidationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// One line of a customer order. The rate is the agreed historical rate and the amount is frozen when the line is
// written (FR-ORD-02/03): later price changes never touch it. Created and changed only through CustomerOrder.
@Entity
@Table(name = "order_items")
public class OrderItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private CustomerOrder order;

    @Column(name = "line_no", nullable = false, updatable = false)
    private int lineNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType;

    @Column(name = "required_weight_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal requiredWeightKg;

    @Column(name = "rate_per_kg", nullable = false, precision = 14, scale = 2)
    private BigDecimal ratePerKg;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "warp_specification", columnDefinition = "text")
    private String warpSpecification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderItemStatus status;

    @Column(columnDefinition = "text")
    private String notes;

    protected OrderItem() {
        // for JPA only
    }

    // Package-private: only CustomerOrder adds lines (it numbers them and checks its own state first).
    static OrderItem of(CustomerOrder order, int lineNo, OrderLine line) {
        OrderItem item = new OrderItem();
        item.order = order;
        item.lineNo = lineNo;
        item.status = OrderItemStatus.PENDING;
        item.apply(line);
        return item;
    }

    // ASSUMPTION Q5 (confirm with the owner): paise are kept; amount = round(weight x rate, 2, HALF_UP).
    public static BigDecimal amountOf(BigDecimal requiredWeightKg, BigDecimal ratePerKg) {
        return Money.round(requiredWeightKg.multiply(ratePerKg));
    }

    // Writes the requested values (create, or an edit before the order is locked) and recomputes the amount.
    void apply(OrderLine line) {
        if (line.productType() == null) {
            throw new FieldValidationException("items[" + (lineNo - 1) + "].productType", "Product type is required.");
        }
        BigDecimal kg = line.requiredWeightKg();
        if (kg == null || kg.signum() <= 0 || kg.stripTrailingZeros().scale() > Weight.SCALE) {
            throw new FieldValidationException("items[" + (lineNo - 1) + "].requiredWeightKg",
                    "Weight must be greater than 0 with at most 3 decimals.");
        }
        BigDecimal rate = line.ratePerKg();
        if (rate == null || rate.signum() < 0 || rate.stripTrailingZeros().scale() > Money.SCALE) {
            throw new FieldValidationException("items[" + (lineNo - 1) + "].ratePerKg",
                    "Rate must be 0.00 or more with at most 2 decimals.");
        }
        productType = line.productType();
        requiredWeightKg = Weight.round(kg);
        ratePerKg = Money.round(rate);
        amount = amountOf(requiredWeightKg, ratePerKg);
        warpSpecification = line.warpSpecification();
        notes = line.notes();
    }

    // Status follows the order (ready, delivered, cancelled); delivered and cancelled lines stay as they are.
    void moveTo(OrderItemStatus next) {
        if (status != OrderItemStatus.CANCELLED && status != OrderItemStatus.DELIVERED) {
            status = next;
        }
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public int getLineNo() {
        return lineNo;
    }

    public ProductType getProductType() {
        return productType;
    }

    public BigDecimal getRequiredWeightKg() {
        return requiredWeightKg;
    }

    public BigDecimal getRatePerKg() {
        return ratePerKg;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getWarpSpecification() {
        return warpSpecification;
    }

    public OrderItemStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }
}
