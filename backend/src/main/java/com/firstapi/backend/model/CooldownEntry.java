package com.firstapi.backend.model;

import java.time.Instant;

/**
 * Represents an in-memory cooldown state for a relay account.
 * Used by {@link com.firstapi.backend.service.RelayAccountSelector} to track
 * rate-limited or overloaded accounts with probe scheduling support.
 */
public class CooldownEntry {

    private final Instant until;
    private final Instant probeAt;
    private final String reason;
    private final String provider;
    private volatile boolean probing;

    public CooldownEntry(Instant until, Instant probeAt, String reason, String provider) {
        this.until = until;
        this.probeAt = probeAt;
        this.reason = reason;
        this.provider = provider;
        this.probing = false;
    }

    public Instant getUntil() {
        return until;
    }

    public Instant getProbeAt() {
        return probeAt;
    }

    public String getReason() {
        return reason;
    }

    public String getProvider() {
        return provider;
    }

    public boolean isProbing() {
        return probing;
    }

    /**
     * Atomically set probing to true if it was false.
     * @return true if this call acquired the probing lock
     */
    public boolean tryStartProbing() {
        synchronized (this) {
            if (probing) {
                return false;
            }
            probing = true;
            return true;
        }
    }

    public void finishProbing() {
        probing = false;
    }
}
