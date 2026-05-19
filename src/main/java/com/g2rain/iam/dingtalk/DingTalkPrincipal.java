package com.g2rain.iam.dingtalk;

/**
 * 钉钉换票用户主体
 * 功能：IAM 内部统一用户表示，字段语义与 Basis 表 passport_idp_binding 对齐
 *
 * @author Alpha
 * @param unionId            稳定主体（建议与 Basis idp_subject 一致）
 * @param openId             开放平台 openId
 * @param corpId             企业 corpId
 * @param nick               展示名
 * @param bindMode           IdP 接入形态，{@link com.g2rain.basis.enums.IdpBindMode} 枚举名
 * @param rawJson            用户信息原始 JSON 快照
 * @param idpApplicationCode 三方应用在 IdP 侧的应用标识（如钉钉 OAuth clientId）
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
