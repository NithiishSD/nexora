package com.nexora.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.transaction.support.TransactionTemplate;

import com.nexora.inventory.application.InventoryService;
import com.nexora.inventory.application.MovementCommand;
import com.nexora.inventory.application.MovementResult;
import com.nexora.inventory.domain.MovementType;
import com.nexora.inventory.domain.ProductType;
import com.nexora.inventory.domain.StockLocation;

import com.nexora.parties.api.dto.CreatePartyRequest;
import com.nexora.parties.api.dto.PartyResponse;
import com.nexora.parties.api.dto.PhoneRequest;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;

// Builds valid records through the application services ("through the front door"), so every rule and
// ledger invariant holds exactly as in production. Names get a short random suffix to stay unique.
public class TestDataFactory {

    private final PartyService partyService;
    private final InventoryService inventoryService;
    private final TransactionTemplate tx;

    public TestDataFactory(PartyService partyService) {
        this(partyService, null, null);
    }

    public TestDataFactory(PartyService partyService, InventoryService inventoryService, TransactionTemplate tx) {
        this.partyService = partyService;
        this.inventoryService = inventoryService;
        this.tx = tx;
    }

    // Stock that existed at go-live, recorded as an OPENING_BALANCE movement (the movement keeps the ledger
    // and the balance in step, exactly as in production).
    public MovementResult openingStock(ProductType product, StockLocation location, String kg) {
        return tx.execute(s -> inventoryService.recordMovement(new MovementCommand(product, null,
                MovementType.OPENING_BALANCE, null, location, new BigDecimal(kg), LocalDate.of(2026, 1, 1),
                null, null, null, null, null)));
    }

    public MovementResult rawSilkInStock(String kg) {
        return openingStock(ProductType.RAW_SILK, StockLocation.RAW_STOCK, kg);
    }

    public PartyResponse customer() {
        return party("Customer", List.of(PartyRole.CUSTOMER), null, null);
    }

    public PartyResponse supplier() {
        return party("Supplier", List.of(PartyRole.SUPPLIER), 10, null);
    }

    public PartyResponse manufacturer() {
        return party("Manufacturer", List.of(PartyRole.MANUFACTURER), null, null);
    }

    public PartyResponse worker() {
        return party("Worker", List.of(PartyRole.WORKER), null, new BigDecimal("100.00"));
    }

    public PartyResponse lender() {
        return party("Lender", List.of(PartyRole.LENDER), null, null);
    }

    public PartyResponse party(String name, List<PartyRole> roles, Integer creditDays, BigDecimal rollingRate) {
        return partyService.create(new CreatePartyRequest(name + " " + suffix(), roles, null, null, "Salem", null, null,
                null, null, List.of(new PhoneRequest("+91 94430 11111", "mobile", true)), creditDays, rollingRate, null));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
