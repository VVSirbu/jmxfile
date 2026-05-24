package com.vvs.model;

import java.util.List;

public record JmxHttpRequest(
        String name,
        String method,
        String protocol,
        String domain,
        String port,
        String path,
        String body,
        List<JmxHeader> headers,
        List<JmxRequestParameter> parameters,
        List<JmxRegexExtractor> regexExtractors,
        List<JmxHtmlExtractor> htmlExtractors,
        List<JmxPostProcessorAction> postProcessorActions,
        boolean rawBody,
        String conditionExpression
) {
    public JmxHttpRequest(
            String name,
            String method,
            String protocol,
            String domain,
            String port,
            String path,
            String body,
            List<JmxHeader> headers,
            List<JmxRequestParameter> parameters,
            List<JmxRegexExtractor> regexExtractors,
            List<JmxHtmlExtractor> htmlExtractors,
            List<JmxPostProcessorAction> postProcessorActions,
            boolean rawBody
    ) {
        this(name, method, protocol, domain, port, path, body, headers, parameters, regexExtractors, htmlExtractors,
                postProcessorActions, rawBody, "");
    }

    public JmxHttpRequest(
            String name,
            String method,
            String protocol,
            String domain,
            String port,
            String path,
            String body,
            List<JmxHeader> headers
    ) {
        this(name, method, protocol, domain, port, path, body, headers, List.of(), List.of(), List.of(), List.of(), true, "");
    }

    public JmxHttpRequest {
        headers = List.copyOf(headers);
        parameters = List.copyOf(parameters);
        regexExtractors = List.copyOf(regexExtractors);
        htmlExtractors = List.copyOf(htmlExtractors);
        postProcessorActions = List.copyOf(postProcessorActions);
        conditionExpression = conditionExpression == null ? "" : conditionExpression;
    }
}
