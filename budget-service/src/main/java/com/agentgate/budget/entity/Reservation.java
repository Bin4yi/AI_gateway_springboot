package com.agentgate.budget.entity;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

    // Held budget is freed by the cleanup job if nobody finishes in time
    private static final Duration HOLD_TIME = Duration.ofMinutes(5);

    @Id
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String actionId;

    @Column(nullable = false)
    private long estimatedAmount;

    // Null until the action is committed and the real cost is known
    private Long actualCost;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected Reservation() {
        // Default constructor for JPA
    }

    // A new reservation always starts HELD, with no actual cost yet
    public Reservation(String tenantId, String actionId, long estimatedAmount) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.actionId = actionId;
        this.estimatedAmount = estimatedAmount;
        this.actualCost = null;
        this.status = ReservationStatus.HELD;
        this.createdAt = now;
        this.expiresAt = now.plus(HOLD_TIME);
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getActionId() {
        return actionId;
    }

    public long getEstimatedAmount() {
        return estimatedAmount;
    }

    public Long getActualCost() {
        return actualCost;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
