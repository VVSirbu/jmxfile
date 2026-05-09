package com.vvs;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(
        info = @Info(
                title = "JMX to k6 Converter API",
                version = "1.0",
                description = "Converts JMeter JMX files into k6 JavaScript scripts"
        )
)
@SpringBootApplication
public class JmxToK6ChangerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JmxToK6ChangerApplication.class, args);
    }
}
