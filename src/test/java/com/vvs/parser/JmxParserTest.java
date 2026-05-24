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

    @Test
    void appliesVariablesDefaultsScopedHeadersAndSkipsDisabledThreadGroups() {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan testname="Complex Test">
                      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments">
                        <collectionProp name="Arguments.arguments">
                          <elementProp name="BASE_PROTOCOL" elementType="Argument">
                            <stringProp name="Argument.name">BASE_PROTOCOL</stringProp>
                            <stringProp name="Argument.value">http</stringProp>
                          </elementProp>
                          <elementProp name="BASE_HOST" elementType="Argument">
                            <stringProp name="Argument.name">BASE_HOST</stringProp>
                            <stringProp name="Argument.value">example.test</stringProp>
                          </elementProp>
                        </collectionProp>
                      </elementProp>
                    </TestPlan>
                    <hashTree>
                      <ThreadGroup testname="Enabled users" enabled="true">
                        <stringProp name="ThreadGroup.num_threads">${__P(users,7)}</stringProp>
                        <stringProp name="ThreadGroup.ramp_time">${__P(ramp,15)}</stringProp>
                        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
                          <stringProp name="LoopController.loops">3</stringProp>
                        </elementProp>
                      </ThreadGroup>
                      <hashTree>
                        <ConfigTestElement testname="HTTP Request Defaults">
                          <stringProp name="HTTPSampler.domain">${BASE_HOST}</stringProp>
                          <stringProp name="HTTPSampler.protocol">${BASE_PROTOCOL}</stringProp>
                        </ConfigTestElement>
                        <hashTree/>
                        <HeaderManager testname="Global headers">
                          <collectionProp name="HeaderManager.headers">
                            <elementProp name="" elementType="Header">
                              <stringProp name="Header.name">Accept</stringProp>
                              <stringProp name="Header.value">text/html</stringProp>
                            </elementProp>
                          </collectionProp>
                        </HeaderManager>
                        <hashTree/>
                        <HTTPSamplerProxy testname="Submit form">
                          <stringProp name="HTTPSampler.path">/login</stringProp>
                          <stringProp name="HTTPSampler.method">POST</stringProp>
                          <boolProp name="HTTPSampler.postBodyRaw">false</boolProp>
                          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
                            <collectionProp name="Arguments.arguments">
                              <elementProp name="email" elementType="HTTPArgument">
                                <stringProp name="Argument.name">email</stringProp>
                                <stringProp name="Argument.value">${email}</stringProp>
                              </elementProp>
                            </collectionProp>
                          </elementProp>
                        </HTTPSamplerProxy>
                        <hashTree>
                          <HeaderManager testname="Request headers">
                            <collectionProp name="HeaderManager.headers">
                              <elementProp name="" elementType="Header">
                                <stringProp name="Header.name">Content-Type</stringProp>
                                <stringProp name="Header.value">application/x-www-form-urlencoded</stringProp>
                              </elementProp>
                            </collectionProp>
                          </HeaderManager>
                          <hashTree/>
                        </hashTree>
                      </hashTree>
                      <ThreadGroup testname="Disabled users" enabled="false">
                        <stringProp name="ThreadGroup.num_threads">100</stringProp>
                      </ThreadGroup>
                      <hashTree>
                        <HTTPSamplerProxy testname="Disabled request">
                          <stringProp name="HTTPSampler.path">/disabled</stringProp>
                        </HTTPSamplerProxy>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        JmxTestPlan testPlan = parser.parse(new ByteArrayInputStream(jmx.getBytes(StandardCharsets.UTF_8)));

        assertThat(testPlan.threadGroup().virtualUsers()).isEqualTo(7);
        assertThat(testPlan.threadGroup().rampUpSeconds()).isEqualTo(15);
        assertThat(testPlan.threadGroup().loops()).isEqualTo(3);
        assertThat(testPlan.requests()).hasSize(1);
        assertThat(testPlan.requests().get(0).protocol()).isEqualTo("http");
        assertThat(testPlan.requests().get(0).domain()).isEqualTo("example.test");
        assertThat(testPlan.requests().get(0).parameters()).hasSize(1);
        assertThat(testPlan.requests().get(0).headers()).extracting("name")
                .containsExactly("Accept", "Content-Type");
    }

    @Test
    void parsesRegexExtractorsAttachedToHttpSampler() {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan testname="Correlation Test"/>
                    <hashTree>
                      <ThreadGroup testname="Users"/>
                      <hashTree>
                        <HTTPSamplerProxy testname="Open login">
                          <stringProp name="HTTPSampler.domain">example.test</stringProp>
                          <stringProp name="HTTPSampler.path">/login</stringProp>
                        </HTTPSamplerProxy>
                        <hashTree>
                          <RegexExtractor testname="Extract token">
                            <stringProp name="RegexExtractor.refname">LOGIN_TOKEN</stringProp>
                            <stringProp name="RegexExtractor.regex">login_token=([a-zA-Z0-9]+)</stringProp>
                            <stringProp name="RegexExtractor.template">$1$</stringProp>
                            <stringProp name="RegexExtractor.default">NOT_FOUND</stringProp>
                            <stringProp name="RegexExtractor.match_number">1</stringProp>
                            <stringProp name="RegexExtractor.useHeaders">false</stringProp>
                          </RegexExtractor>
                          <hashTree/>
                        </hashTree>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        JmxTestPlan testPlan = parser.parse(new ByteArrayInputStream(jmx.getBytes(StandardCharsets.UTF_8)));

        assertThat(testPlan.requests()).hasSize(1);
        assertThat(testPlan.requests().get(0).regexExtractors()).hasSize(1);
        assertThat(testPlan.requests().get(0).regexExtractors().get(0).variableName()).isEqualTo("LOGIN_TOKEN");
        assertThat(testPlan.requests().get(0).regexExtractors().get(0).defaultValue()).isEqualTo("NOT_FOUND");
    }

    @Test
    void parsesCsvDataSets() {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan testname="CSV Test"/>
                    <hashTree>
                      <CSVDataSet testname="Users CSV">
                        <stringProp name="filename">D:/Jmeter/bin/users.csv</stringProp>
                        <stringProp name="variableNames">email,password</stringProp>
                        <stringProp name="delimiter">,</stringProp>
                        <boolProp name="ignoreFirstLine">true</boolProp>
                        <boolProp name="recycle">true</boolProp>
                        <boolProp name="stopThread">false</boolProp>
                      </CSVDataSet>
                      <hashTree/>
                      <ThreadGroup testname="Users"/>
                      <hashTree>
                        <HTTPSamplerProxy testname="Open">
                          <stringProp name="HTTPSampler.domain">example.test</stringProp>
                        </HTTPSamplerProxy>
                        <hashTree/>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        JmxTestPlan testPlan = parser.parse(new ByteArrayInputStream(jmx.getBytes(StandardCharsets.UTF_8)));

        assertThat(testPlan.csvDataSets()).hasSize(1);
        assertThat(testPlan.csvDataSets().get(0).filename()).isEqualTo("D:/Jmeter/bin/users.csv");
        assertThat(testPlan.csvDataSets().get(0).variableNames()).containsExactly("email", "password");
        assertThat(testPlan.csvDataSets().get(0).ignoreFirstLine()).isTrue();
    }

    @Test
    void attachesIfControllerConditionToNestedHttpRequests() {
        String jmx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
                  <hashTree>
                    <TestPlan testname="Condition Test"/>
                    <hashTree>
                      <ThreadGroup testname="Users"/>
                      <hashTree>
                        <IfController testname="Has product">
                          <stringProp name="IfController.condition">${__groovy(!'NO_ID'.equals(vars.get('productId')),)}</stringProp>
                        </IfController>
                        <hashTree>
                          <HTTPSamplerProxy testname="Open product">
                            <stringProp name="HTTPSampler.domain">example.test</stringProp>
                            <stringProp name="HTTPSampler.path">/product/${productId}</stringProp>
                          </HTTPSamplerProxy>
                          <hashTree/>
                        </hashTree>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """;

        JmxTestPlan testPlan = parser.parse(new ByteArrayInputStream(jmx.getBytes(StandardCharsets.UTF_8)));

        assertThat(testPlan.requests()).hasSize(1);
        assertThat(testPlan.requests().get(0).conditionExpression()).isEqualTo("variables.productId !== 'NO_ID'");
    }
}
