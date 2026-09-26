package com.g2rain.iam.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 钉钉内嵌扫码引导请求 DTO：须在授权事务内发起。
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
     * 授权事务 ID；服务端从事务还原 clientId/redirectUri 等。
     */
    @NotBlank
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "授权事务 tid")
    private String tid;

    /**
     * 登录意图 USER|ADMIN，默认 USER
     */
    @Schema(description = "登录意图 USER|ADMIN，默认 USER")
    private String loginRole;
}
