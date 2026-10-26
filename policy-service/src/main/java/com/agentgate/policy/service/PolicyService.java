package com.agentgate.policy.service;

import com.agentgate.policy.dto.CheckRequest;
import com.agentgate.policy.dto.CheckResponse;
import com.agentgate.policy.dto.CreatePolicyRequest;
import com.agentgate.policy.dto.PolicyResponse;

import java.util.List;

public interface PolicyService {
    PolicyResponse create(CreatePolicyRequest request);
    List<PolicyResponse> list();
    CheckResponse check(CheckRequest request);
}
