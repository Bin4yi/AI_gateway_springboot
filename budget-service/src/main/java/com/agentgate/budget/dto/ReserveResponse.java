package com.agentgate.budget.dto;

import java.time.Instant;
import java.util.UUID;

public record ReserveResponse(UUID reservationId, String tenantId, String actionId, long amount,
                              String status, Instant expiresAt) {
}
