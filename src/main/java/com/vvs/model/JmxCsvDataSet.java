package com.vvs.model;

import java.util.List;

public record JmxCsvDataSet(
        String name,
        String filename,
        List<String> variableNames,
        String delimiter,
        boolean ignoreFirstLine,
        boolean recycle,
        boolean stopThread
) {
    public JmxCsvDataSet {
        variableNames = List.copyOf(variableNames);
    }
}
