package com.g2rain.iam.dingtalk;


import lombok.extern.slf4j.Slf4j;
/**
 * 轻量 QPS 限流器（单线程同步调用场景）。
 */
@Slf4j
final class DingTalkTopApiRateLimiter {

    private final long intervalNanos;
    private long nextPermitNanos;

    DingTalkTopApiRateLimiter(double qps) {
        double effectiveQps = qps > 0 ? qps : 1;
        this.intervalNanos = (long) (1_000_000_000L / effectiveQps);
    }

    synchronized void acquire() {
        long now = System.nanoTime();
        if (now < nextPermitNanos) {
            long waitNanos = nextPermitNanos - now;
            try {
                Thread.sleep(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
            } catch (InterruptedException ex) {
                log.error("failed to acquire", ex);
                Thread.currentThread().interrupt();
            }
            now = System.nanoTime();
        }
        nextPermitNanos = Math.max(now, nextPermitNanos) + intervalNanos;
    }
}
