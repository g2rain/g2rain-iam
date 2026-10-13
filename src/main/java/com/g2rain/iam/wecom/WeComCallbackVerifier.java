package com.g2rain.iam.wecom;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import com.g2rain.iam.enums.RedisKeyRule;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 企业微信回调通用验签解密器：时间窗、签名、防重放、AES 解密与接收方校验。
 */
@Component
public class WeComCallbackVerifier {

    private static final int MAX_PARAM_LENGTH = 4096;
    private static final int MAX_BODY_LENGTH = 65536;

    private final WeComIamProperties properties;
    private final RedisTemplate<String, Object> redisTemplate;

    public WeComCallbackVerifier(
        WeComIamProperties properties,
        RedisTemplate<String, Object> redisTemplate
    ) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    public VerifiedWeComCallback verifyAndDecrypt(
        WeComCallbackCredential credential,
        String msgSignature,
        String timestamp,
        String nonce,
        String encryptedBody
    ) {
        requireCredential(credential);
        requireParam(msgSignature, "msgSignature");
        requireParam(timestamp, "timestamp");
        requireParam(nonce, "nonce");
        requireParam(encryptedBody, "encryptedBody");
        if (encryptedBody.length() > MAX_BODY_LENGTH
            || msgSignature.length() > MAX_PARAM_LENGTH
            || timestamp.length() > MAX_PARAM_LENGTH
            || nonce.length() > MAX_PARAM_LENGTH) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
        }

        validateTimestamp(timestamp);
        String encrypt = extractEncrypt(encryptedBody);
        verifySignature(credential.token(), msgSignature, timestamp, nonce, encrypt);
        if (credential.callbackType() == WeComCallbackType.CUSTOMER_SERVICE) {
            rejectReplay(credential, msgSignature, timestamp, nonce, encrypt);
        }
        return decrypt(credential, encrypt);
    }

    private void validateTimestamp(String timestamp) {
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_TIMESTAMP_INVALID);
        }
        long skew = Math.max(1L, properties.getCustomerService().getTimestampSkewSeconds());
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - epochSeconds) > skew) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_TIMESTAMP_INVALID);
        }
    }

    private void verifySignature(
        String token, String msgSignature, String timestamp, String nonce, String encrypt) {
        try {
            String expected = signature(token, timestamp, nonce, encrypt);
            if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                msgSignature.getBytes(StandardCharsets.US_ASCII))) {
                throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
        }
    }

    private void rejectReplay(
        WeComCallbackCredential credential,
        String msgSignature,
        String timestamp,
        String nonce,
        String encrypt
    ) {
        String fingerprint = fingerprint(credential, msgSignature, timestamp, nonce, encrypt);
        String key = RedisKeyRule.WECOM_CALLBACK_REPLAY.format(fingerprint);
        Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1",
            Duration.ofSeconds(Math.max(1L, properties.getCustomerService().getReplayTtlSeconds())));
        if (Boolean.FALSE.equals(first)) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_REPLAY);
        }
    }

    private VerifiedWeComCallback decrypt(WeComCallbackCredential credential, String encrypt) {
        try {
            byte[] aesKey = Base64.getDecoder().decode(credential.encodingAesKey() + "=");
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                new IvParameterSpec(aesKey, 0, 16));
            byte[] plain = unpad(cipher.doFinal(Base64.getDecoder().decode(encrypt)));
            if (plain.length < 20) {
                throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
            }
            int messageLength = ByteBuffer.wrap(plain, 16, 4).getInt();
            if (messageLength < 0 || 20 + messageLength > plain.length) {
                throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
            }
            String message = new String(plain, 20, messageLength, StandardCharsets.UTF_8);
            String receiver = new String(
                plain, 20 + messageLength,
                plain.length - 20 - messageLength, StandardCharsets.UTF_8);
            if (!credential.expectedReceiver().equals(receiver)) {
                throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
            }
            return new VerifiedWeComCallback(message, receiver);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
        }
    }

    private static String extractEncrypt(String encryptedBody) {
        String trimmed = encryptedBody.trim();
        if (trimmed.startsWith("<")) {
            String encrypt = WeComCallbackCrypto.xmlValue(trimmed, "Encrypt");
            if (Strings.isBlank(encrypt)) {
                throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
            }
            return encrypt;
        }
        return trimmed;
    }

    private static String fingerprint(
        WeComCallbackCredential credential,
        String msgSignature,
        String timestamp,
        String nonce,
        String encrypt
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(credential.callbackType().name().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(nullToEmpty(credential.bindingCode()).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(msgSignature.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(timestamp.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(nonce.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(encrypt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
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
        return HexFormat.of().formatHex(digest.digest());
    }

    private static byte[] unpad(byte[] value) {
        int padding = value[value.length - 1] & 0xff;
        if (padding < 1 || padding > 32 || padding > value.length) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID);
        }
        return Arrays.copyOf(value, value.length - padding);
    }

    private static void requireCredential(WeComCallbackCredential credential) {
        if (credential == null
            || Strings.isBlank(credential.token())
            || Strings.isBlank(credential.encodingAesKey())
            || Strings.isBlank(credential.expectedReceiver())) {
            throw new BusinessException(IamErrorCode.WECOM_CREDENTIAL_MISSING);
        }
    }

    private static void requireParam(String value, String field) {
        if (Strings.isBlank(value)) {
            throw new BusinessException(IamErrorCode.WECOM_CALLBACK_INVALID, field);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
