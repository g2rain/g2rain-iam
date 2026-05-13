package com.g2rain.iam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 钉钉 Stream 场景下发 OAuth 授权码的请求体。
 */
@Setter
@Getter
@NoArgsConstructor
public class DingTalkStreamAuthorizationRequest {

    /**
     * OAuth 客户端 ID（与换 token 时 DPoP kid 一致）。
     */
    @NotBlank
    private String clientId;

    /**
     * OAuth state，原样返回。
     */
    private String state;

    /**
     * IdP 接入形态，{@link com.g2rain.basis.enums.IdpBindMode} 枚举名。
     */
    @NotBlank
    private String bindMode;

    /**
     * 钉钉 unionId，与 Basis passport_idp_binding.idp_subject 一致。
     */
    @NotBlank
    private String unionId;

    /**
     * 钉钉 corpId，可选。
     */
    private String corpId;
}
