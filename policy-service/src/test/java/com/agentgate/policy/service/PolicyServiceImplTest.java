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
        givenRules();

        CheckResponse response = policyService.check(request);

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("no matching rule");
    }

    @Test
    void check_onlyAllowRule_isAllowed() {
        givenRules(rule(Effect.ALLOW));

        CheckResponse response = policyService.check(request);

        assertThat(response.allowed()).isTrue();
        assertThat(response.reason()).isEqualTo("allowed by policy");
    }

    @Test
    void check_onlyDenyRule_isDenied() {
        givenRules(rule(Effect.DENY));

        CheckResponse response = policyService.check(request);

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("denied by policy");
    }

    @Test
    void check_allowThenDeny_denyWins() {
        givenRules(rule(Effect.ALLOW), rule(Effect.DENY));

        CheckResponse response = policyService.check(request);

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("denied by policy");
    }

    @Test
    void check_denyThenAllow_denyWins() {
        givenRules(rule(Effect.DENY), rule(Effect.ALLOW));   // reversed order: same result

        CheckResponse response = policyService.check(request);

        assertThat(response.allowed()).isFalse();
        assertThat(response.reason()).isEqualTo("denied by policy");
    }

    // Arrange step in one line: "the repository finds these rules for our request"
    private void givenRules(Policy... rules) {
        when(policyRepository.findByTenantIdAndAgentIdAndTool(
                request.tenantId(), request.agentId(), request.tool()))
                .thenReturn(List.of(rules));
    }

    private Policy rule(Effect effect) {
        return new Policy(request.tenantId(), request.agentId(), request.tool(), effect, Map.of());
    }
}
