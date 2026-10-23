package com.nexora.inventory.domain;

import static com.nexora.inventory.domain.StockLocation.CUSTOMER;
import static com.nexora.inventory.domain.StockLocation.EXTERNAL_WIP;
import static com.nexora.inventory.domain.StockLocation.FINISHED_STOCK;
import static com.nexora.inventory.domain.StockLocation.INTERNAL_WIP;
import static com.nexora.inventory.domain.StockLocation.RAW_STOCK;
import static com.nexora.inventory.domain.StockLocation.RETURNED;
import static com.nexora.inventory.domain.StockLocation.SUPPLIER;
import static com.nexora.inventory.domain.StockLocation.WASTAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.FieldValidationException;

class MovementRulesTest {

    private static final BigDecimal KG = new BigDecimal("10.000");

    // The expected (from, to) pairs, written out independently of MovementRules (DOMAIN_RULES section 3).
    // "null" as source = no source (opening stock).
    private static final Map<MovementType, Set<String>> LEGAL = Map.ofEntries(
            Map.entry(MovementType.OPENING_BALANCE, Set.of("null>RAW_STOCK", "null>INTERNAL_WIP",
                    "null>EXTERNAL_WIP", "null>FINISHED_STOCK")),
            Map.entry(MovementType.RECEIPT, Set.of("SUPPLIER>RAW_STOCK")),
            Map.entry(MovementType.PURCHASE_RETURN, Set.of("RAW_STOCK>RETURNED")),
            Map.entry(MovementType.DIRECT_SALE, Set.of("RAW_STOCK>CUSTOMER")),
            Map.entry(MovementType.CONSUMPTION, Set.of("RAW_STOCK>INTERNAL_WIP")),
            Map.entry(MovementType.PRODUCTION_OUTPUT, Set.of("INTERNAL_WIP>FINISHED_STOCK")),
            Map.entry(MovementType.WASTAGE, Set.of("RAW_STOCK>WASTAGE", "INTERNAL_WIP>WASTAGE", "EXTERNAL_WIP>WASTAGE")),
            Map.entry(MovementType.OUTSOURCE_ISSUE, Set.of("RAW_STOCK>EXTERNAL_WIP")),
            Map.entry(MovementType.OUTSOURCE_RECEIPT, Set.of("EXTERNAL_WIP>FINISHED_STOCK")),
            Map.entry(MovementType.DELIVERY, Set.of("FINISHED_STOCK>CUSTOMER")),
            Map.entry(MovementType.CUSTOMER_RETURN, Set.of("CUSTOMER>FINISHED_STOCK", "CUSTOMER>RAW_STOCK",
                    "CUSTOMER>WASTAGE")));

    @Test
    @DisplayName("FR-INV-03 legal pairs match movement type table")
    void legalPairs_matchMovementTypeTable() {
        List<StockLocation> sources = new ArrayList<>(Arrays.asList(StockLocation.values()));
        sources.add(null);
        List<String> mismatches = new ArrayList<>();
        for (MovementType type : MovementType.values()) {
            for (StockLocation from : sources) {
                for (StockLocation to : StockLocation.values()) {
                    boolean expected = type == MovementType.ADJUSTMENT
                            // any -> any different place, with at least one internal side
                            ? from != null && from != to && (from.isInternal() || to.isInternal())
                            : LEGAL.get(type).contains(from + ">" + to);
                    boolean actual = isLegal(type, from, to);
                    if (expected != actual) {
                        mismatches.add(type + " " + from + ">" + to + " expected " + expected);
                    }
                }
            }
        }
        assertThat(mismatches).isEmpty();
        // FR-INV-03 AC2: an adjustment between two outside places is a 400 naming the field
        assertThatThrownBy(() -> MovementRules.validate(MovementType.ADJUSTMENT, ProductType.RAW_SILK,
                ProductType.RAW_SILK, SUPPLIER, CUSTOMER, KG, "count"))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.getField()).isEqualTo("toLocation");
                });
        // an internal location holds only its own products (plan decision R2)
        assertCode(() -> MovementRules.validate(MovementType.ADJUSTMENT, ProductType.SILK_WARP, ProductType.SILK_WARP,
                CUSTOMER, RAW_STOCK, KG, "count"), ErrorCode.INVALID_PRODUCT_FOR_OPERATION);
        assertCode(() -> MovementRules.validate(MovementType.RECEIPT, ProductType.KORA_WARP, ProductType.KORA_WARP,
                SUPPLIER, RAW_STOCK, KG, null), ErrorCode.INVALID_PRODUCT_FOR_OPERATION);
        // raw silk becomes warp only on consumption or outsourcing issue
        assertThatCode(() -> MovementRules.validate(MovementType.CONSUMPTION, ProductType.RAW_SILK,
                ProductType.KORA_WARP, RAW_STOCK, INTERNAL_WIP, KG, null)).doesNotThrowAnyException();
        assertCode(() -> MovementRules.validate(MovementType.DIRECT_SALE, ProductType.RAW_SILK, ProductType.SILK_WARP,
                RAW_STOCK, CUSTOMER, KG, null), ErrorCode.INVALID_PRODUCT_FOR_OPERATION);
        // an adjustment needs a reason
        assertThatThrownBy(() -> MovementRules.validate(MovementType.ADJUSTMENT, ProductType.RAW_SILK,
                ProductType.RAW_SILK, SUPPLIER, RAW_STOCK, KG, "  "))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.getField()).isEqualTo("reason"));
    }

    @Test
    @DisplayName("FR-INV-10 vuda warp in consumption issue or output is rejected")
    void vudaWarp_inConsumptionIssueOrOutput_isRejected() {
        ProductType vuda = ProductType.VUDA_WARP;
        assertCode(() -> MovementRules.validate(MovementType.CONSUMPTION, vuda, vuda, RAW_STOCK, INTERNAL_WIP, KG, null),
                ErrorCode.INVALID_PRODUCT_FOR_OPERATION);
        assertCode(() -> MovementRules.validate(MovementType.OUTSOURCE_ISSUE, vuda, vuda, RAW_STOCK, EXTERNAL_WIP, KG, null),
                ErrorCode.INVALID_PRODUCT_FOR_OPERATION);
        assertCode(() -> MovementRules.validate(MovementType.OUTSOURCE_ISSUE, ProductType.RAW_SILK, vuda,
                RAW_STOCK, EXTERNAL_WIP, KG, null), ErrorCode.INVALID_PRODUCT_FOR_OPERATION);
        assertCode(() -> MovementRules.validate(MovementType.PRODUCTION_OUTPUT, vuda, vuda,
                INTERNAL_WIP, FINISHED_STOCK, KG, null), ErrorCode.INVALID_PRODUCT_FOR_OPERATION);
        assertCode(() -> MovementRules.validate(MovementType.OUTSOURCE_RECEIPT, vuda, vuda,
                EXTERNAL_WIP, FINISHED_STOCK, KG, null), ErrorCode.INVALID_PRODUCT_FOR_OPERATION);
        // what vuda warp may do: be received, sold, wasted and adjusted in raw stock (FR-INV-10 description)
        for (MovementType ok : List.of(MovementType.RECEIPT, MovementType.DIRECT_SALE, MovementType.WASTAGE,
                MovementType.PURCHASE_RETURN)) {
            StockLocation from = ok == MovementType.RECEIPT ? SUPPLIER : RAW_STOCK;
            StockLocation to = switch (ok) {
                case RECEIPT -> RAW_STOCK;
                case DIRECT_SALE -> CUSTOMER;
                case WASTAGE -> WASTAGE;
                default -> RETURNED;
            };
            assertThatCode(() -> MovementRules.validate(ok, vuda, vuda, from, to, KG, null)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("FR-INV-15 quantity with four decimals is rejected")
    void quantityWithFourDecimals_isRejected() {
        assertThatThrownBy(() -> receipt(new BigDecimal("12.3456")))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        e -> assertThat(e.getField()).isEqualTo("quantityKg"));
        // three decimals (also written with a trailing zero) are fine and kept exactly
        assertThatCode(() -> receipt(new BigDecimal("12.345"))).doesNotThrowAnyException();
        assertThatCode(() -> receipt(new BigDecimal("12.3450"))).doesNotThrowAnyException();
        // zero and negative quantities never become movements (FR-INV-02 AC2)
        assertThatThrownBy(() -> receipt(BigDecimal.ZERO)).isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> receipt(new BigDecimal("-1"))).isInstanceOf(FieldValidationException.class);
    }

    @Test
    @DisplayName("FR-INV-17 customer return pair is defined for later use")
    void customerReturnPair_isDefinedForLaterUse() {
        // usable warp goes back to finished stock, raw silk or vuda back to raw stock
        assertThatCode(() -> MovementRules.validate(MovementType.CUSTOMER_RETURN, ProductType.SILK_WARP,
                ProductType.SILK_WARP, CUSTOMER, FINISHED_STOCK, KG, null)).doesNotThrowAnyException();
        assertThatCode(() -> MovementRules.validate(MovementType.CUSTOMER_RETURN, ProductType.RAW_SILK,
                ProductType.RAW_SILK, CUSTOMER, RAW_STOCK, KG, null)).doesNotThrowAnyException();
        // but never warp into raw stock
        assertCode(() -> MovementRules.validate(MovementType.CUSTOMER_RETURN, ProductType.SILK_WARP,
                ProductType.SILK_WARP, CUSTOMER, RAW_STOCK, KG, null), ErrorCode.INVALID_PRODUCT_FOR_OPERATION);
    }

    private static void receipt(BigDecimal kg) {
        MovementRules.validate(MovementType.RECEIPT, ProductType.RAW_SILK, ProductType.RAW_SILK, SUPPLIER, RAW_STOCK,
                kg, null);
    }

    private static boolean isLegal(MovementType type, StockLocation from, StockLocation to) {
        try {
            MovementRules.requireLegalPair(type, from, to);
            return true;
        } catch (FieldValidationException e) {
            return false;
        }
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
