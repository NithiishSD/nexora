package com.nexora.parties.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// One phone number in a create/patch request. "primary" is optional (null = false).
public record PhoneRequest(
        @NotBlank @Pattern(regexp = "^[0-9+()\\- ]{6,30}$", message = "must be 6-30 digits, spaces, +, -, ( or )")
        String phoneNumber,
        @NotBlank @Size(max = 50) String label,
        Boolean primary) {
}
