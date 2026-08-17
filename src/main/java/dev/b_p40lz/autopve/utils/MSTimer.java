package dev.b_p40lz.autopve.utils;

public class MSTimer {
    private long lastTime = System.currentTimeMillis();

    public final boolean hasPassTime(long time) {
        return getPassTime() >= time;
    }

    public final boolean hasPassTime(int time) {
        return getPassTime() >= (long) time;
    }

    public final boolean hasPassed(long time) {
        return getPassTime() >= time;
    }

    public final void reset() {
        this.lastTime = System.currentTimeMillis();
    }

    public final long getPassTime() {
        return System.currentTimeMillis() - this.lastTime;
    }
}
