package com.g2rain.iam.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 {@link AuthPolicyProperties}。
 */
@Configuration
@EnableConfigurationProperties(AuthPolicyProperties.class)
public class AuthPolicyConfiguration {
}
