package com.vvs.model;

public record JmxHtmlExtractor(
        String variableName,
        String cssSelector,
        String attribute,
        String defaultValue,
        int matchNumber
) {
}
