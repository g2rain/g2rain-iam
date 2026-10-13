package com.g2rain.iam.wecom;

import org.springframework.stereotype.Component;

/**
 * 企业微信授权回调兼容入口：委托通用验证器，固定使用第三方授权凭据。
 */
@Component
public class WeComCallbackCrypto {
    private final WeComCallbackCredentialResolver credentialResolver;
    private final WeComCallbackVerifier callbackVerifier;

    public WeComCallbackCrypto(
        WeComCallbackCredentialResolver credentialResolver,
        WeComCallbackVerifier callbackVerifier
    ) {
        this.credentialResolver = credentialResolver;
        this.callbackVerifier = callbackVerifier;
    }

    public String decryptMessage(
        String signature, String timestamp, String nonce, String xmlBody) {
        WeComCallbackCredential credential = credentialResolver.resolve(
            WeComCallbackType.THIRD_PARTY_AUTHORIZATION, null);
        return callbackVerifier.verifyAndDecrypt(
            credential, signature, timestamp, nonce, xmlBody).plainBody();
    }

    public String decryptEcho(
        String signature, String timestamp, String nonce, String encryptedEcho) {
        WeComCallbackCredential credential = credentialResolver.resolve(
            WeComCallbackType.THIRD_PARTY_AUTHORIZATION, null);
        return callbackVerifier.verifyAndDecrypt(
            credential, signature, timestamp, nonce, encryptedEcho).plainBody();
    }

    public static String xmlValue(String xml, String tag) {
        try {
            javax.xml.parsers.DocumentBuilderFactory factory =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            org.w3c.dom.Document document = factory.newDocumentBuilder().parse(
                new java.io.ByteArrayInputStream(
                    xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var nodes = document.getElementsByTagName(tag);
            return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
        } catch (Exception exception) {
            throw new com.g2rain.common.exception.BusinessException(
                com.g2rain.iam.enums.IamErrorCode.WECOM_CALLBACK_INVALID);
        }
    }
}
