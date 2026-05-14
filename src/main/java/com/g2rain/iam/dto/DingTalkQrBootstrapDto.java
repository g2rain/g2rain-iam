package com.g2rain.iam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 登录页内嵌钉钉扫码：向 IAM 申请 {@code goto} 授权 URL（与跳转授权共用 Redis state 与回调换票逻辑）。
 */
@Getter
@Setter
@NoArgsConstructor
public class DingTalkQrBootstrapDto {

    @NotBlank
    private String bindMode;

    @NotBlank
    private String clientId;

    @NotBlank
    private String redirectUri;

    private String state;
}
