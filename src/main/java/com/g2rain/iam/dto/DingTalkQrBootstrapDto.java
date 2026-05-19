package com.g2rain.iam.dto;

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
public class DingTalkQrBootstrapDto {

    /**
     * IdP 接入形态[INTERNAL:企业内部应用, THIRD_PARTY:第三方企业应用]
     */
    @NotBlank
    private String bindMode;

    /**
     * OAuth2 客户端 ID
     */
    @NotBlank
    private String clientId;

    /**
     * OAuth2 客户端回调地址
     */
    @NotBlank
    private String redirectUri;

    /**
     * 业务系统传入的 state
     */
    private String state;
}
