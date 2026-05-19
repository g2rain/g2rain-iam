package com.g2rain.iam.dingtalk;

import com.g2rain.basis.enums.IdpBindMode;

/**
 * 钉钉登录换票适配器接口
 * 功能：按 {@link IdpBindMode} 区分企业内部应用与第三方企业应用换票链路
 *
 * @author Alpha
 */
public interface DingTalkLoginAdapter {

    /**
     * 本适配器对应的接入形态
     *
     * @return IdP 接入形态枚举
     */
    IdpBindMode bindMode();

    /**
     * 构造方式一浏览器 OAuth 授权 URL（login.dingtalk.com/oauth2/auth）
     *
     * @param state                  IAM opaque state
     * @param redirectUriForDingTalk 须在钉钉开放平台配置的 IAM 回调地址
     * @return 钉钉授权页完整 URL
     */
    String buildAuthorizeUrl(String state, String redirectUriForDingTalk);

    /**
     * 构造方式二内嵌扫码 sns_authorize 的 goto URL
     *
     * @param state                  IAM opaque state
     * @param redirectUriForDingTalk IAM 回调地址
     * @return sns 授权 goto 完整 URL
     */
    String buildQrEmbeddedAuthorizeUrl(String state, String redirectUriForDingTalk);

    /**
     * 使用授权码换票并解析用户主体（默认方式一浏览器 OAuth）
     *
     * @param authCode 钉钉授权码
     * @return 钉钉用户主体
     */
    default DingTalkPrincipal exchangeCodeForPrincipal(String authCode) {
        return exchangeCodeForPrincipal(authCode, false);
    }

    /**
     * 使用授权码换票并解析用户主体
     *
     * @param authCode   钉钉授权码
     * @param snsQrLogin {@code true} 时走 SNS getuserinfo_bycode；{@code false} 时走 userAccessToken + users/me
     * @return 钉钉用户主体
     */
    DingTalkPrincipal exchangeCodeForPrincipal(String authCode, boolean snsQrLogin);
}
