package com.g2rain.iam.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 钉钉 OAuth State 缓存 DTO
 * 存储: Redis，键规则见 {@link com.g2rain.iam.enums.RedisKeyRule#DINGTALK_OAUTH_STATE}
 *
 * @author Alpha
 */
@Getter
@Setter
@NoArgsConstructor
public class DingTalkOAuthStateDto {

    /**
     * IdP 接入形态[INTERNAL:企业内部应用, THIRD_PARTY:第三方企业应用]
     */
    private String bindMode;

    /**
     * OAuth2 客户端 ID
     */
    private String clientId;

    /**
     * OAuth2 客户端回调地址
     */
    private String redirectUri;

    /**
     * 业务系统传入的 state
     */
    private String state;

    /**
     * 是否内嵌扫码登录[true:方式二 sns_authorize, false:方式一浏览器 OAuth]
     */
    private Boolean qrEmbedded;
}
