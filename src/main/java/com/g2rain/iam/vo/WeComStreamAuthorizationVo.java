package com.g2rain.iam.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 企业微信 Stream 授权码返回 VO
 *
 * @param code  OAuth 授权码
 * @param state 业务系统 state（与请求一致，可为 null）
 */
@Schema(description = "企业微信 Stream 授权码 VO")
public record WeComStreamAuthorizationVo(
    @Schema(description = "OAuth 授权码") String code,
    @Schema(description = "业务系统 state") String state
) {
}
