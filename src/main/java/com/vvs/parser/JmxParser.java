package com.vvs.parser;

import com.vvs.model.JmxCsvDataSet;
import com.vvs.model.JmxHeader;
import com.vvs.model.JmxHtmlExtractor;
import com.vvs.model.JmxHttpRequest;
import com.vvs.model.JmxPostProcessorAction;
import com.vvs.model.JmxRegexExtractor;
import com.vvs.model.JmxRequestParameter;
import com.vvs.model.JmxTestPlan;
import com.vvs.model.JmxThreadGroup;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JmxParser {

    private static final Pattern JMETER_PROPERTY_FUNCTION = Pattern.compile("\\$\\{__P\\([^,]+,\\s*([0-9]+)\\)\\}");
    private static final Pattern IF_VARIABLE_EQUALS = Pattern.compile("(!?)\\s*['\"]([^'\"]+)['\"]\\.equals\\(vars\\.get\\(['\"]([^'\"]+)['\"]\\)\\)");

    public JmxTestPlan parse(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder().parse(new InputSource(inputStream));
            document.getDocumentElement().normalize();

            String testPlanName = firstElementTestName(document, "TestPlan", "Converted JMeter test plan");
            Map<String, String> variables = parseUserDefinedVariables(document);
            ParseState state = new ParseState();
            Element rootHashTree = firstChildElement(document.getDocumentElement(), "hashTree");

            if (rootHashTree != null) {
                walkHashTree(rootHashTree, ParseContext.root(variables), state);
            }

            JmxThreadGroup threadGroup = state.firstThreadGroup == null
                    ? new JmxThreadGroup("Default thread group", 1, 1, 1)
                    : state.firstThreadGroup;

            return new JmxTestPlan(testPlanName, threadGroup, state.requests, variables, state.csvDataSets);
        } catch (Exception ex) {
            throw new JmxParseException("Failed to parse JMX file", ex);
        }
    }

    private void walkHashTree(Element hashTree, ParseContext context, ParseState state) {
        List<Element> children = childElements(hashTree);
        ParseContext currentContext = context;

        for (int i = 0; i < children.size(); i++) {
            Element element = children.get(i);
            if ("hashTree".equals(element.getTagName())) {
                continue;
            }

            Element childHashTree = nextHashTree(children, i);
            if (!isEnabled(element)) {
                continue;
            }

            String tagName = element.getTagName();
            if ("ThreadGroup".equals(tagName)) {
                JmxThreadGroup threadGroup = parseThreadGroup(element);
                if (state.firstThreadGroup == null) {
                    state.firstThreadGroup = threadGroup;
                }
                if (childHashTree != null) {
                    walkHashTree(childHashTree, currentContext.withThreadGroup(threadGroup), state);
                }
                continue;
            }

            if ("ConfigTestElement".equals(tagName) && "HTTP Request Defaults".equals(attrOrDefault(element, "testname", ""))) {
                currentContext = currentContext.withDefaults(parseDefaults(element, currentContext.defaults));
                continue;
            }

            if ("HeaderManager".equals(tagName)) {
                currentContext = currentContext.withAdditionalHeaders(parseHeaders(element));
                continue;
            }

            if ("Arguments".equals(tagName)) {
                currentContext = currentContext.withVariables(parseArgumentsAsVariables(element));
                continue;
            }

            if ("CSVDataSet".equals(tagName)) {
                parseCsvDataSet(element).ifPresent(state::addCsvDataSet);
                continue;
            }

            if ("IfController".equals(tagName)) {
                if (childHashTree != null) {
                    walkHashTree(childHashTree, currentContext.withCondition(parseIfCondition(element)), state);
                }
                continue;
            }

            if ("HTTPSamplerProxy".equals(tagName)) {
                state.requests.add(parseHttpRequest(element, childHashTree, currentContext));
                continue;
            }

            if (childHashTree != null) {
                walkHashTree(childHashTree, currentContext, state);
            }
        }
    }

    private JmxThreadGroup parseThreadGroup(Element threadGroup) {
        String name = attrOrDefault(threadGroup, "testname", "Thread group");
        int virtualUsers = intChildProperty(threadGroup, "ThreadGroup.num_threads", 1);
        int rampUpSeconds = intChildProperty(threadGroup, "ThreadGroup.ramp_time", 1);
        int loops = intChildProperty(threadGroup, "LoopController.loops", 1);
        return new JmxThreadGroup(name, virtualUsers, rampUpSeconds, loops);
    }

    private JmxHttpRequest parseHttpRequest(Element sampler, Element childHashTree, ParseContext context) {
        HttpDefaults defaults = context.defaults;
        String method = childProperty(sampler, "HTTPSampler.method", "GET").toUpperCase();
        String protocol = childProperty(sampler, "HTTPSampler.protocol", defaults.protocol());
        String domain = childProperty(sampler, "HTTPSampler.domain", defaults.domain());
        String port = childProperty(sampler, "HTTPSampler.port", defaults.port());
        String path = normalizePath(childProperty(sampler, "HTTPSampler.path", defaults.path()));
        boolean rawBody = "true".equalsIgnoreCase(childProperty(sampler, "HTTPSampler.postBodyRaw", "false"));
        List<JmxRequestParameter> parameters = parseRequestParameters(sampler);
        String body = rawBody || parameters.isEmpty() ? childProperty(sampler, "Argument.value", "") : "";

        List<JmxHeader> headers = new ArrayList<>(context.headers);
        List<JmxRegexExtractor> regexExtractors = List.of();
        List<JmxHtmlExtractor> htmlExtractors = List.of();
        List<JmxPostProcessorAction> postProcessorActions = List.of();
        if (childHashTree != null) {
            headers.addAll(parseDirectHeaders(childHashTree));
            regexExtractors = parseDirectRegexExtractors(childHashTree);
            htmlExtractors = parseDirectHtmlExtractors(childHashTree);
            postProcessorActions = parseDirectPostProcessorActions(childHashTree);
        }

        return new JmxHttpRequest(
                attrOrDefault(sampler, "testname", "HTTP request"),
                method,
                resolveVariables(protocol, context.variables),
                resolveVariables(domain, context.variables),
                resolveVariables(port, context.variables),
                resolveVariables(path, context.variables),
                resolveVariables(body, context.variables),
                resolveHeaders(headers, context.variables),
                resolveParameters(parameters, context.variables),
                regexExtractors,
                htmlExtractors,
                postProcessorActions,
                rawBody || parameters.isEmpty(),
                context.conditionExpression
        );
    }

    private String parseIfCondition(Element ifController) {
        String condition = childProperty(ifController, "IfController.condition", "");
        if (condition.isBlank()) {
            return "";
        }

        Matcher matcher = IF_VARIABLE_EQUALS.matcher(condition);
        List<String> comparisons = new ArrayList<>();
        while (matcher.find()) {
            String operator = matcher.group(1).isBlank() ? "===" : "!==";
            comparisons.add(variableExpression(matcher.group(3)) + " " + operator + " " + toJsString(matcher.group(2)));
        }

        if (comparisons.isEmpty()) {
            return "";
        }

        String joiner = condition.contains("||") ? " || " : " && ";
        return String.join(joiner, comparisons);
    }

    private HttpDefaults parseDefaults(Element defaultsElement, HttpDefaults parentDefaults) {
        return new HttpDefaults(
                childProperty(defaultsElement, "HTTPSampler.protocol", parentDefaults.protocol()),
                childProperty(defaultsElement, "HTTPSampler.domain", parentDefaults.domain()),
                childProperty(defaultsElement, "HTTPSampler.port", parentDefaults.port()),
                normalizePath(childProperty(defaultsElement, "HTTPSampler.path", parentDefaults.path()))
        );
    }

    private Map<String, String> parseUserDefinedVariables(Document document) {
        Map<String, String> variables = new LinkedHashMap<>();
        NodeList testPlans = document.getElementsByTagName("TestPlan");
        for (int i = 0; i < testPlans.getLength(); i++) {
            if (testPlans.item(i) instanceof Element testPlan) {
                NodeList elements = testPlan.getElementsByTagName("elementProp");
                for (int j = 0; j < elements.getLength(); j++) {
                    if (elements.item(j) instanceof Element element
                            && "TestPlan.user_defined_variables".equals(element.getAttribute("name"))) {
                        variables.putAll(parseArgumentsAsVariables(element));
                    }
                }
            }
        }
        return variables;
    }

    private Map<String, String> parseArgumentsAsVariables(Element argumentsElement) {
        Map<String, String> variables = new LinkedHashMap<>();
        for (Element argument : argumentElements(argumentsElement)) {
            String name = childProperty(argument, "Argument.name", attrOrDefault(argument, "name", ""));
            String value = childProperty(argument, "Argument.value", "");
            if (!name.isBlank()) {
                variables.put(name, value);
            }
        }
        return variables;
    }

    private java.util.Optional<JmxCsvDataSet> parseCsvDataSet(Element csvDataSet) {
        String filename = childProperty(csvDataSet, "filename", "");
        String variableNames = childProperty(csvDataSet, "variableNames", "");
        if (filename.isBlank() || variableNames.isBlank()) {
            return java.util.Optional.empty();
        }

        List<String> variables = new ArrayList<>();
        for (String variableName : variableNames.split(",")) {
            String trimmed = variableName.trim();
            if (!trimmed.isBlank()) {
                variables.add(trimmed);
            }
        }

        if (variables.isEmpty()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(new JmxCsvDataSet(
                attrOrDefault(csvDataSet, "testname", "CSV Data Set"),
                filename,
                variables,
                childProperty(csvDataSet, "delimiter", ","),
                "true".equalsIgnoreCase(childProperty(csvDataSet, "ignoreFirstLine", "false")),
                "true".equalsIgnoreCase(childProperty(csvDataSet, "recycle", "true")),
                "true".equalsIgnoreCase(childProperty(csvDataSet, "stopThread", "false"))
        ));
    }

    private List<JmxHeader> parseDirectHeaders(Element hashTree) {
        List<JmxHeader> headers = new ArrayList<>();
        for (Element child : childElements(hashTree)) {
            if ("HeaderManager".equals(child.getTagName()) && isEnabled(child)) {
                headers.addAll(parseHeaders(child));
            }
        }
        return headers;
    }

    private List<JmxRegexExtractor> parseDirectRegexExtractors(Element hashTree) {
        List<JmxRegexExtractor> extractors = new ArrayList<>();
        for (Element child : childElements(hashTree)) {
            if ("RegexExtractor".equals(child.getTagName()) && isEnabled(child)) {
                String variableName = childProperty(child, "RegexExtractor.refname", "");
                String regex = childProperty(child, "RegexExtractor.regex", "");
                if (!variableName.isBlank() && !regex.isBlank()) {
                    extractors.add(new JmxRegexExtractor(
                            variableName,
                            regex,
                            childProperty(child, "RegexExtractor.default", ""),
                            intChildProperty(child, "RegexExtractor.match_number", 1),
                            "true".equalsIgnoreCase(childProperty(child, "RegexExtractor.useHeaders", "false"))
                    ));
                }
            }
        }
        return extractors;
    }

    private List<JmxHtmlExtractor> parseDirectHtmlExtractors(Element hashTree) {
        List<JmxHtmlExtractor> extractors = new ArrayList<>();
        for (Element child : childElements(hashTree)) {
            if ("HtmlExtractor".equals(child.getTagName()) && isEnabled(child)) {
                String variableName = childProperty(child, "HtmlExtractor.refname", "");
                String cssSelector = childProperty(child, "HtmlExtractor.expr", "");
                String attribute = childProperty(child, "HtmlExtractor.attribute", "");
                if (!variableName.isBlank() && !cssSelector.isBlank()) {
                    extractors.add(new JmxHtmlExtractor(
                            variableName,
                            cssSelector,
                            attribute,
                            childProperty(child, "HtmlExtractor.default", ""),
                            intChildProperty(child, "HtmlExtractor.match_number", 1)
                    ));
                }
            }
        }
        return extractors;
    }

    private List<JmxPostProcessorAction> parseDirectPostProcessorActions(Element hashTree) {
        List<JmxPostProcessorAction> actions = new ArrayList<>();
        for (Element child : childElements(hashTree)) {
            if ("JSR223PostProcessor".equals(child.getTagName()) && isEnabled(child)) {
                parseRandomPickAction(childProperty(child, "script", "")).ifPresent(actions::add);
            }
        }
        return actions;
    }

    private java.util.Optional<JmxPostProcessorAction> parseRandomPickAction(String script) {
        if (script == null || script.isBlank()) {
            return java.util.Optional.empty();
        }

        Pattern sourcePattern = Pattern.compile("vars\\.get\\('([^']+)_matchNr'\\).*?vars\\.get\\('\\1_'\\s*\\+\\s*rnd\\)", Pattern.DOTALL);
        Matcher sourceMatcher = sourcePattern.matcher(script);
        Pattern targetPattern = Pattern.compile("vars\\.put\\('([^']+)'\\s*,\\s*url \\?: '([^']+)'\\)");
        Matcher targetMatcher = targetPattern.matcher(script);

        if (!sourceMatcher.find() || !targetMatcher.find()) {
            return java.util.Optional.empty();
        }

        boolean normalizeUrl = script.contains("startsWith('http')") || script.contains("startsWith(&apos;http&apos;)");
        boolean extractProductId = script.contains("vars.put('productId'") || script.contains("vars.put(&apos;productId&apos;");
        return java.util.Optional.of(new JmxPostProcessorAction(
                sourceMatcher.group(1),
                targetMatcher.group(1),
                targetMatcher.group(2),
                normalizeUrl,
                extractProductId
        ));
    }

    private List<JmxHeader> parseHeaders(Element headerManager) {
        List<JmxHeader> headers = new ArrayList<>();
        for (Element element : argumentLikeElements(headerManager, "Header")) {
            String headerName = childProperty(element, "Header.name", "");
            String headerValue = childProperty(element, "Header.value", "");
            if (!headerName.isBlank()) {
                headers.add(new JmxHeader(headerName, headerValue));
            }
        }
        return headers;
    }

    private List<JmxRequestParameter> parseRequestParameters(Element sampler) {
        List<JmxRequestParameter> parameters = new ArrayList<>();
        for (Element argument : argumentElements(sampler)) {
            String name = childProperty(argument, "Argument.name", attrOrDefault(argument, "name", ""));
            String value = childProperty(argument, "Argument.value", "");
            boolean alwaysEncode = "true".equalsIgnoreCase(childProperty(argument, "HTTPArgument.always_encode", "false"));
            if (!name.isBlank()) {
                parameters.add(new JmxRequestParameter(name, value, alwaysEncode));
            }
        }
        return parameters;
    }

    private List<Element> argumentElements(Element root) {
        return argumentLikeElements(root, "Argument", "HTTPArgument");
    }

    private List<Element> argumentLikeElements(Element root, String... elementTypes) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = root.getElementsByTagName("elementProp");
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element element)) {
                continue;
            }
            String elementType = element.getAttribute("elementType");
            for (String acceptedType : elementTypes) {
                if (acceptedType.equals(elementType)) {
                    elements.add(element);
                    break;
                }
            }
        }
        return elements;
    }

    private List<JmxHeader> resolveHeaders(List<JmxHeader> headers, Map<String, String> variables) {
        List<JmxHeader> resolved = new ArrayList<>();
        for (JmxHeader header : headers) {
            resolved.add(new JmxHeader(
                    resolveVariables(header.name(), variables),
                    resolveVariables(header.value(), variables)
            ));
        }
        return resolved;
    }

    private List<JmxRequestParameter> resolveParameters(List<JmxRequestParameter> parameters, Map<String, String> variables) {
        List<JmxRequestParameter> resolved = new ArrayList<>();
        for (JmxRequestParameter parameter : parameters) {
            resolved.add(new JmxRequestParameter(
                    resolveVariables(parameter.name(), variables),
                    resolveVariables(parameter.value(), variables),
                    parameter.alwaysEncode()
            ));
        }
        return resolved;
    }

    private String resolveVariables(String value, Map<String, String> variables) {
        if (value == null || value.isBlank() || variables.isEmpty()) {
            return value;
        }

        String resolved = value;
        boolean changed;
        int guard = 0;
        do {
            changed = false;
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String token = "${" + entry.getKey() + "}";
                if (resolved.contains(token)) {
                    resolved = resolved.replace(token, entry.getValue());
                    changed = true;
                }
            }
            guard++;
        } while (changed && guard < 10);
        return resolved;
    }

    private String firstElementTestName(Document document, String tagName, String defaultValue) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || !(nodes.item(0) instanceof Element element)) {
            return defaultValue;
        }
        return attrOrDefault(element, "testname", defaultValue);
    }

    private int intChildProperty(Element root, String propertyName, int defaultValue) {
        String value = childProperty(root, propertyName, "");
        if (value.isBlank() || "-1".equals(value)) {
            return defaultValue;
        }

        Matcher matcher = JMETER_PROPERTY_FUNCTION.matcher(value);
        if (matcher.matches()) {
            value = matcher.group(1);
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String childProperty(Element root, String propertyName, String defaultValue) {
        NodeList children = root.getElementsByTagName("*");
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && propertyName.equals(element.getAttribute("name"))) {
                return element.getTextContent() == null ? defaultValue : element.getTextContent().trim();
            }
        }
        return defaultValue;
    }

    private String attrOrDefault(Element element, String attributeName, String defaultValue) {
        String value = element.getAttribute(attributeName);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") || path.startsWith("${") ? path : "/" + path;
    }

    private boolean isEnabled(Element element) {
        return !"false".equalsIgnoreCase(element.getAttribute("enabled"));
    }

    private String variableExpression(String variableName) {
        if (variableName != null && variableName.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
            return "variables." + variableName;
        }
        return "variables[" + toJsString(variableName) + "]";
    }

    private String toJsString(String value) {
        String safeValue = value == null ? "" : value;
        return "'" + safeValue
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "'";
    }

    private Element nextHashTree(List<Element> elements, int currentIndex) {
        int nextIndex = currentIndex + 1;
        if (nextIndex < elements.size() && "hashTree".equals(elements.get(nextIndex).getTagName())) {
            return elements.get(nextIndex);
        }
        return null;
    }

    private Element firstChildElement(Element root, String tagName) {
        for (Element child : childElements(root)) {
            if (tagName.equals(child.getTagName())) {
                return child;
            }
        }
        return null;
    }

    private List<Element> childElements(Element root) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private record HttpDefaults(String protocol, String domain, String port, String path) {
        private static HttpDefaults empty() {
            return new HttpDefaults("https", "", "", "/");
        }
    }

    private record ParseContext(
            Map<String, String> variables,
            HttpDefaults defaults,
            List<JmxHeader> headers,
            JmxThreadGroup threadGroup,
            String conditionExpression
    ) {
        private static ParseContext root(Map<String, String> variables) {
            return new ParseContext(new LinkedHashMap<>(variables), HttpDefaults.empty(), List.of(), null, "");
        }

        private ParseContext withThreadGroup(JmxThreadGroup threadGroup) {
            return new ParseContext(variables, defaults, List.of(), threadGroup, conditionExpression);
        }

        private ParseContext withDefaults(HttpDefaults defaults) {
            return new ParseContext(variables, defaults, headers, threadGroup, conditionExpression);
        }

        private ParseContext withAdditionalHeaders(List<JmxHeader> additionalHeaders) {
            List<JmxHeader> merged = new ArrayList<>(headers);
            merged.addAll(additionalHeaders);
            return new ParseContext(variables, defaults, merged, threadGroup, conditionExpression);
        }

        private ParseContext withVariables(Map<String, String> additionalVariables) {
            Map<String, String> merged = new LinkedHashMap<>(variables);
            merged.putAll(additionalVariables);
            return new ParseContext(merged, defaults, headers, threadGroup, conditionExpression);
        }

        private ParseContext withCondition(String additionalCondition) {
            if (additionalCondition == null || additionalCondition.isBlank()) {
                return this;
            }
            String mergedCondition = conditionExpression == null || conditionExpression.isBlank()
                    ? additionalCondition
                    : "(" + conditionExpression + ") && (" + additionalCondition + ")";
            return new ParseContext(variables, defaults, headers, threadGroup, mergedCondition);
        }
    }

    private static class ParseState {
        private JmxThreadGroup firstThreadGroup;
        private final List<JmxHttpRequest> requests = new ArrayList<>();
        private final List<JmxCsvDataSet> csvDataSets = new ArrayList<>();
        private final java.util.Set<String> csvKeys = new java.util.LinkedHashSet<>();

        private void addCsvDataSet(JmxCsvDataSet csvDataSet) {
            String key = csvDataSet.filename() + "|" + String.join(",", csvDataSet.variableNames());
            if (csvKeys.add(key)) {
                csvDataSets.add(csvDataSet);
            }
        }
    }
}
