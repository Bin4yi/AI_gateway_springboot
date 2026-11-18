package com.agentgate.budget.dto;

// For GET /budgets/{tenantId}. available = totalLimit - used - reserved
public record BudgetResponse(String tenantId, long totalLimit, long used, long reserved, long available) {
}
