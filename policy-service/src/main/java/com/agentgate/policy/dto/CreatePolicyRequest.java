package com.agentgate.policy.dto;

import com.agentgate.policy.entity.Effect;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreatePolicyRequest(@NotBlank String tenantId, @NotBlank String agentId, @NotBlank String tool,
                                  @NotNull Effect effect, Map<String, Object> conditions) {
}
