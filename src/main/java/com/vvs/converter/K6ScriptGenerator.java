package com.vvs.converter;

import com.vvs.model.JmxHeader;
import com.vvs.model.JmxHttpRequest;
import com.vvs.model.JmxTestPlan;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class K6ScriptGenerator {

    public String generate(JmxTestPlan testPlan) {
        StringBuilder script = new StringBuilder();
        script.append("import http from 'k6/http';\n");
        script.append("import { sleep } from 'k6';\n\n");
        script.append("export const options = {\n");
        script.append("  vus: ").append(testPlan.threadGroup().virtualUsers()).append(",\n");
        script.append("  iterations: ").append(totalIterations(testPlan)).append(",\n");
        script.append("};\n\n");
        script.append("export default function () {\n");

        for (JmxHttpRequest request : testPlan.requests()) {
            appendRequest(script, request);
            script.append("  sleep(1);\n\n");
        }

        script.append("}\n");
        return script.toString();
    }

    private int totalIterations(JmxTestPlan testPlan) {
        int loops = Math.max(testPlan.threadGroup().loops(), 1);
        int requestCount = Math.max(testPlan.requests().size(), 1);
        return loops * requestCount;
    }

    private void appendRequest(StringBuilder script, JmxHttpRequest request) {
        String params = buildParams(request);
        String url = buildUrlExpression(request);
        String method = request.method().toLowerCase();
        String k6Method = toK6Method(method);

        script.append("  // ").append(escapeComment(request.name())).append("\n");
        if (requiresBody(method)) {
            script.append("  http.").append(k6Method).append("(")
                    .append(url).append(", ")
                    .append(toJsString(request.body())).append(", ")
                    .append(params)
                    .append(");\n");
        } else {
            script.append("  http.").append(k6Method).append("(")
                    .append(url).append(", ")
                    .append(params)
                    .append(");\n");
        }
    }

    private String buildParams(JmxHttpRequest request) {
        if (request.headers().isEmpty()) {
            return "{}";
        }

        String headers = request.headers().stream()
                .collect(Collectors.toMap(
                        JmxHeader::name,
                        JmxHeader::value,
                        (first, ignored) -> first
                ))
                .entrySet()
                .stream()
                .map(entry -> "      " + toJsString(entry.getKey()) + ": " + toJsString(entry.getValue()))
                .collect(Collectors.joining(",\n"));

        return "{\n    headers: {\n" + headers + "\n    }\n  }";
    }

    private String buildUrlExpression(JmxHttpRequest request) {
        String protocol = request.protocol().isBlank() ? "https" : request.protocol();
        String port = request.port().isBlank() ? "" : ":" + request.port();
        return toJsString(protocol + "://" + request.domain() + port + request.path());
    }

    private boolean requiresBody(String method) {
        return "post".equals(method) || "put".equals(method) || "patch".equals(method);
    }

    private String toK6Method(String method) {
        if ("delete".equals(method)) {
            return "del";
        }
        return method;
    }

    private String toJsString(String value) {
        String safeValue = value == null ? "" : value;
        return "'" + safeValue
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "'";
    }

    private String escapeComment(String value) {
        return value == null ? "" : value.replace("\r", " ").replace("\n", " ");
    }
}
