package com.vvs.parser;

import com.vvs.model.JmxTestPlan;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JmxParserTest {

    private final JmxParser parser = new JmxParser();

    @Test
    void parsesThreadGroupHttpSamplerAndHeaders() {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan testname="Smoke Test"/>
                    <hashTree>
                      <ThreadGroup testname="Users">
                        <intProp name="ThreadGroup.num_threads">5</intProp>
                        <intProp name="ThreadGroup.ramp_time">10</intProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <stringProp name="LoopController.loops">2</stringProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <HeaderManager testname="Headers">
                          <collectionProp name="HeaderManager.headers">
                            <elementProp name="" elementType="Header">
                              <stringProp name="Header.name">Content-Type</stringProp>
                              <stringProp name="Header.value">application/json</stringProp>
                            </elementProp>
                          </collectionProp>
                        </HeaderManager>
                        <HTTPSamplerProxy testname="Create user">
                          <stringProp name="HTTPSampler.domain">api.example.com</stringProp>
                          <stringProp name="HTTPSampler.protocol">https</stringProp>
                          <stringProp name="HTTPSampler.path">/users</stringProp>
                          <stringProp name="HTTPSampler.method">POST</stringProp>
                          <stringProp name="Argument.value">{"name":"Ann"}</stringProp>
                        </HTTPSamplerProxy>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        JmxTestPlan testPlan = parser.parse(new ByteArrayInputStream(jmx.getBytes(StandardCharsets.UTF_8)));

        assertThat(testPlan.name()).isEqualTo("Smoke Test");
        assertThat(testPlan.threadGroup().virtualUsers()).isEqualTo(5);
        assertThat(testPlan.threadGroup().rampUpSeconds()).isEqualTo(10);
        assertThat(testPlan.threadGroup().loops()).isEqualTo(2);
        assertThat(testPlan.requests()).hasSize(1);
        assertThat(testPlan.requests().get(0).headers()).hasSize(1);
        assertThat(testPlan.requests().get(0).body()).isEqualTo("{\"name\":\"Ann\"}");
    }
}
