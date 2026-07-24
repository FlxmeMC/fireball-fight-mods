package net.weavemc.mods.timer;

import java.util.function.LongSupplier;

/** Thread-safe monotonic stopwatch. It never depends on ticks or the system clock. */
public final class TimerState {
    private final LongSupplier nanoClock;
    private long startedAtNanos;
    private long stoppedElapsedNanos;
    private boolean running;
    private boolean visible;

    public TimerState() {
        this(System::nanoTime);
    }

    TimerState(LongSupplier nanoClock) {
        if (nanoClock == null) {
            throw new IllegalArgumentException("nanoClock");
        }
        this.nanoClock = nanoClock;
    }

    /** Starts a fresh timer, or freezes the currently running timer. */
    public synchronized void toggle() {
        if (running) {
            stoppedElapsedNanos = elapsedNanos(nanoClock.getAsLong());
            running = false;
            return;
        }
        startFresh();
    }

    public synchronized void startFresh() {
        startedAtNanos = nanoClock.getAsLong();
        stoppedElapsedNanos = 0L;
        running = true;
        visible = true;
    }

    public synchronized void reset() {
        startedAtNanos = 0L;
        stoppedElapsedNanos = 0L;
        running = false;
        visible = false;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized boolean isVisible() {
        return visible;
    }

    public synchronized long elapsedMillis() {
        long nanos = running ? elapsedNanos(nanoClock.getAsLong()) : stoppedElapsedNanos;
        return nanos / 1_000_000L;
    }

    private long elapsedNanos(long nowNanos) {
        return Math.max(0L, nowNanos - startedAtNanos);
    }
}
