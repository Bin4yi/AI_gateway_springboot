package com.agentgate.policy.service;

import com.agentgate.policy.dto.CheckRequest;
import com.agentgate.policy.dto.CheckResponse;
import com.agentgate.policy.entity.Effect;
import com.agentgate.policy.entity.Policy;
import com.agentgate.policy.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyServiceImplTest {

    @Mock
    private PolicyRepository policyRepository;

    private PolicyServiceImpl policyService;

    private final CheckRequest request = new CheckRequest("acme", "support-bot", "send_email");

    @BeforeEach
    void setUp() {
        policyService = new PolicyServiceImpl(policyRepository);
    }

    @Test
    void check_noRules_isDeniedByDefault() {
        // Arrange: the repository finds no rules
        when(policyRepository.findByTenantIdAndAgentIdAndTool("acme", "support-bot", "send_email"))
                .thenReturn(List.of());

        // Act
        CheckResponse response = policyService.check(request);

        // Assert
        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("no matching rule");
    }

    @Test
    void check_onlyAllowRule_isAllowed() {
        // Arrange: the repository finds one ALLOW rule
        when(policyRepository.findByTenantIdAndAgentIdAndTool("acme", "support-bot", "send_email"))
                .thenReturn(List.of(rule(Effect.ALLOW)));

        // Act
        CheckResponse response = policyService.check(request);

        // Assert
        assertThat(response.allowed()).isTrue();
        assertThat(response.reason()).isEqualTo("allowed by policy");
    }

    @Test
    void check_onlyDenyRule_isDenied() {
        // Arrange: the repository finds one DENY rule
        when(policyRepository.findByTenantIdAndAgentIdAndTool("acme", "support-bot", "send_email"))
                .thenReturn(List.of(rule(Effect.DENY)));

        // Act
        CheckResponse response = policyService.check(request);

        // Assert
        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("denied by policy");
    }

    @Test
    void check_allowThenDeny_denyWins() {
        // Arrange: the repository finds an ALLOW rule first, then a DENY rule
        when(policyRepository.findByTenantIdAndAgentIdAndTool("acme", "support-bot", "send_email"))
                .thenReturn(List.of(rule(Effect.ALLOW), rule(Effect.DENY)));

        // Act
        CheckResponse response = policyService.check(request);

        // Assert
        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("denied by policy");
    }

    @Test
    void check_denyThenAllow_denyWins() {
        // Arrange: same two rules, reversed order. The result must be the same.
        when(policyRepository.findByTenantIdAndAgentIdAndTool("acme", "support-bot", "send_email"))
                .thenReturn(List.of(rule(Effect.DENY), rule(Effect.ALLOW)));

        // Act
        CheckResponse response = policyService.check(request);

        // Assert
        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("denied by policy");
    }

    // Builds one rule for our tenant, agent and tool
    private Policy rule(Effect effect) {
        return new Policy("acme", "support-bot", "send_email", effect, Map.of());
    }
}
