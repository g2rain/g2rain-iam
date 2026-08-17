package com.g2rain.iam.wecom;

import com.g2rain.basis.enums.IdpBindMode;

/**
 * 企业微信登录换票适配器接口
 * 功能：按 {@link IdpBindMode} 区分企业内部应用与服务商三方应用换票链路
 */
public interface WeComLoginAdapter {

    /**
     * 本适配器对应的接入形态
     */
    IdpBindMode bindMode();

    /**
     * 构造企业微信扫码授权 URL
     *
     * @param state       IAM opaque state
     * @param callbackUrl IAM 回调地址
     * @return 企业微信授权页完整 URL
     */
    String buildAuthorizeUrl(String state, String callbackUrl);

    /**
     * 使用授权码换票并解析用户主体
     *
     * @param authCode 企业微信回调中的 code 或 auth_code
     * @return 企业微信用户主体
     */
    WeComPrincipal exchangeCodeForPrincipal(String authCode);
}
