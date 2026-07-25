package com.g2rain.iam.wecom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class WeComPrincipalTest {

    @Test
    void thirdPartyUsesOpenUserIdAsPreferredSubject() {
        WeComPrincipal principal = new WeComPrincipal(
            "corp-a", "encrypted-user", "open-user", "name",
            "THIRD_PARTY", "suite", "agent", "{}");

        assertEquals("WECHAT_WORK", principal.toIdpPrincipal().idpType());
        assertEquals("open-user", principal.toIdpPrincipal().idpSubject());
    }

    @Test
    void fallbackSubjectIsStableAndSeparatedByEnterprise() {
        String first = WeComPrincipal.stableSubject("corp-a", "same-user");
        String repeated = WeComPrincipal.stableSubject("corp-a", "same-user");
        String otherCorp = WeComPrincipal.stableSubject("corp-b", "same-user");

        assertEquals(first, repeated);
        assertNotEquals(first, otherCorp);
    }
}
