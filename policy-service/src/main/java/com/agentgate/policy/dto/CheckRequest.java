package com.agentgate.policy.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckRequest(@NotBlank String tenantId, @NotBlank String agentId, @NotBlank String tool) {
}
