package com.g2rain.iam.wecom;

import com.g2rain.iam.config.WeComIamProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeComCredentialCipherTest {

    @Test
    void permanentCodeIsNeverReturnedAsPlaintext() {
        WeComIamProperties properties = new WeComIamProperties();
        properties.getCredential().setEncryptionKey("test-only-key");
        WeComCredentialCipher cipher = new WeComCredentialCipher(properties);

        String encrypted = cipher.encrypt("permanent-code");

        assertTrue(encrypted.startsWith("v1:"));
        assertNotEquals("permanent-code", encrypted);
    }
}
