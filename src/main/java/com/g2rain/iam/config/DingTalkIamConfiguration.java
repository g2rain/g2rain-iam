package com.g2rain.iam.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 钉钉 IAM 相关 Bean。
 */
@Configuration
@EnableConfigurationProperties({DingTalkIamProperties.class, IamAccessProperties.class})
public class DingTalkIamConfiguration {

    @Bean
    public RestClient dingTalkRestClient(RestClient.Builder restClientBuilder) {
        return restClientBuilder.build();
    }
}
