package com.vvs.converter;

import com.vvs.model.JmxCsvDataSet;
import com.vvs.model.JmxHeader;
import com.vvs.model.JmxHttpRequest;
import com.vvs.model.JmxRegexExtractor;
import com.vvs.model.JmxRequestParameter;
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
                        List.of(new JmxHeader("Content-Type", "application/json")),
                        List.of(),
                        List.of(new JmxRegexExtractor("USER_ID", "\"id\":\"([^\"]+)\"", "NOT_FOUND", 1, false)),
                        List.of(),
                        List.of(),
                        true,
                        "variables.productId !== 'NO_ID'"
                )),
                java.util.Map.of(),
                List.of(new JmxCsvDataSet("Users CSV", "D:/Jmeter/bin/users.csv", List.of("email", "password"), ",", true, true, false))
        );

        String script = generator.generate(testPlan);

        assertThat(script).contains("import http from 'k6/http';");
        assertThat(script).contains("vus: 3");
        assertThat(script).contains("iterations: 2");
        assertThat(script).contains("if (variables.productId !== 'NO_ID') {");
        assertThat(script).contains("http.post(resolve('https://api.example.com/users')");
        assertThat(script).contains("'Content-Type': resolve('application/json')");
        assertThat(script).contains("variables.USER_ID = (res1_Create_userMatches1[0] && (res1_Create_userMatches1[0][1] || res1_Create_userMatches1[0][0])) ?? 'NOT_FOUND';");
        assertThat(script).contains("import { SharedArray } from 'k6/data';");
        assertThat(script).contains("open('users.csv')");
        assertThat(script).contains("variables.email = csvRow1[0] ?? variables.email;");
    }

    @Test
    void generatesValidAccessorsForNonIdentifierVariableNames() {
        JmxTestPlan testPlan = new JmxTestPlan(
                "Variables Test",
                new JmxThreadGroup("Users", 1, 1, 1),
                List.of(new JmxHttpRequest(
                        "Search",
                        "GET",
                        "https",
                        "api.example.com",
                        "",
                        "/search",
                        "",
                        List.of(new JmxHeader("X-User", "${user-id}")),
                        List.of(new JmxRequestParameter("q", "hello world", true)),
                        List.of(new JmxRegexExtractor("token-id", "token=([a-z]+)", "missing", 1, false)),
                        List.of(),
                        List.of(),
                        true
                )),
                java.util.Map.of("user-id", "42"),
                List.of(new JmxCsvDataSet("Users CSV", "users.csv", List.of("user-id"), ",", false, true, false))
        );

        String script = generator.generate(testPlan);

        assertThat(script).contains("'user-id': __ENV['user-id'] || '42'");
        assertThat(script).contains("variables['user-id'] = csvRow1[0] ?? variables['user-id'];");
        assertThat(script).contains("variables['token-id'] = (res1_SearchMatches1[0] && (res1_SearchMatches1[0][1] || res1_SearchMatches1[0][0])) ?? 'missing';");
        assertThat(script).contains("withQueryParameters(resolve('https://api.example.com/search')");
        assertThat(script).contains("'q': true");
        assertThat(script).doesNotContain("variables.['");
        assertThat(script).doesNotContain("__ENV.['");
    }

    @Test
    void generatesUniqueResponseVariablesForDuplicateRequestNames() {
        JmxHttpRequest firstRequest = requestWithRegex("Open login");
        JmxHttpRequest secondRequest = requestWithRegex("Open login");
        JmxTestPlan testPlan = new JmxTestPlan(
                "Duplicate Request Names",
                new JmxThreadGroup("Users", 1, 1, 1),
                List.of(firstRequest, secondRequest)
        );

        String script = generator.generate(testPlan);

        assertThat(script).contains("const res1_Open_login = http.get(");
        assertThat(script).contains("const res2_Open_login = http.get(");
    }

    @Test
    void usesRequestedRegexMatchNumber() {
        JmxTestPlan testPlan = new JmxTestPlan(
                "Regex Match Test",
                new JmxThreadGroup("Users", 1, 1, 1),
                List.of(new JmxHttpRequest(
                        "Extract second",
                        "GET",
                        "https",
                        "api.example.com",
                        "",
                        "/items",
                        "",
                        List.of(),
                        List.of(),
                        List.of(new JmxRegexExtractor("ITEM_ID", "item=([0-9]+)", "NO_ID", 2, false)),
                        List.of(),
                        List.of(),
                        true
                ))
        );

        String script = generator.generate(testPlan);

        assertThat(script).contains("variables.ITEM_ID = (res1_Extract_secondMatches1[1] && (res1_Extract_secondMatches1[1][1] || res1_Extract_secondMatches1[1][0])) ?? 'NO_ID';");
    }

    private JmxHttpRequest requestWithRegex(String name) {
        return new JmxHttpRequest(
                name,
                "GET",
                "https",
                "api.example.com",
                "",
                "/login",
                "",
                List.of(),
                List.of(),
                List.of(new JmxRegexExtractor("TOKEN", "token=([a-z]+)", "NO_TOKEN", 1, false)),
                List.of(),
                List.of(),
                true
        );
    }
}
