package com.nexora.finance.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.finance.api.dto.OutstandingResponse;
import com.nexora.finance.application.CustomerOutstandingQuery;

// E-PAY-06, read only. PAYMENT_VIEW per 05-API-SPEC section 22 #20.
@RestController
@RequestMapping("/api/v1/customers")
class CustomerOutstandingController {

    private final CustomerOutstandingQuery outstanding;

    CustomerOutstandingController(CustomerOutstandingQuery outstanding) {
        this.outstanding = outstanding;
    }

    @GetMapping("/{id}/outstanding")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    OutstandingResponse get(@PathVariable UUID id) {
        return outstanding.forCustomer(id);
    }
}
