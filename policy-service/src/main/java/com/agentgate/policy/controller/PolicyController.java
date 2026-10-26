package com.agentgate.policy.controller;

import com.agentgate.policy.dto.CheckRequest;
import com.agentgate.policy.dto.CheckResponse;
import com.agentgate.policy.dto.CreatePolicyRequest;
import com.agentgate.policy.dto.PolicyResponse;
import com.agentgate.policy.service.PolicyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyResponse createPolicy(@Valid @RequestBody CreatePolicyRequest request) {
        return policyService.create(request);
    }

    @GetMapping
    public List<PolicyResponse> listPolicies() {
        return policyService.list();
    }

    @PostMapping("/check")
    public CheckResponse checkPolicy(@Valid @RequestBody CheckRequest request) {
        return policyService.check(request);
    }
}
