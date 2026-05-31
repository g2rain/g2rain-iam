package com.g2rain.iam.dingtalk;


import com.g2rain.basis.enums.IdpType;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.idp.IdpPrincipal;

/**
 * 钉钉换票结果（钉钉适配层内部模型）。
 * <p>
 * 出站至认证/会话链路时请转换为 {@link IdpPrincipal}。
 * </p>
 *
 * @author Alpha
 * @param unionId            钉钉稳定主体（映射为 {@link IdpPrincipal#idpSubject()}）
 * @param openId             开放平台 openId（映射为 {@link IdpPrincipal#idpUserId()}）
 * @param corpId             企业 corpId
 * @param nick               展示名（映射为 {@link IdpPrincipal#displayName()}）
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

    /**
     * 转换为平台无关的 IdP 主体
     */
    public IdpPrincipal toIdpPrincipal() {
        return new IdpPrincipal(
            IdpType.DINGTALK.name(),
            unionId,
            Strings.isBlank(openId) ? null : openId.trim(),
            Strings.isBlank(corpId) ? null : corpId.trim(),
            nick,
            bindMode,
            Strings.isBlank(rawJson) ? "{}" : rawJson,
            idpApplicationCode == null ? "" : idpApplicationCode.trim()
        );
    }
}
