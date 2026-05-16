package com.g2rain.iam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 登录页内嵌钉钉扫码（方式二）：向 IAM 申请 {@code sns_authorize} 的 {@code goto} URL（与浏览器方式一共用 Redis state 与回调换票逻辑）。
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
