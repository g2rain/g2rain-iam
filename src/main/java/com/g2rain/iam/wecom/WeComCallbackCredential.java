package com.g2rain.iam.wecom;

/**
 * 企业微信回调解密凭据（不含业务明文）。
 */
public record WeComCallbackCredential(
    WeComCallbackType callbackType,
    String bindingCode,
    String token,
    String encodingAesKey,
    String expectedReceiver,
    String enterpriseId,
    String bindMode
) {
}
