package com.g2rain.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 钉钉 Stream 授权码请求 DTO
 *
 * @author Alpha
 */
@Setter
@Getter
@NoArgsConstructor
@Schema(description = "钉钉 Stream 发码请求 DTO")
public class DingTalkStreamAuthorizationDto {

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
     * 钉钉 unionId（与 Basis passport_idp_binding.idp_subject 一致）
     */
    @NotBlank
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "钉钉 unionId")
    private String unionId;

    /**
     * 钉钉企业 corpId
     */
    @Schema(description = "钉钉企业 corpId")
    private String corpId;
}
