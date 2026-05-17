package com.g2rain.iam.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 钉钉登录与换票相关配置（支持 Nacos / 环境变量覆盖）。
 * <p>
     * {@code bind_mode} 与 {@link com.g2rain.basis.enums.IdpBindMode} 一致：{@code INTERNAL} 使用 {@link #internal}
     * 凭证；{@code THIRD_PARTY} 使用 {@link #thirdParty} 凭证。两条链路均为钉钉 OAuth + 换票，非 OAuth「两跳」语义。
     * 授权链接中的 {@code appid} 与 sns {@code accessKey} 均使用各凭证的 {@code client-id}（AppKey）。
 * </p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "g2rain.iam.dingtalk")
public class DingTalkIamProperties {

    /**
     * 钉钉授权页 URL（浏览器整页跳转，通常 {@code login.dingtalk.com/oauth2/auth}，使用 {@code client_id}）。
     */
    private String authorizeUrl = "https://login.dingtalk.com/oauth2/auth";

    /**
     * 换取用户访问令牌。
     */
    private String userAccessTokenUrl = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";

    /**
     * 当前登录用户身份（方式一浏览器 OAuth，须 token 含 {@code Contact.User.Read}）。
     */
    private String userMeUrl = "https://api.dingtalk.com/v1.0/contact/users/me";

    /**
     * IAM 暴露给钉钉的回调路径（与 {@link #callbackBaseUrl} 拼接后须与钉钉开放平台配置一致）。
     */
    private String callbackPath = "/auth/dingtalk/callback";

    /**
     * 钉钉 OAuth 回调使用的 IAM 根地址（与 {@link IamAccessProperties#getAccessBaseUrl()} 通常一致；
     * 也可单独配置以支持回调域名与对外访问域名不同）。
     */
    private String callbackBaseUrl = "http://127.0.0.1:8082";

    /**
     * 企业内部应用凭证。
     */
    private final Credential internal = new Credential();

    /**
     * 第三方企业应用凭证。
     */
    private final Credential thirdParty = new Credential();

    /**
     * 登录页「钉钉登录」链接使用的 {@link com.g2rain.basis.enums.IdpBindMode} 枚举名（{@code INTERNAL} / {@code THIRD_PARTY}）。
     * 为空或未配置时不展示登录页钉钉入口；非空时须与 Nacos 中已启用的钉钉 OAuth 应用一致。
     */
    private String loginPageBindMode = "";

    /**
     * 单套 clientId / clientSecret。
     */
    @Getter
    @Setter
    public static class Credential {
        private String clientId = "";
        private String clientSecret = "";
    }

    /**
     * 钉钉回调完整 URL（作为授权请求中的 redirect_uri）。
     */
    public String fullCallbackUrl() {
        String base = callbackBaseUrl.endsWith("/") ? callbackBaseUrl.substring(0, callbackBaseUrl.length() - 1) : callbackBaseUrl;
        String path = callbackPath.startsWith("/") ? callbackPath : "/" + callbackPath;
        return base + path;
    }
}
