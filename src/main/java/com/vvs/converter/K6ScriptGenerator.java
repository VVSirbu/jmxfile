package com.vvs.converter;

import com.vvs.model.JmxCsvDataSet;
import com.vvs.model.JmxHeader;
import com.vvs.model.JmxHtmlExtractor;
import com.vvs.model.JmxHttpRequest;
import com.vvs.model.JmxPostProcessorAction;
import com.vvs.model.JmxRegexExtractor;
import com.vvs.model.JmxRequestParameter;
import com.vvs.model.JmxTestPlan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class K6ScriptGenerator {

    public String generate(JmxTestPlan testPlan) {
        StringBuilder script = new StringBuilder();
        script.append("import http from 'k6/http';\n");
        script.append("import { sleep } from 'k6';\n\n");
        if (!testPlan.csvDataSets().isEmpty()) {
            script.append("import { SharedArray } from 'k6/data';\n\n");
        }
        appendVariables(script, testPlan);
        appendCsvDataSets(script, testPlan);
        appendResolveHelper(script);
        script.append("export const options = {\n");
        script.append("  vus: ").append(testPlan.threadGroup().virtualUsers()).append(",\n");
        script.append("  iterations: ").append(totalIterations(testPlan)).append(",\n");
        script.append("};\n\n");
        script.append("export default function () {\n");
        appendCsvVariableAssignments(script, testPlan);

        for (int i = 0; i < testPlan.requests().size(); i++) {
            appendRequest(script, testPlan.requests().get(i), i + 1);
            script.append("\n");
        }

        script.append("}\n");
        return script.toString();
    }

    private int totalIterations(JmxTestPlan testPlan) {
        int loops = Math.max(testPlan.threadGroup().loops(), 1);
        return loops;
    }

    private void appendRequest(StringBuilder script, JmxHttpRequest request, int requestIndex) {
        String params = buildParams(request);
        String url = buildUrlExpression(request);
        String method = request.method().toLowerCase();
        String k6Method = toK6Method(method);
        String responseVariable = responseVariableName(request, requestIndex);
        boolean captureResponse = !request.regexExtractors().isEmpty()
                || !request.htmlExtractors().isEmpty()
                || !request.postProcessorActions().isEmpty();
        String indent = "  ";
        if (!request.conditionExpression().isBlank()) {
            script.append("  if (").append(request.conditionExpression()).append(") {\n");
            indent = "    ";
        }

        script.append(indent).append("// ").append(escapeComment(request.name())).append("\n");
        if (captureResponse) {
            script.append(indent).append("const ").append(responseVariable).append(" = ");
        } else {
            script.append(indent);
        }
        if (requiresBody(method)) {
            script.append("http.").append(k6Method).append("(")
                    .append(url).append(", ")
                    .append(buildBodyExpression(request)).append(", ")
                    .append(params)
                    .append(");\n");
        } else {
            script.append("http.").append(k6Method).append("(")
                    .append(url).append(", ")
                    .append(params)
                    .append(");\n");
        }
        appendRegexExtractors(script, request, responseVariable, indent);
        appendHtmlExtractors(script, request, responseVariable, indent);
        appendPostProcessorActions(script, request, responseVariable, indent);
        script.append(indent).append("sleep(1);\n");
        if (!request.conditionExpression().isBlank()) {
            script.append("  }\n");
        }
    }

    private void appendVariables(StringBuilder script, JmxTestPlan testPlan) {
        script.append("const variables = {\n");
        for (Map.Entry<String, String> entry : testPlan.variables().entrySet()) {
            script.append("  ")
                    .append(toObjectKey(entry.getKey()))
                    .append(": ")
                    .append(envAccess(entry.getKey()))
                    .append(" || ")
                    .append(toJsString(entry.getValue()))
                    .append(",\n");
        }
        script.append("};\n\n");
    }

    private void appendCsvDataSets(StringBuilder script, JmxTestPlan testPlan) {
        for (int i = 0; i < testPlan.csvDataSets().size(); i++) {
            JmxCsvDataSet csvDataSet = testPlan.csvDataSets().get(i);
            String dataVariable = csvDataVariableName(i);
            script.append("const ").append(dataVariable).append(" = new SharedArray(")
                    .append(toJsString(csvDataSet.name()))
                    .append(", function () {\n");
            script.append("  const rows = open(").append(toJsString(toK6FilePath(csvDataSet.filename()))).append(").trim().split(/\\r?\\n/);\n");
            script.append("  const dataRows = ").append(csvDataSet.ignoreFirstLine() ? "rows.slice(1)" : "rows").append(";\n");
            script.append("  return dataRows.filter((row) => row.trim().length > 0).map((row) => parseCsvLine(row, ")
                    .append(toJsString(csvDataSet.delimiter()))
                    .append("));\n");
            script.append("});\n\n");
        }

        if (!testPlan.csvDataSets().isEmpty()) {
            appendCsvParseHelper(script);
        }
    }

    private void appendCsvParseHelper(StringBuilder script) {
        script.append("function parseCsvLine(line, delimiter) {\n");
        script.append("  const values = [];\n");
        script.append("  let current = '';\n");
        script.append("  let quoted = false;\n");
        script.append("  for (let i = 0; i < line.length; i++) {\n");
        script.append("    const char = line[i];\n");
        script.append("    if (char === '\"') {\n");
        script.append("      if (quoted && line[i + 1] === '\"') {\n");
        script.append("        current += '\"';\n");
        script.append("        i++;\n");
        script.append("      } else {\n");
        script.append("        quoted = !quoted;\n");
        script.append("      }\n");
        script.append("    } else if (char === delimiter && !quoted) {\n");
        script.append("      values.push(current);\n");
        script.append("      current = '';\n");
        script.append("    } else {\n");
        script.append("      current += char;\n");
        script.append("    }\n");
        script.append("  }\n");
        script.append("  values.push(current);\n");
        script.append("  return values;\n");
        script.append("}\n\n");
    }

    private void appendCsvVariableAssignments(StringBuilder script, JmxTestPlan testPlan) {
        for (int i = 0; i < testPlan.csvDataSets().size(); i++) {
            JmxCsvDataSet csvDataSet = testPlan.csvDataSets().get(i);
            String dataVariable = csvDataVariableName(i);
            String rowVariable = "csvRow" + (i + 1);
            script.append("  const ").append(rowVariable).append(" = ").append(dataVariable)
                    .append("[(__VU + __ITER - 1) % ").append(dataVariable).append(".length] || [];\n");
            for (int j = 0; j < csvDataSet.variableNames().size(); j++) {
                script.append("  ")
                        .append(variableAccess(csvDataSet.variableNames().get(j)))
                        .append(" = ")
                        .append(rowVariable)
                        .append("[")
                        .append(j)
                        .append("] ?? ")
                        .append(variableAccess(csvDataSet.variableNames().get(j)))
                        .append(";\n");
            }
        }
        if (!testPlan.csvDataSets().isEmpty()) {
            script.append("\n");
        }
    }

    private void appendResolveHelper(StringBuilder script) {
        script.append("function resolve(value) {\n");
        script.append("  return String(value).replace(/\\$\\{([^}]+)\\}/g, (_, name) => variables[name] ?? __ENV[name] ?? `\\${${name}}`);\n");
        script.append("}\n\n");
        script.append("function pickRandom(values, defaultValue) {\n");
        script.append("  if (!values || values.length === 0) {\n");
        script.append("    return defaultValue;\n");
        script.append("  }\n");
        script.append("  return values[Math.floor(Math.random() * values.length)];\n");
        script.append("}\n\n");
        script.append("function withQueryParameters(url, params, encodeValue) {\n");
        script.append("  const entries = Object.entries(params).filter(([, value]) => value !== undefined && value !== null);\n");
        script.append("  if (entries.length === 0) {\n");
        script.append("    return url;\n");
        script.append("  }\n");
        script.append("  const query = entries.map(([name, value]) => {\n");
        script.append("    const resolved = resolve(value);\n");
        script.append("    const queryValue = encodeValue[name] ? encodeURIComponent(resolved) : resolved;\n");
        script.append("    return `${encodeURIComponent(name)}=${queryValue}`;\n");
        script.append("  }).join('&');\n");
        script.append("  return `${url}${url.includes('?') ? '&' : '?'}${query}`;\n");
        script.append("}\n\n");
        script.append("function normalizeUrl(value) {\n");
        script.append("  const url = resolve(value).trim();\n");
        script.append("  if (!url || url.startsWith('NO_')) {\n");
        script.append("    return url;\n");
        script.append("  }\n");
        script.append("  if (url.startsWith('http')) {\n");
        script.append("    return url;\n");
        script.append("  }\n");
        script.append("  const baseUrl = variables.BASE_URL || '';\n");
        script.append("  return url.startsWith('/') ? `${baseUrl}${url}` : `${baseUrl}/${url}`;\n");
        script.append("}\n\n");
        script.append("function extractQueryParameter(url, name, defaultValue) {\n");
        script.append("  try {\n");
        script.append("    return new URL(resolve(url)).searchParams.get(name) || defaultValue;\n");
        script.append("  } catch (_) {\n");
        script.append("    return defaultValue;\n");
        script.append("  }\n");
        script.append("}\n\n");
    }

    private String buildParams(JmxHttpRequest request) {
        if (request.headers().isEmpty()) {
            return "{}";
        }

        String headers = mergeHeaders(request.headers())
                .entrySet()
                .stream()
                .map(entry -> "      " + toJsString(entry.getKey()) + ": resolve(" + toJsString(entry.getValue()) + ")")
                .collect(Collectors.joining(",\n"));

        return "{\n    headers: {\n" + headers + "\n    }\n  }";
    }

    private String buildUrlExpression(JmxHttpRequest request) {
        String protocol = request.protocol().isBlank() ? "https" : request.protocol();
        String port = request.port().isBlank() ? "" : ":" + request.port();
        String url = protocol + "://" + request.domain() + port + request.path();
        String resolvedUrl = "resolve(" + toJsString(url) + ")";
        if (!"GET".equalsIgnoreCase(request.method()) || request.parameters().isEmpty()) {
            return resolvedUrl;
        }
        return "withQueryParameters(" + resolvedUrl + ", " + buildQueryParameterObject(request.parameters()) + ", "
                + buildQueryEncodingObject(request.parameters()) + ")";
    }

    private String buildQueryParameterObject(List<JmxRequestParameter> parameters) {
        return parameters.stream()
                .map(parameter -> "    " + toJsString(parameter.name()) + ": resolve(" + toJsString(parameter.value()) + ")")
                .collect(Collectors.joining(",\n", "{\n", "\n  }"));
    }

    private String buildQueryEncodingObject(List<JmxRequestParameter> parameters) {
        return parameters.stream()
                .map(parameter -> "    " + toJsString(parameter.name()) + ": " + parameter.alwaysEncode())
                .collect(Collectors.joining(",\n", "{\n", "\n  }"));
    }

    private String buildBodyExpression(JmxHttpRequest request) {
        if (!request.rawBody() && !request.parameters().isEmpty()) {
            return buildFormBodyExpression(request.parameters());
        }
        return "resolve(" + toJsString(request.body()) + ")";
    }

    private String buildFormBodyExpression(List<JmxRequestParameter> parameters) {
        return parameters.stream()
                .map(parameter -> "    " + toJsString(parameter.name()) + ": resolve(" + toJsString(parameter.value()) + ")")
                .collect(Collectors.joining(",\n", "{\n", "\n  }"));
    }

    private void appendRegexExtractors(StringBuilder script, JmxHttpRequest request, String responseVariable, String indent) {
        if (request.regexExtractors().isEmpty()) {
            return;
        }

        for (int i = 0; i < request.regexExtractors().size(); i++) {
            JmxRegexExtractor extractor = request.regexExtractors().get(i);
            String matchesVariable = responseVariable + "Matches" + (i + 1);
            String sourceExpression = extractor.useHeaders()
                    ? "JSON.stringify(" + responseVariable + ".headers)"
                    : responseVariable + ".body";
            script.append(indent).append("const ").append(matchesVariable).append(" = Array.from(String(")
                    .append(sourceExpression)
                    .append(").matchAll(")
                    .append(toJsRegex(extractor.regex(), "g"))
                    .append("));\n");
            script.append(indent)
                    .append(variableAccess(extractor.variableName()))
                    .append(" = ")
                    .append(regexMatchExpression(matchesVariable, extractor.matchNumber()))
                    .append(" ?? ")
                    .append(toJsString(extractor.defaultValue()))
                    .append(";\n");
        }
    }

    private void appendHtmlExtractors(StringBuilder script, JmxHttpRequest request, String responseVariable, String indent) {
        if (request.htmlExtractors().isEmpty()) {
            return;
        }

        for (JmxHtmlExtractor extractor : request.htmlExtractors()) {
            String arrayVariable = htmlArrayVariableName(responseVariable, extractor.variableName());
            script.append(indent).append("const ").append(arrayVariable).append(" = ")
                    .append(responseVariable)
                    .append(".html().find(")
                    .append(toJsString(extractor.cssSelector()))
                    .append(").toArray().map((element) => element.")
                    .append(htmlAttributeAccessor(extractor.attribute()))
                    .append(").filter((value) => value && String(value).trim().length > 0);\n");
            script.append(indent)
                    .append(variableAccess(extractor.variableName() + "_matchNr"))
                    .append(" = String(")
                    .append(arrayVariable)
                    .append(".length);\n");
            script.append(indent).append(arrayVariable).append(".forEach((value, index) => {\n");
            script.append(indent).append("  variables[").append(toJsString(extractor.variableName() + "_")).append(" + (index + 1)] = value;\n");
            script.append(indent).append("});\n");
            if (extractor.matchNumber() > 0) {
                script.append(indent)
                        .append(variableAccess(extractor.variableName()))
                        .append(" = ")
                        .append(arrayVariable)
                        .append("[")
                        .append(extractor.matchNumber() - 1)
                        .append("] ?? ")
                        .append(toJsString(extractor.defaultValue()))
                        .append(";\n");
            }
        }
    }

    private void appendPostProcessorActions(StringBuilder script, JmxHttpRequest request, String responseVariable, String indent) {
        for (JmxPostProcessorAction action : request.postProcessorActions()) {
            String arrayVariable = htmlArrayVariableName(responseVariable, action.sourceVariablePrefix());
            String selectedVariable = responseVariable + "_" + action.targetVariableName() + "Value";
            script.append(indent).append("const ").append(selectedVariable).append(" = pickRandom(")
                    .append(arrayVariable)
                    .append(", ")
                    .append(toJsString(action.defaultValue()))
                    .append(");\n");
            if (action.normalizeUrl()) {
                script.append(indent)
                        .append(variableAccess(action.targetVariableName()))
                        .append(" = normalizeUrl(")
                        .append(selectedVariable)
                        .append(");\n");
            } else {
                script.append(indent)
                        .append(variableAccess(action.targetVariableName()))
                        .append(" = ")
                        .append(selectedVariable)
                        .append(";\n");
            }
            if (action.extractProductId()) {
                script.append(indent).append("variables.productId = extractQueryParameter(")
                        .append(variableAccess(action.targetVariableName()))
                        .append(", 'product_id', 'NO_ID');\n");
            }
        }
    }

    private Map<String, String> mergeHeaders(List<JmxHeader> headers) {
        Map<String, String> merged = new LinkedHashMap<>();
        for (JmxHeader header : headers) {
            merged.put(header.name(), header.value());
        }
        return merged;
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

    private String responseVariableName(JmxHttpRequest request, int requestIndex) {
        String baseName = request.name() == null ? "response" : request.name();
        String normalized = baseName.replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (normalized.isBlank() || Character.isDigit(normalized.charAt(0))) {
            normalized = "response_" + normalized;
        }
        return "res" + requestIndex + "_" + normalized;
    }

    private String regexMatchExpression(String matchesVariable, int matchNumber) {
        if (matchNumber == 0) {
            return "(pickRandom(" + matchesVariable + ", [])[1] || pickRandom(" + matchesVariable + ", [])[0])";
        }
        int index = Math.max(matchNumber, 1) - 1;
        return "(" + matchesVariable + "[" + index + "] && (" + matchesVariable + "[" + index + "][1] || " + matchesVariable + "[" + index + "][0]))";
    }

    private String variableAccess(String value) {
        if (value != null && value.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
            return "variables." + value;
        }
        return "variables[" + toJsString(value) + "]";
    }

    private String variableProperty(String value) {
        if (value != null && value.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
            return value;
        }
        return "[" + toJsString(value) + "]";
    }

    private String toJsRegex(String regex) {
        return toJsRegex(regex, "");
    }

    private String toJsRegex(String regex, String flags) {
        String safeRegex = regex == null ? "" : regex;
        return "new RegExp(" + toJsString(safeRegex) + ", " + toJsString(flags) + ")";
    }

    private String htmlArrayVariableName(String responseVariableName, String variableName) {
        String sourceName = responseVariableName + "_" + (variableName == null ? "values" : variableName);
        String normalized = sourceName.replaceAll("[^a-zA-Z0-9]+", "_");
        if (normalized.isBlank() || Character.isDigit(normalized.charAt(0))) {
            normalized = "values_" + normalized;
        }
        return "html_" + normalized;
    }

    private String htmlAttributeAccessor(String attribute) {
        if (attribute == null || attribute.isBlank() || "text".equalsIgnoreCase(attribute)) {
            return "text()";
        }
        return "attr(" + toJsString(attribute) + ")";
    }

    private String csvDataVariableName(int index) {
        return "csvData" + (index + 1);
    }

    private String toK6FilePath(String filename) {
        String normalized = filename == null ? "" : filename.replace("\\", "/");
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private String toObjectKey(String value) {
        if (value != null && value.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
            return value;
        }
        return toJsString(value);
    }

    private String envAccess(String value) {
        if (value != null && value.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return "__ENV." + value;
        }
        return "__ENV[" + toJsString(value) + "]";
    }
}
