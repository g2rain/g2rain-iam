package com.g2rain.iam.utils;

import com.g2rain.common.exception.BusinessException;
import com.g2rain.iam.enums.IamErrorCode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IamUtilsThumbprintTest {

    @Test
    void thumbprint_sameKeyGivesSameValueRegardlessOfJsonShape() throws Exception {
        ECKey key = new ECKeyGenerator(Curve.P_256).keyID("client-1").generate();
        String withKid = key.toPublicJWK().toJSONString();
        String withoutKid = new ECKey.Builder(key.toPublicJWK()).keyID(null).build().toJSONString();

        String expected = IamUtils.thumbprint(key);
        assertEquals(43, expected.length());
        assertEquals(expected, IamUtils.thumbprint(withKid));
        assertEquals(expected, IamUtils.thumbprint(withoutKid));
    }

    @Test
    void thumbprint_blankReturnsNull() {
        assertNull(IamUtils.thumbprint((String) null));
        assertNull(IamUtils.thumbprint(" "));
    }

    @Test
    void thumbprint_rejectsNonP256OrMalformed() throws Exception {
        String p384 = new ECKeyGenerator(Curve.P_384).generate().toPublicJWK().toJSONString();
        BusinessException e = assertThrows(BusinessException.class, () -> IamUtils.thumbprint(p384));
        assertEquals(IamErrorCode.CLIENT_PUBLIC_KEY_INVALID.code(), e.getErrorCode());
        assertThrows(BusinessException.class, () -> IamUtils.thumbprint("not-json"));
    }

    @Test
    void isEs256_requiresEs256AndP256() throws Exception {
        ECKey p256 = new ECKeyGenerator(Curve.P_256).generate();
        ECKey p384 = new ECKeyGenerator(Curve.P_384).generate();
        assertTrue(IamUtils.isEs256(new JWSHeader(JWSAlgorithm.ES256), p256));
        assertFalse(IamUtils.isEs256(new JWSHeader(JWSAlgorithm.ES384), p256));
        assertFalse(IamUtils.isEs256(new JWSHeader(JWSAlgorithm.ES256), p384));
    }
}
