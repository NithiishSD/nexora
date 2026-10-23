package com.nexora.inventory.application;

import java.util.UUID;

// What the caller gets back after a movement: its id and business number (MOV-2026-0001).
public record MovementResult(UUID id, String movementNumber) {
}
