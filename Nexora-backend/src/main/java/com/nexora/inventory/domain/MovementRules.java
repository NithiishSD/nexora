package com.nexora.inventory.domain;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

import com.nexora.shared.domain.Weight;
import com.nexora.shared.error.FieldValidationException;
import com.nexora.shared.error.InvalidProductForOperationException;

// The pure rules every movement must satisfy before it touches the database (DOMAIN_RULES section 3,
// 04-DATA-MODEL section 4.2). No Spring, no database: a plain unit test proves the whole table.
// The same rules exist as CHECK constraints in V6; the database is the last line of defence, this class
// gives the client a clear error code instead of a 500.
public final class MovementRules {

    private static final Set<StockLocation> WASTAGE_SOURCES =
            EnumSet.of(StockLocation.RAW_STOCK, StockLocation.INTERNAL_WIP, StockLocation.EXTERNAL_WIP);

    // Movements that make or turn material into warp: vuda warp may never take part (trade-only).
    private static final Set<MovementType> PRODUCTION_TYPES = EnumSet.of(MovementType.CONSUMPTION,
            MovementType.OUTSOURCE_ISSUE, MovementType.OUTSOURCE_RECEIPT, MovementType.PRODUCTION_OUTPUT);

    // The only movements where raw silk arrives as a warp type (work in progress is tracked as the warp being made).
    private static final Set<MovementType> CONVERSIONS =
            EnumSet.of(MovementType.CONSUMPTION, MovementType.OUTSOURCE_ISSUE);

    private MovementRules() {
    }

    // Throws when the movement is illegal:
    //  - 400 VALIDATION_FAILED (FieldValidationException) for a bad quantity, an illegal (from, to) pair or a missing reason;
    //  - 422 INVALID_PRODUCT_FOR_OPERATION for a product that may not take part (vuda warp, wrong product for a location).
    public static void validate(MovementType type, ProductType product, ProductType toProduct,
                                StockLocation from, StockLocation to, BigDecimal quantityKg, String reason) {
        requireQuantity(quantityKg);
        requireLegalPair(type, from, to);
        requireLegalProducts(type, product, toProduct, from, to);
        if (type == MovementType.ADJUSTMENT && (reason == null || reason.isBlank())) {
            throw new FieldValidationException("reason", "An adjustment needs a reason.");
        }
    }

    // Quantity > 0 with at most 3 decimals (FR-INV-15). More decimals are rejected, never silently rounded.
    static void requireQuantity(BigDecimal quantityKg) {
        if (quantityKg == null || !Weight.isPositive(quantityKg)) {
            throw new FieldValidationException("quantityKg", "Quantity must be greater than 0 kg.");
        }
        // stripTrailingZeros: 12.3450 is really 3 decimals; 12.3456 is 4 and is rejected
        if (quantityKg.stripTrailingZeros().scale() > Weight.SCALE) {
            throw new FieldValidationException("quantityKg", "Quantity can have at most 3 decimals.");
        }
    }

    // The (type, from, to) table of DOMAIN_RULES section 3.
    static void requireLegalPair(MovementType type, StockLocation from, StockLocation to) {
        if (to == null) {
            throw new FieldValidationException("toLocation", "A movement needs a destination.");
        }
        if (from == to) {
            throw new FieldValidationException("toLocation", "Source and destination must be different.");
        }
        boolean fromOk = switch (type) {
            case OPENING_BALANCE -> from == null;                     // stock that existed before go-live
            case RECEIPT -> from == StockLocation.SUPPLIER;
            case PURCHASE_RETURN, DIRECT_SALE, CONSUMPTION, OUTSOURCE_ISSUE -> from == StockLocation.RAW_STOCK;
            case PRODUCTION_OUTPUT -> from == StockLocation.INTERNAL_WIP;
            case WASTAGE -> WASTAGE_SOURCES.contains(from);
            case OUTSOURCE_RECEIPT -> from == StockLocation.EXTERNAL_WIP;
            case DELIVERY -> from == StockLocation.FINISHED_STOCK;
            case CUSTOMER_RETURN -> from == StockLocation.CUSTOMER;
            case ADJUSTMENT -> from != null;
        };
        if (!fromOk) {
            throw new FieldValidationException("fromLocation", type + " cannot start from " + from + ".");
        }
        boolean toOk = switch (type) {
            case OPENING_BALANCE -> to.isInternal();
            case RECEIPT -> to == StockLocation.RAW_STOCK;
            case CONSUMPTION -> to == StockLocation.INTERNAL_WIP;
            case PURCHASE_RETURN -> to == StockLocation.RETURNED;
            case DIRECT_SALE, DELIVERY -> to == StockLocation.CUSTOMER;
            case PRODUCTION_OUTPUT, OUTSOURCE_RECEIPT -> to == StockLocation.FINISHED_STOCK;
            case WASTAGE -> to == StockLocation.WASTAGE;
            case OUTSOURCE_ISSUE -> to == StockLocation.EXTERNAL_WIP;
            case CUSTOMER_RETURN -> to == StockLocation.FINISHED_STOCK || to == StockLocation.RAW_STOCK
                    || to == StockLocation.WASTAGE;
            // an adjustment between two outside places would change no stock at all (FR-INV-03 AC2)
            case ADJUSTMENT -> from.isInternal() || to.isInternal();
        };
        if (!toOk) {
            throw new FieldValidationException("toLocation", type + " cannot go from " + from + " to " + to + ".");
        }
    }

    // Which products may take part (vuda trade-only, conversions, location/product table).
    static void requireLegalProducts(MovementType type, ProductType product, ProductType toProduct,
                                     StockLocation from, StockLocation to) {
        if (product == null || toProduct == null) {
            throw new FieldValidationException("productType", "A movement needs a product type.");
        }
        if (PRODUCTION_TYPES.contains(type)
                && (product == ProductType.VUDA_WARP || toProduct == ProductType.VUDA_WARP)) {
            throw new InvalidProductForOperationException("Vuda warp is trade-only and cannot be used in " + type + ".");
        }
        if (product != toProduct) {
            // only raw silk entering work in progress becomes a (silk or kora) warp
            boolean conversion = CONVERSIONS.contains(type) && product == ProductType.RAW_SILK
                    && toProduct.isWarpMadeHere();
            if (!conversion) {
                throw new InvalidProductForOperationException(
                        type + " cannot turn " + product + " into " + toProduct + ".");
            }
        }
        if (from != null && !from.holds(product)) {
            throw new InvalidProductForOperationException(from + " does not hold " + product + ".");
        }
        if (!to.holds(toProduct)) {
            throw new InvalidProductForOperationException(to + " does not hold " + toProduct + ".");
        }
    }
}
