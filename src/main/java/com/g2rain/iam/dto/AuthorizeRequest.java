package com.g2rain.iam.dto;

import com.g2rain.iam.enums.AuthorizationMode;

/**
 * 首次进入 {@code /auth/authorize} 的授权请求参数。
 *
 * @param clientId        客户端 ID（DPoP kid）
 * @param redirectUri     授权后回调地址
 * @param state           业务 state（仅用于回传 CSRF 值）
 * @param applicationCode 目标应用编码（可选）
 * @param mode            授权模式
 * @param entryKey        匿名接入码（仅 {@code ANONYMOUS}）
 * @param jkt             客户端公钥指纹（RFC 7638）；未携带公钥时为 {@code null}
 */
public record AuthorizeRequest(
    String clientId,
    String redirectUri,
    String state,
    String applicationCode,
    AuthorizationMode mode,
    String entryKey,
    String jkt) {
}
