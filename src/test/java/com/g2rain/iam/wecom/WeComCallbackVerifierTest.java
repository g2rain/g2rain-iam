package com.g2rain.iam.wecom;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeComCallbackVerifierTest {

    private static final String TOKEN = "callback-token";
    private static final String AES_KEY_TEXT = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static final String RECEIVER = "ww-suite-or-corp";

    @Test
    void verifyAndDecryptAcceptsValidCustomerServicePayload() throws Exception {
        WeComCallbackVerifier verifier = verifier(false);
        String plain = "<xml><ToUserName><![CDATA[ww-corp]]></ToUserName></xml>";
        String encrypt = encrypt(plain, RECEIVER);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-1";
        String signature = signature(TOKEN, timestamp, nonce, encrypt);

        VerifiedWeComCallback verified = verifier.verifyAndDecrypt(
            credential(WeComCallbackType.CUSTOMER_SERVICE),
            signature, timestamp, nonce, encrypt);

        assertEquals(plain, verified.plainBody());
        assertEquals(RECEIVER, verified.receiver());
    }

    @Test
    void rejectExpiredTimestamp() {
        WeComCallbackVerifier verifier = verifier(false);
        BusinessException ex = assertThrows(BusinessException.class,
            () -> verifier.verifyAndDecrypt(
                credential(WeComCallbackType.CUSTOMER_SERVICE),
                "sig", "1", "nonce", "cipher"));
        assertEquals(IamErrorCode.WECOM_CALLBACK_TIMESTAMP_INVALID.code(), ex.getErrorCode());
    }

    @Test
    void rejectReplayForCustomerService() throws Exception {
        WeComCallbackVerifier verifier = verifier(true);
        String plain = "<xml><ToUserName><![CDATA[ww-corp]]></ToUserName></xml>";
        String encrypt = encrypt(plain, RECEIVER);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-2";
        String signature = signature(TOKEN, timestamp, nonce, encrypt);
        WeComCallbackCredential credential = credential(WeComCallbackType.CUSTOMER_SERVICE);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> verifier.verifyAndDecrypt(credential, signature, timestamp, nonce, encrypt));
        assertEquals(IamErrorCode.WECOM_CALLBACK_REPLAY.code(), ex.getErrorCode());
    }

    @SuppressWarnings("unchecked")
    private static WeComCallbackVerifier verifier(boolean replayHit) {
        WeComIamProperties properties = new WeComIamProperties();
        properties.getCustomerService().setTimestampSkewSeconds(300);
        properties.getCustomerService().setReplayTtlSeconds(600);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), any(), any())).thenReturn(!replayHit);
        return new WeComCallbackVerifier(properties, redisTemplate);
    }

    private static WeComCallbackCredential credential(WeComCallbackType type) {
        return new WeComCallbackCredential(
            type, "bind-1", TOKEN, AES_KEY_TEXT, RECEIVER, "ww-corp", "THIRD_PARTY");
    }

    private static String encrypt(String message, String receiver) throws Exception {
        byte[] aesKey = Base64.getDecoder().decode(AES_KEY_TEXT + "=");
        byte[] random = new byte[16];
        Arrays.fill(random, (byte) 1);
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] receiverBytes = receiver.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(20 + messageBytes.length + receiverBytes.length);
        buffer.put(random);
        buffer.putInt(messageBytes.length);
        buffer.put(messageBytes);
        buffer.put(receiverBytes);
        byte[] padded = pkcs7Pad(buffer.array());
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
            new IvParameterSpec(aesKey, 0, 16));
        return Base64.getEncoder().encodeToString(cipher.doFinal(padded));
    }

    private static byte[] pkcs7Pad(byte[] data) {
        int pad = 32 - (data.length % 32);
        byte[] result = Arrays.copyOf(data, data.length + pad);
        Arrays.fill(result, data.length, result.length, (byte) pad);
        return result;
    }

    private static String signature(String... values) throws Exception {
        Arrays.sort(values);
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        for (String value : values) {
            digest.update(value.getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
