package com.nexora.finance.api.dto;

import java.util.UUID;

public record ExpenseCategoryResponse(UUID id, String name, boolean active) {
}
