package com.agentgate.policy.dto;

import com.agentgate.policy.entity.Effect;

import java.time.Instant;
import java.util.Map;

public record PolicyResponse(Long id, String tenantId, String agentId, String tool, Effect effect,
                             Map<String,Object> conditions, Instant createdAt) {
}
