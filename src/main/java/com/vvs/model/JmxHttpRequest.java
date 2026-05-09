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
        List<JmxHeader> headers
) {
}
