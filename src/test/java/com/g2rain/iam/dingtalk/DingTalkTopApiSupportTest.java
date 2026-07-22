package com.g2rain.iam.dingtalk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DingTalkTopApiSupportTest {

    @Test
    void isRetryableErrCode_shouldAcceptKnownRateLimitCodes() {
        assertTrue(DingTalkTopApiSupport.isRetryableErrCode(90002));
        assertTrue(DingTalkTopApiSupport.isRetryableErrCode(90018));
        assertTrue(DingTalkTopApiSupport.isRetryableErrCode(-1));
    }

    @Test
    void isRetryableErrCode_shouldRejectBusinessErrors() {
        assertFalse(DingTalkTopApiSupport.isRetryableErrCode(40014));
        assertFalse(DingTalkTopApiSupport.isRetryableErrCode(0));
    }

    @Test
    void computeRetryBackoffMs_shouldIncreaseWithAttempt() {
        long first = DingTalkTopApiSupport.computeRetryBackoffMs(500, 1);
        long second = DingTalkTopApiSupport.computeRetryBackoffMs(500, 2);
        assertTrue(first >= 500);
        assertTrue(second >= first);
    }
}
