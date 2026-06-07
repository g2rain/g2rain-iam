package com.g2rain.iam.idp;


/**
 * IdP 登录后的 IAM 内部用户主体，字段语义与 Basis {@code passport_idp_binding} 对齐。
 *
 * @param idpType              身份源类型，如 {@link com.g2rain.basis.enums.IdpType#DINGTALK}
 * @param idpSubject           IdP 稳定主体（钉钉 unionId 等）
 * @param idpUserId            IdP 用户 ID（钉钉 openId 等，可空）
 * @param corpId               企业/租户标识（可空）
 * @param displayName          展示名（可空）
 * @param bindMode             接入形态，{@link com.g2rain.basis.enums.IdpBindMode} 枚举名
 * @param rawProfile           用户信息原始 JSON 快照
 * @param idpApplicationCode   三方应用在 IdP 侧的应用标识（如钉钉 OAuth clientId）
 */
public record IdpPrincipal(
    String idpType,
    String idpSubject,
    String idpUserId,
    String corpId,
    String displayName,
    String bindMode,
    String rawProfile,
    String idpApplicationCode
) {
}
