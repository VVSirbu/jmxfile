package com.vvs.converter;

import com.vvs.model.JmxHeader;
import com.vvs.model.JmxHttpRequest;
import com.vvs.model.JmxTestPlan;
import com.vvs.model.JmxThreadGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class K6ScriptGeneratorTest {

    private final K6ScriptGenerator generator = new K6ScriptGenerator();

    @Test
    void generatesK6ScriptForHttpRequests() {
        JmxTestPlan testPlan = new JmxTestPlan(
                "Smoke Test",
                new JmxThreadGroup("Users", 3, 1, 2),
                List.of(new JmxHttpRequest(
                        "Create user",
                        "POST",
                        "https",
                        "api.example.com",
                        "",
                        "/users",
                        "{\"name\":\"Ann\"}",
                        List.of(new JmxHeader("Content-Type", "application/json"))
                ))
        );

        String script = generator.generate(testPlan);

        assertThat(script).contains("import http from 'k6/http';");
        assertThat(script).contains("vus: 3");
        assertThat(script).contains("iterations: 2");
        assertThat(script).contains("http.post('https://api.example.com/users'");
        assertThat(script).contains("'Content-Type': 'application/json'");
    }
}
