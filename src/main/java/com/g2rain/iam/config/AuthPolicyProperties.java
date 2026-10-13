package com.g2rain.iam.config;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按 {@code applicationCode} 的登录/注册策略配置。
 * <p>前缀 {@code g2rain.iam.auth-policy}。键只用 applicationCode，不用 applicationId。</p>
 */
@Setter
@ConfigurationProperties(prefix = "g2rain.iam.auth-policy")
public class AuthPolicyProperties {

    /**
     * 未命中专条时整段使用的默认策略。
     */
    @NestedConfigurationProperty
    private AuthPolicyEntry defaultEntry = AuthPolicyEntry.platformDefault();

    /**
     * 按 applicationCode 的完整策略专条；命中后不再读 default。
     */
    private Map<String, AuthPolicyEntry> applications = new LinkedHashMap<>();

    /**
     * JavaBean 属性名 {@code default}，绑定 YAML {@code auth-policy.default}。
     *
     * @return default 条目，不会为 null
     */
    public AuthPolicyEntry getDefault() {
        return defaultEntry == null ? AuthPolicyEntry.platformDefault() : defaultEntry;
    }

    /**
     * 绑定 YAML {@code default} 段。
     *
     * @param defaultEntry default 条目；null 时回退出厂值
     */
    public void setDefault(AuthPolicyEntry defaultEntry) {
        this.defaultEntry = defaultEntry == null ? AuthPolicyEntry.platformDefault() : defaultEntry;
    }

    /**
     * applicationCode → 专条。
     *
     * @return 可变 Map，不会为 null
     */
    public Map<String, AuthPolicyEntry> getApplications() {
        if (applications == null) {
            applications = new LinkedHashMap<>();
        }
        return applications;
    }
}
