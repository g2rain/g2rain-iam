package com.g2rain.iam.wecom;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.common.utils.Strings;
import com.g2rain.iam.config.WeComIamProperties;
import com.g2rain.iam.enums.IamErrorCode;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class WeComCredentialCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final WeComIamProperties properties;

    public WeComCredentialCipher(WeComIamProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String permanentCode) {
        if (Strings.isBlank(permanentCode)
            || Strings.isBlank(properties.getCredential().getEncryptionKey())) {
            throw new BusinessException(IamErrorCode.WECOM_CREDENTIAL_ENCRYPTION_FAILED);
        }
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(
                properties.getCredential().getEncryptionKey()
                    .getBytes(StandardCharsets.UTF_8));
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(
                permanentCode.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return "v1:" + Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new BusinessException(IamErrorCode.WECOM_CREDENTIAL_ENCRYPTION_FAILED);
        }
    }
}
