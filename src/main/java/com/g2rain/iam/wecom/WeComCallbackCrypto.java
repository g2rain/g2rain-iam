package com.g2rain.iam.wecom;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

@Component
public class WeComCallbackCrypto {
    private final WeComIamProperties properties;

    public WeComCallbackCrypto(WeComIamProperties properties) {
        this.properties = properties;
    }

    public String decryptMessage(
        String signature, String timestamp, String nonce, String xmlBody) {
        return decrypt(signature, timestamp, nonce, xmlValue(xmlBody, "Encrypt"));
    }

    public String decryptEcho(
        String signature, String timestamp, String nonce, String encryptedEcho) {
        return decrypt(signature, timestamp, nonce, encryptedEcho);
    }

    private String decrypt(
        String signature, String timestamp, String nonce, String encrypted) {
        try {
            String token = properties.getThirdParty().getToken();
            String expected = signature(token, timestamp, nonce, encrypted);
            if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
                throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
            }
            byte[] aesKey = Base64.getDecoder().decode(
                properties.getThirdParty().getEncodingAesKey() + "=");
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                new IvParameterSpec(aesKey, 0, 16));
            byte[] plain = unpad(cipher.doFinal(Base64.getDecoder().decode(encrypted)));
            if (plain.length < 20) {
                throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
            }
            int messageLength = ByteBuffer.wrap(plain, 16, 4).getInt();
            if (messageLength < 0 || 20 + messageLength > plain.length) {
                throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
            }
            String message = new String(
                plain, 20, messageLength, StandardCharsets.UTF_8);
            String receiver = new String(
                plain, 20 + messageLength,
                plain.length - 20 - messageLength, StandardCharsets.UTF_8);
            if (!properties.getThirdParty().getSuiteId().equals(receiver)) {
                throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
            }
            return message;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
        }
    }

    private static String signature(String... values) throws Exception {
        Arrays.sort(values);
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        for (String value : values) {
            digest.update(value.getBytes(StandardCharsets.UTF_8));
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static byte[] unpad(byte[] value) {
        int padding = value[value.length - 1] & 0xff;
        if (padding < 1 || padding > 32 || padding > value.length) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
        }
        return Arrays.copyOf(value, value.length - padding);
    }

    public static String xmlValue(String xml, String tag) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            var nodes = document.getElementsByTagName(tag);
            return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
        } catch (Exception exception) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
        }
    }
}
