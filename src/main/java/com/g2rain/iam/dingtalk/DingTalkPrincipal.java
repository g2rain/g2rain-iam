package com.g2rain.iam.dingtalk;

/**
 * 钉钉换票完成后，IAM 内部统一使用的用户主体（与 Basis 表 {@code passport_idp_binding} 字段语义对齐）。
 *
 * @param unionId              稳定主体，建议与 Basis {@code idp_subject} 一致
 * @param openId               开放平台 openId（若有）
 * @param corpId               企业 corpId（若有）
 * @param nick                 展示名
 * @param bindMode             {@link com.g2rain.basis.enums.IdpBindMode} 名称
 * @param rawJson              用户信息原始 JSON 快照（可审计）
 * @param idpApplicationCode   三方应用在 IdP 侧的应用标识（如钉钉 OAuth clientId），与 Basis {@code idp_application_code} 一致
 */
public record DingTalkPrincipal(
    String unionId,
    String openId,
    String corpId,
    String nick,
    String bindMode,
    String rawJson,
    String idpApplicationCode
) {
}
