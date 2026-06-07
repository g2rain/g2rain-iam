package com.g2rain.iam.service.idp;


import com.g2rain.iam.idp.IdpPrincipal;

/**
 * 身份源认证：将 {@link IdpPrincipal} 解析为平台 {@code passportId}（含必要时自动注册与绑定）。
 */
public interface IdpAuthService {

    /**
     * 是否支持该身份源类型
     */
    boolean supports(String idpType);

    /**
     * @param principal                    IdP 用户主体
     * @param autoProvisionMissingPassport {@code true} 无绑定时自动建号；{@code false} 必须已绑定
     * @return passportId 字符串
     */
    String resolvePassportId(IdpPrincipal principal, boolean autoProvisionMissingPassport);
}
