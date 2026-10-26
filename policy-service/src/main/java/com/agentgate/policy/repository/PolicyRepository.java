package com.agentgate.policy.repository;

import com.agentgate.policy.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    List<Policy> findByTenantIdAndAgentIdAndTool(String tenantId, String agentId, String tool);
}
