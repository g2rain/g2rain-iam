package com.g2rain.iam.dto;

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
public class DingTalkStreamAuthorizationDto {

    /**
     * OAuth2 客户端 ID（与换 token 时 DPoP kid 一致）
     */
    @NotBlank
    private String clientId;

    /**
     * 业务系统传入的 state
     */
    private String state;

    /**
     * IdP 接入形态[INTERNAL:企业内部应用, THIRD_PARTY:第三方企业应用]
     */
    @NotBlank
    private String bindMode;

    /**
     * 钉钉 unionId（与 Basis passport_idp_binding.idp_subject 一致）
     */
    @NotBlank
    private String unionId;

    /**
     * 钉钉企业 corpId
     */
    private String corpId;
}
