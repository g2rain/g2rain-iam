package com.g2rain.iam.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IamUrlUtilsTest {

    @Test
    void trimTrailingSlashNormalizesWhitespaceAndRepeatedSlashes() {
        assertEquals("https://iam.example.com", IamUrlUtils.trimTrailingSlash(
            "  https://iam.example.com///  "));
    }

    @Test
    void ensureLeadingSlashHandlesEmptyAndExistingSegments() {
        assertEquals("", IamUrlUtils.ensureLeadingSlash("  "));
        assertEquals("/auth/callback", IamUrlUtils.ensureLeadingSlash("auth/callback"));
        assertEquals("/auth/callback", IamUrlUtils.ensureLeadingSlash(" /auth/callback "));
    }

    @Test
    void joinAbsoluteUrlSkipsBlankSegmentsAndAvoidsDuplicateSeparators() {
        assertEquals(
            "https://iam.example.com/auth/callback?next=/main",
            IamUrlUtils.joinAbsoluteUrl(
                " https://iam.example.com/ ", null, " ", "auth", "/callback?next=/main"));
    }
}
