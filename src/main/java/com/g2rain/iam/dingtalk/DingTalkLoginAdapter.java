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
     * 构造跳转钉钉授权页的 URL（已包含 state、redirect_uri 等）。
     *
     * @param state                 防 CSRF 的 opaque state（由 IAM 写入 Redis）
     * @param redirectUriForDingTalk 须在钉钉开放平台配置的 IAM 回调地址
     */
    String buildAuthorizeUrl(String state, String redirectUriForDingTalk);

    /**
     * 内嵌扫码 {@code DDLogin} 的 {@code goto} 授权 URL（钉钉 {@code oapi} {@code sns_authorize}，使用 {@code appid}）。
     */
    String buildQrEmbeddedAuthorizeUrl(String state, String redirectUriForDingTalk);

    /**
     * 使用授权码完成换票并解析用户主体。
     */
    DingTalkPrincipal exchangeCodeForPrincipal(String authCode);
}
