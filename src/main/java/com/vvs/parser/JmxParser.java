package com.vvs.parser;

import com.vvs.model.JmxHeader;
import com.vvs.model.JmxHttpRequest;
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
import java.util.List;

@Component
public class JmxParser {

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
            JmxThreadGroup threadGroup = parseThreadGroup(document);
            List<JmxHeader> globalHeaders = parseHeaders(document);
            List<JmxHttpRequest> requests = parseHttpRequests(document, globalHeaders);

            return new JmxTestPlan(testPlanName, threadGroup, requests);
        } catch (Exception ex) {
            throw new JmxParseException("Failed to parse JMX file", ex);
        }
    }

    private JmxThreadGroup parseThreadGroup(Document document) {
        NodeList nodes = document.getElementsByTagName("ThreadGroup");
        if (nodes.getLength() == 0 || !(nodes.item(0) instanceof Element threadGroup)) {
            return new JmxThreadGroup("Default thread group", 1, 1, 1);
        }

        String name = attrOrDefault(threadGroup, "testname", "Thread group");
        int virtualUsers = intChildProperty(threadGroup, "ThreadGroup.num_threads", 1);
        int rampUpSeconds = intChildProperty(threadGroup, "ThreadGroup.ramp_time", 1);
        int loops = intChildProperty(threadGroup, "LoopController.loops", 1);

        return new JmxThreadGroup(name, virtualUsers, rampUpSeconds, loops);
    }

    private List<JmxHeader> parseHeaders(Document document) {
        List<JmxHeader> headers = new ArrayList<>();
        NodeList headerManagers = document.getElementsByTagName("HeaderManager");

        for (int i = 0; i < headerManagers.getLength(); i++) {
            if (!(headerManagers.item(i) instanceof Element headerManager)) {
                continue;
            }

            NodeList elements = headerManager.getElementsByTagName("elementProp");
            for (int j = 0; j < elements.getLength(); j++) {
                if (!(elements.item(j) instanceof Element element)) {
                    continue;
                }

                String headerName = childProperty(element, "Header.name", "");
                String headerValue = childProperty(element, "Header.value", "");
                if (!headerName.isBlank()) {
                    headers.add(new JmxHeader(headerName, headerValue));
                }
            }
        }

        return headers;
    }

    private List<JmxHttpRequest> parseHttpRequests(Document document, List<JmxHeader> globalHeaders) {
        List<JmxHttpRequest> requests = new ArrayList<>();
        NodeList samplers = document.getElementsByTagName("HTTPSamplerProxy");

        for (int i = 0; i < samplers.getLength(); i++) {
            if (!(samplers.item(i) instanceof Element sampler)) {
                continue;
            }

            requests.add(new JmxHttpRequest(
                    attrOrDefault(sampler, "testname", "HTTP request " + (i + 1)),
                    childProperty(sampler, "HTTPSampler.method", "GET").toUpperCase(),
                    childProperty(sampler, "HTTPSampler.protocol", "https"),
                    childProperty(sampler, "HTTPSampler.domain", ""),
                    childProperty(sampler, "HTTPSampler.port", ""),
                    normalizePath(childProperty(sampler, "HTTPSampler.path", "/")),
                    extractBody(sampler),
                    List.copyOf(globalHeaders)
            ));
        }

        return requests;
    }

    private String extractBody(Element sampler) {
        String rawBody = childProperty(sampler, "Argument.value", "");
        return rawBody.isBlank() ? "" : rawBody;
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
        return path.startsWith("/") ? path : "/" + path;
    }
}
