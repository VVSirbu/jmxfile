package com.vvs.model;

import java.util.List;

public record JmxTestPlan(
        String name,
        JmxThreadGroup threadGroup,
        List<JmxHttpRequest> requests
) {
}
