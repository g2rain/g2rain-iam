package com.g2rain.iam.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 钉钉 OAuth 发起阶段写入 Redis 的 state 载荷（与钉钉回调中的 state 对应），
 * 并携带后续 {@code /auth/authorize} 所需的 OAuth 参数。
 */
@Getter
@Setter
@NoArgsConstructor
public class DingTalkOAuthStateDto {

    private String bindMode;

    /**
     * OAuth2 客户端 ID，与 {@code /auth/authorize} 的 {@code clientId} 一致。
     */
    private String clientId;

    /**
     * OAuth2 客户端回调地址，与 {@code /auth/authorize} 的 {@code redirectUri} 一致。
     */
    private String clientRedirectUri;

    /**
     * 业务系统传入的 state，原样回传。
     */
    private String clientState;
}
