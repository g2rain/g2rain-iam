package com.g2rain.iam.dingtalk;

import com.g2rain.basis.enums.IdpBindMode;

/**
 * 钉钉登录换票适配器：企业内部应用与第三方企业应用两套实现，由 {@link IdpBindMode} 选择。
 */
public interface DingTalkLoginAdapter {

    /**
     * 本适配器对应的接入形态。
     */
    IdpBindMode bindMode();

    /**
     * 方式一：构造钉钉 {@code login.dingtalk.com/oauth2/auth} 授权 URL（浏览器整页跳转）。
     *
     * @param state                 防 CSRF 的 opaque state（由 IAM 写入 Redis）
     * @param redirectUriForDingTalk 须在钉钉开放平台配置的 IAM 回调地址
     */
    String buildAuthorizeUrl(String state, String redirectUriForDingTalk);

    /**
     * 方式二：内嵌扫码 {@code DDLogin} 的 {@code goto}，须为 {@code oapi.../sns_authorize}，{@code scope=snsapi_login}，
     * 扫码后拼接 {@code loginTmpCode} 再跳转；回调仍回落同一 {@code redirect_uri}。
     */
    String buildQrEmbeddedAuthorizeUrl(String state, String redirectUriForDingTalk);

    /**
     * 使用授权码完成换票并解析用户主体（默认方式一浏览器 OAuth）。
     */
    default DingTalkPrincipal exchangeCodeForPrincipal(String authCode) {
        return exchangeCodeForPrincipal(authCode, false);
    }

    /**
     * @param snsQrLogin {@code true} 时按方式二 sns 临时码调用 {@code getuserinfo_bycode}，否则 {@code userAccessToken} + {@code users/me}
     */
    DingTalkPrincipal exchangeCodeForPrincipal(String authCode, boolean snsQrLogin);
}
