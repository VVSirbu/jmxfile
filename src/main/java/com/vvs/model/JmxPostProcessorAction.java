package com.vvs.model;

public record JmxPostProcessorAction(
        String sourceVariablePrefix,
        String targetVariableName,
        String defaultValue,
        boolean normalizeUrl,
        boolean extractProductId
) {
}
