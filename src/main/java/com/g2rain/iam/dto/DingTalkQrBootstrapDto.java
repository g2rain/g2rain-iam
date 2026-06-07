package com.g2rain.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 钉钉内嵌扫码引导请求 DTO
 *
 * @author Alpha
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "钉钉内嵌扫码引导请求 DTO")
public class DingTalkQrBootstrapDto {

    /**
     * IdP 接入形态[INTERNAL:企业内部应用, THIRD_PARTY:第三方企业应用]
     */
    @NotBlank
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "IdP 接入形态，IdpBindMode 枚举名")
    private String bindMode;

    /**
     * OAuth2 客户端 ID
     */
    @NotBlank
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "OAuth2 客户端 ID")
    private String clientId;

    /**
     * OAuth2 客户端回调地址
     */
    @NotBlank
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "OAuth2 回调地址")
    private String redirectUri;

    /**
     * 业务系统传入的 state
     */
    @Schema(description = "业务系统 state")
    private String state;
}
