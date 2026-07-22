package com.g2rain.iam.dingtalk;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 钉钉 TopAPI 限流重试辅助逻辑。
 */
final class DingTalkTopApiSupport {

    private DingTalkTopApiSupport() {
    }

    static boolean isRetryableErrCode(int errCode) {
        return errCode == -1 || errCode == 90002 || errCode == 90018;
    }

    static long computeRetryBackoffMs(long baseMs, int attempt) {
        long multiplier = 1L << Math.max(0, attempt - 1);
        long backoff = baseMs * multiplier;
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, baseMs / 2));
        return backoff + jitter;
    }
}
