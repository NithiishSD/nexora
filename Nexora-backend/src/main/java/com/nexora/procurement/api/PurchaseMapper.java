package com.nexora.procurement.api;

import java.util.Map;
import java.util.UUID;

import com.nexora.parties.application.PartyRef;
import com.nexora.procurement.api.dto.PurchaseResponse;
import com.nexora.procurement.api.dto.PurchaseSummaryResponse;
import com.nexora.procurement.api.dto.ReceiptResponse;
import com.nexora.procurement.api.dto.SupplierReturnResponse;
import com.nexora.procurement.application.PurchasePayment;
import com.nexora.procurement.domain.MaterialReceipt;
import com.nexora.procurement.domain.Purchase;
import com.nexora.procurement.domain.SupplierReturn;

// Turns the purchase aggregate into API records. Called inside the service transaction (receipts and returns
// are lazy and open-in-view is off). The supplier and the movement numbers come from other modules' services
// and are passed in, so this class needs nothing but the data.
public final class PurchaseMapper {

    private PurchaseMapper() {
    }

    public static PurchaseResponse toResponse(Purchase p, PartyRef supplier, PurchasePayment pay,
                                              Map<UUID, String> receiptMovements, Map<UUID, String> returnMovements) {
        return new PurchaseResponse(p.getId(), p.getPurchaseNumber(), supplier, p.getProductType(),
                p.getPurchaseDate(), p.getOrderedWeightKg(), p.getRatePerKg(), p.orderedValue(), p.getPaymentType(),
                p.getCreditDays(), p.getDueDate(), p.getBillReference(), p.getStatus(), p.receivedWeightKg(),
                p.acceptedWeightKg(), p.rejectedWeightKg(), p.returnedWeightKg(), p.remainingWeightKg(),
                p.payableAmount(), pay.allocated(), pay.outstanding(), pay.paymentStatus(), pay.overdue(),
                pay.overdueDays(),
                p.getReceipts().stream().map(r -> toReceipt(r, receiptMovements.get(r.getId()))).toList(),
                p.getReturns().stream().map(r -> toReturn(r, returnMovements.get(r.getId()))).toList(),
                p.getNotes(), p.getCreatedAt(), p.getUpdatedAt(), p.getCreatedBy());
    }

    public static PurchaseSummaryResponse toSummary(Purchase p, PartyRef supplier, PurchasePayment pay) {
        return new PurchaseSummaryResponse(p.getId(), p.getPurchaseNumber(), supplier, p.getProductType(),
                p.getPurchaseDate(), p.getOrderedWeightKg(), p.receivedWeightKg(), p.getRatePerKg(), p.orderedValue(),
                p.getStatus(), pay.paymentStatus(), p.getDueDate(), pay.outstanding(), pay.overdue());
    }

    public static ReceiptResponse toReceipt(MaterialReceipt r, String movementNumber) {
        return new ReceiptResponse(r.getId(), r.getReceiptNumber(), r.getPurchase().getId(), r.getReceiptDate(),
                r.getReceivedWeightKg(), r.getAcceptedWeightKg(), r.getRejectedWeightKg(), movementNumber,
                r.getNotes(), r.getCreatedAt(), r.getUpdatedAt(), r.getCreatedBy());
    }

    public static SupplierReturnResponse toReturn(SupplierReturn r, String movementNumber) {
        return new SupplierReturnResponse(r.getId(), r.getReturnNumber(), r.getPurchase().getId(), r.getReturnDate(),
                r.getReturnedWeightKg(), r.getReason(), movementNumber, r.getNotes(), r.getCreatedAt(),
                r.getUpdatedAt(), r.getCreatedBy());
    }
}
