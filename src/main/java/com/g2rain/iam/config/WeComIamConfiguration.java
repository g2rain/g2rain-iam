package com.g2rain.iam.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(WeComIamProperties.class)
public class WeComIamConfiguration {

    @Bean("weComRestClient")
    public RestClient weComRestClient() {
        return RestClient.builder().build();
    }
}
