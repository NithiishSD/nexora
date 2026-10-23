package com.nexora.support;

import java.net.URI;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.nexora.parties.api.dto.CreatePartyRequest;
import com.nexora.parties.api.dto.PartyResponse;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.shared.error.FieldValidationException;
import com.nexora.shared.idempotency.Idempotent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

// Test-only endpoints for the cross-cutting error and security tests, used before (and independently of)
// any business endpoint. @TestComponent keeps it out of component scanning: it exists only in contexts
// that @Import it, so the production endpoint list (and EndpointSecurityCoverageTest) never sees it.
@TestComponent
@RestController
public class TestProbeController {

    public static final String BODY = "/api/v1/test-probe";
    public static final String PAYMENT_ONLY = "/api/v1/test-probe/payment-only";
    public static final String IDEMPOTENT = "/api/v1/test-probe/idempotent";

    private final PartyService partyService;

    public TestProbeController(PartyService partyService) {
        this.partyService = partyService;
    }

    // Accepts a JSON body: used for 400 / 405 / 415 tests.
    @PostMapping(BODY)
    @PreAuthorize("hasAuthority('PARTY_VIEW')")
    public String create(@Valid @RequestBody ProbeBody body) {
        return body.name();
    }

    // Needs a permission STAFF does not have: used for the 403 test.
    @GetMapping(PAYMENT_ONLY)
    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public String paymentOnly() {
        return "ok";
    }

    // An @Idempotent write for the idempotency tests: creates a customer (a real row with a PTY number), or fails
    // after creating it when the name is "fail" (proves the key and the use case roll back together).
    @PostMapping(IDEMPOTENT)
    @PreAuthorize("hasAuthority('PARTY_MANAGE')")
    @Idempotent
    public ResponseEntity<PartyResponse> idempotent(@Valid @RequestBody ProbeBody body) {
        PartyResponse created = partyService.create(new CreatePartyRequest(body.name(), List.of(PartyRole.CUSTOMER), null, null,
                null, null, null, null, null, List.of(), null, null, null));
        if (body.name().equals("fail")) {
            throw new FieldValidationException("name", "fail on purpose");
        }
        return ResponseEntity.created(URI.create("/api/v1/parties/" + created.id())).body(created);
    }

    public record ProbeBody(@NotBlank String name) {
    }
}
