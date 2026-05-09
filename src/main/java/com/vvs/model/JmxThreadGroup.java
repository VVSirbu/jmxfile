package com.vvs.model;

public record JmxThreadGroup(
        String name,
        int virtualUsers,
        int rampUpSeconds,
        int loops
) {
}
