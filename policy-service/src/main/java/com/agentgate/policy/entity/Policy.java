package com.agentgate.policy.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "policies")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String agentId;

    @Column(nullable = false)
    private String tool;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Effect effect;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> conditions;

    @Column(nullable = false)
    private Instant createdAt;

    protected Policy() {
        // Default constructor for JPA
    }

    public Policy(String tenantId, String agentId, String tool, Effect effect, Map<String, Object> conditions) {
        this.tenantId = tenantId;
        this.agentId = agentId;
        this.tool = tool;
        this.effect = effect;
        this.conditions = (conditions!= null) ? conditions : Map.of();
        this.createdAt = Instant.now();
    }

    public String getTenantId() {
        return tenantId;
    }

    public Long getId() {
        return id;
    }

    public String getAgentId() {
        return agentId;
    }

    public Effect getEffect() {
        return effect;
    }

    public String getTool() {
        return tool;
    }

    public Map<String, Object> getConditions() {
        return conditions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
