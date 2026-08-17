package com.g2rain.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 企业微信 Stream 授权码请求 DTO
 */
@Setter
@Getter
@NoArgsConstructor
@Schema(description = "企业微信 Stream 发码请求 DTO")
public class WeComStreamAuthorizationDto {

    /**
     * OAuth2 客户端 ID（与换 token 时 DPoP kid 一致）
     */
    @NotBlank
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "OAuth2 客户端 ID")
    private String clientId;

    /**
     * 业务系统传入的 state
     */
    @Schema(description = "业务系统 state")
    private String state;

    /**
     * IdP 接入形态[INTERNAL:企业内部应用, THIRD_PARTY:第三方企业应用]
     */
    @NotBlank
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "IdP 接入形态，IdpBindMode 枚举名")
    private String bindMode;

    /**
     * 企业微信企业 corpId
     */
    @NotBlank
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "企业微信企业 corpId")
    private String corpId;

    /**
     * 企业微信成员 userId
     */
    @NotBlank
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "企业微信成员 userId")
    private String userId;
}
