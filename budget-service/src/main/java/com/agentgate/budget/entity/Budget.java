package com.agentgate.budget.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    private String tenantId;

    @Column(nullable = false)
    private long totalLimit;

    @Column(nullable = false)
    private long used;

    @Column(nullable = false)
    private long reserved;

    @Version   // Hibernate adds 1 on every update and refuses a stale update
    @Column(nullable = false)
    private long version;

    protected Budget() {
        // Default constructor for JPA
    }

    // A new budget starts with nothing used or reserved. Hibernate manages version.
    public Budget(String tenantId, long totalLimit) {
        this.tenantId = tenantId;
        this.totalLimit = totalLimit;
        this.used = 0;
        this.reserved = 0;
    }

    public String getTenantId() {
        return tenantId;
    }

    public long getTotalLimit() {
        return totalLimit;
    }

    public long getUsed() {
        return used;
    }

    public long getReserved() {
        return reserved;
    }

    public long getVersion() {
        return version;
    }
}
