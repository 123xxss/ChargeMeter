package com.dsh.chargemeter;

/**
 * 自动识别电芯数。
 * 原理：charge counter 的增长速率（mAh/h）代表整包电流；
 * 若系统上报的 current_now 只是单个电芯的电流，两者比值约为 2，否则约为 1。
 */
class CellDetector {

    private long startTs = 0;
    private long startCounter = 0;
    private double ampSum = 0;
    private int samples = 0;
    private int result = 0;

    void reset() {
        startTs = 0;
        startCounter = 0;
        ampSum = 0;
        samples = 0;
    }

    /** @return 识别出 1 或 2，未识别返回 0 */
    int add(long counterUAh, double amps, long nowMs) {
        if (counterUAh <= 0 || Math.abs(amps) < 0.15) return result;
        if (startTs == 0) {
            startTs = nowMs;
            startCounter = counterUAh;
            ampSum = 0;
            samples = 0;
            return result;
        }
        ampSum += Math.abs(amps);
        samples++;
        long span = nowMs - startTs;
        if (span < 45000 || samples < 20) return result;

        double dCounterMah = (counterUAh - startCounter) / 1000.0;
        double hours = span / 3600000.0;
        double avgAmps = ampSum / samples;
        if (dCounterMah <= 0 || avgAmps <= 0) {
            reset();
            return result;
        }
        double impliedAmps = dCounterMah / hours;
        double k = impliedAmps / avgAmps;
        if (k > 1.55 && k < 2.6) result = 2;
        else if (k > 0.6 && k < 1.45) result = 1;
        reset();
        return result;
    }

    int current() {
        return result;
    }
}
