package com.nexora.orders.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.documents.api.dto.DocumentResponse;
import com.nexora.documents.application.DocumentService;
import com.nexora.documents.domain.DocumentReferenceType;
import com.nexora.documents.domain.DocumentType;
import com.nexora.orders.domain.CustomerOrder;
import com.nexora.orders.domain.OrderStatus;
import com.nexora.orders.infrastructure.CustomerOrderRepository;
import com.nexora.parties.api.dto.PartyResponse;
import com.nexora.parties.application.PartyService;
import com.nexora.shared.api.PageResponse;
import com.nexora.shared.error.InvalidStateTransitionException;
import com.nexora.shared.error.ResourceNotFoundException;

// E-ORD-12: Generate the sales bill for a delivered order (FR-DOC-06, FR-ORD-19).
@Service
public class OrderBillService {

    private static final Logger log = LoggerFactory.getLogger(OrderBillService.class);
    private static final Set<OrderStatus> DELIVERED_STATUSES = Set.of(OrderStatus.DELIVERED, OrderStatus.COMPLETED);

    private final CustomerOrderRepository orders;
    private final PartyService parties;
    private final DocumentService documents;
    private final Clock clock;

    public OrderBillService(CustomerOrderRepository orders, PartyService parties, DocumentService documents, Clock clock) {
        this.orders = orders;
        this.parties = parties;
        this.documents = documents;
        this.clock = clock;
    }

    @Transactional
    public DocumentResponse generateBill(UUID orderId) {
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (!DELIVERED_STATUSES.contains(order.getStatus())) {
            throw new InvalidStateTransitionException("Order " + order.getOrderNumber(), order.getStatus(), "BILLED");
        }

        PartyResponse customer = parties.get(order.getCustomerId());
        LocalDate today = LocalDate.now(clock);

        // Check if a sales bill already exists for this order; if so, new bill supersedes it
        UUID previousBillId = null;
        PageResponse<DocumentResponse> existing = documents.list(
                DocumentReferenceType.ORDER, orderId, DocumentType.SALES_BILL, null, null,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")));
        if (!existing.content().isEmpty()) {
            previousBillId = existing.content().getFirst().id();
        }

        byte[] pdfBytes = PdfBillGenerator.generateSalesBill(order, customer, today);
        String fileName = "bill-" + order.getOrderNumber().toLowerCase() + ".pdf";

        DocumentResponse doc = documents.upload(pdfBytes, fileName, DocumentType.SALES_BILL,
                DocumentReferenceType.ORDER, order.getId(), previousBillId);

        log.info("event=sales_bill_generated order={} documentId={} supersedes={}",
                order.getOrderNumber(), doc.id(), previousBillId);

        return doc;
    }
}
