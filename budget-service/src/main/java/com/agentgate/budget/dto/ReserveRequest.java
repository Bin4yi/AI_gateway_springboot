package com.agentgate.budget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

// The Idempotency-Key comes as an HTTP header, not in this body
public record ReserveRequest(@NotBlank String actionId, @Positive long amount) {
}
