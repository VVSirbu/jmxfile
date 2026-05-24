package com.vvs.model;

public record JmxRequestParameter(
        String name,
        String value,
        boolean alwaysEncode
) {
}
