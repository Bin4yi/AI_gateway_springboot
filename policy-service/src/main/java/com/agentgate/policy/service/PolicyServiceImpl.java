package com.agentgate.policy.service;

import com.agentgate.policy.dto.CheckRequest;
import com.agentgate.policy.dto.CheckResponse;
import com.agentgate.policy.dto.CreatePolicyRequest;
import com.agentgate.policy.dto.PolicyResponse;
import com.agentgate.policy.entity.Effect;
import com.agentgate.policy.entity.Policy;
import com.agentgate.policy.repository.PolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PolicyServiceImpl implements PolicyService {

    private final PolicyRepository policyRepository;

    public PolicyServiceImpl(PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    @Override
    @Transactional
    public PolicyResponse create(CreatePolicyRequest request) {
        Policy policy = new Policy(request.tenantId(), request.agentId(), request.tool(),
                request.effect(), request.conditions());
        return toResponse(policyRepository.save(policy));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyResponse> list() {
        return policyRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckResponse check(CheckRequest request) {
        List<Policy> policies = policyRepository.findByTenantIdAndAgentIdAndTool(
                request.tenantId(), request.agentId(), request.tool());

        // A DENY always wins, even if an ALLOW rule also matches
        boolean denied = policies.stream().anyMatch(p -> p.getEffect() == Effect.DENY);
        if (denied) {
            return new CheckResponse(false, "denied by policy");
        }
        boolean allowed = policies.stream().anyMatch(p -> p.getEffect() == Effect.ALLOW);
        if (allowed) {
            return new CheckResponse(true, "allowed by policy");
        }
        // Default deny: no matching rule means no access
        return new CheckResponse(false, "no matching rule");
    }

    // The entity never leaves the service layer
    private PolicyResponse toResponse(Policy policy) {
        return new PolicyResponse(policy.getId(), policy.getTenantId(), policy.getAgentId(), policy.getTool(),
                policy.getEffect(), policy.getConditions(), policy.getCreatedAt());
    }
}
