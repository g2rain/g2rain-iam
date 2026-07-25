package com.g2rain.iam.wecom;

import com.g2rain.basis.enums.IdpBindMode;
import com.g2rain.basis.enums.IdpType;
import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.idp.IdpPrincipal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 企业微信换票结果（企业微信适配层内部模型）。
 * 出站至认证/会话链路时请转换为 {@link IdpPrincipal}。
 */
public record WeComPrincipal(
    String corpId,
    String userId,
    String openUserId,
    String displayName,
    String bindMode,
    String idpApplicationCode,
    String installedApplicationId,
    String rawProfile
) {
    public IdpPrincipal toIdpPrincipal() {
        IdpBindMode mode = IdpBindMode.valueOf(bindMode);
        String subject;
        if (mode == IdpBindMode.THIRD_PARTY && Strings.isNotBlank(openUserId)) {
            subject = openUserId.trim();
        } else {
            subject = stableSubject(corpId, userId);
        }
        return new IdpPrincipal(
            IdpType.WECHAT_WORK.name(),
            subject,
            Strings.isNotBlank(openUserId) ? openUserId.trim() : userId,
            openUserId,
            corpId,
            displayName,
            mode.name(),
            rawProfile,
            idpApplicationCode
        );
    }

    public static String stableSubject(String enterpriseId, String userId) {
        if (Strings.isBlank(enterpriseId) || Strings.isBlank(userId)) {
            throw new BusinessException(IamErrorCode.WECOM_USERINFO_FAILED);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = (enterpriseId.trim() + '\0' + userId.trim())
                .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(IamErrorCode.WECOM_USERINFO_FAILED);
        }
    }
}
