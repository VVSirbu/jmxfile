package com.vvs.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record JmxTestPlan(
        String name,
        JmxThreadGroup threadGroup,
        List<JmxHttpRequest> requests,
        Map<String, String> variables,
        List<JmxCsvDataSet> csvDataSets
) {
    public JmxTestPlan(String name, JmxThreadGroup threadGroup, List<JmxHttpRequest> requests) {
        this(name, threadGroup, requests, Map.of(), List.of());
    }

    public JmxTestPlan(String name, JmxThreadGroup threadGroup, List<JmxHttpRequest> requests, Map<String, String> variables) {
        this(name, threadGroup, requests, variables, List.of());
    }

    public JmxTestPlan {
        requests = List.copyOf(requests);
        variables = Map.copyOf(new LinkedHashMap<>(variables));
        csvDataSets = List.copyOf(csvDataSets);
    }
}
