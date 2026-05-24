package com.vvs.model;

public record JmxRegexExtractor(
        String variableName,
        String regex,
        String defaultValue,
        int matchNumber,
        boolean useHeaders
) {
}
