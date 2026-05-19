package com.g2rain.iam.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 钉钉 IAM 配置属性
 * 配置前缀: g2rain.iam.dingtalk
 *
 * @author Alpha
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "g2rain.iam.dingtalk")
public class DingTalkIamProperties {

    /**
     * 钉钉浏览器 OAuth 授权页 URL
     */
    private String authorizeUrl = "https://login.dingtalk.com/oauth2/auth";

    /**
     * 换取用户访问令牌接口地址
     */
    private String userAccessTokenUrl = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";

    /**
     * 当前登录用户身份接口地址（方式一浏览器 OAuth）
     */
    private String userMeUrl = "https://api.dingtalk.com/v1.0/contact/users/me";

    /**
     * IAM 暴露给钉钉的回调路径（与 g2rain.iam.base-url 拼接）
     */
    private String callbackPath = "/auth/dingtalk/callback";

    /**
     * 企业内部应用凭证
     */
    private final Credential internal = new Credential();

    /**
     * 第三方企业应用凭证
     */
    private final Credential thirdParty = new Credential();

    /**
     * 登录页钉钉入口使用的 IdP 接入形态[INTERNAL, THIRD_PARTY]；为空时不展示入口
     */
    private String loginPageBindMode = "";

    /**
     * 钉钉 OAuth 单套 clientId / clientSecret
     */
    @Getter
    @Setter
    public static class Credential {

        /**
         * 应用 clientId（AppKey）
         */
        private String clientId = "";

        /**
         * 应用 clientSecret（AppSecret）
         */
        private String clientSecret = "";
    }

    /**
     * 拼接钉钉回调完整 URL
     *
     * @param iamBaseUrl IAM 对外根 URL，见 {@link IamAccessProperties#normalizedBaseUrl()}
     * @return 钉钉 redirect_uri
     */
    public String fullCallbackUrl(String iamBaseUrl) {
        String base = iamBaseUrl == null ? "" : iamBaseUrl.trim();
        String path = callbackPath.startsWith("/") ? callbackPath : "/" + callbackPath;
        return base + path;
    }
}
