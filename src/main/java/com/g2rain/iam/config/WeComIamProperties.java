package com.g2rain.iam.config;

import com.g2rain.iam.utils.IamUrlUtils;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "g2rain.iam.wecom")
public class WeComIamProperties {
    private String callbackPath = "/auth/wecom/callback";
    private String authorizationCallbackPath =
        "/auth/wecom/third-party/authorization/callback";
    private String loginPageBindMode = "";
    private final Internal internal = new Internal();
    private final ThirdParty thirdParty = new ThirdParty();
    private final Credential credential = new Credential();
    private final CustomerService customerService = new CustomerService();

    public String fullCallbackUrl(String baseUrl) {
        return IamUrlUtils.joinAbsoluteUrl(baseUrl, callbackPath);
    }

    public String fullAuthorizationCallbackUrl(String baseUrl) {
        return IamUrlUtils.joinAbsoluteUrl(baseUrl, authorizationCallbackPath);
    }

    @Getter
    @Setter
    public static class Internal {
        private String corpId = "";
        private String agentId = "";
        private String secret = "";
    }

    @Getter
    @Setter
    public static class ThirdParty {
        private String providerCorpId = "";
        private String providerSecret = "";
        private String suiteId = "";
        private String suiteSecret = "";
        private String token = "";
        private String encodingAesKey = "";
    }

    @Getter
    @Setter
    public static class Credential {
        private String encryptionKey = "";
        private String keyId = "default";
    }

    @Getter
    @Setter
    public static class CustomerService {
        private boolean enabled = false;
        private long timestampSkewSeconds = 300;
        private long replayTtlSeconds = 600;
        private long memberResolveCodeTtlSeconds = 300;
        private long memberTokenTtlSeconds = 1800;
        private java.util.List<CustomerServiceBinding> bindings = new java.util.ArrayList<>();
    }

    @Getter
    @Setter
    public static class CustomerServiceBinding {
        private String bindingCode = "";
        private String token = "";
        private String encodingAesKey = "";
        private String expectedReceiver = "";
        private String enterpriseId = "";
        private String bindMode = "THIRD_PARTY";
    }
}
