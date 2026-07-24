package net.weavemc.mods.blockhitsound;

import java.util.concurrent.TimeUnit;

/** Detects one confirmed local hurt event while a sword block is active. */
final class BlockhitDetector {
    static final long DUPLICATE_WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(100L);
    static final long COMBAT_SIGNAL_WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(250L);

    private boolean swordBlocking;
    private long pendingHurtNanos = Long.MIN_VALUE;
    private long pendingVelocityNanos = Long.MIN_VALUE;
    private long lastSuccessfulBlockhitNanos = Long.MIN_VALUE;

    synchronized void updateSwordBlocking(boolean blocking) {
        swordBlocking = blocking;
        if (!blocking) {
            pendingHurtNanos = Long.MIN_VALUE;
            pendingVelocityNanos = Long.MIN_VALUE;
        }
    }

    synchronized boolean recordLocalHurt(long nowNanos) {
        if (!swordBlocking) {
            return false;
        }
        pendingHurtNanos = nowNanos;
        return finishCombatHit(nowNanos);
    }

    synchronized boolean recordLocalVelocity(long nowNanos) {
        if (!swordBlocking) {
            return false;
        }
        pendingVelocityNanos = nowNanos;
        return finishCombatHit(nowNanos);
    }

    private boolean finishCombatHit(long nowNanos) {
        if (pendingHurtNanos == Long.MIN_VALUE || pendingVelocityNanos == Long.MIN_VALUE
                || absoluteDifference(pendingHurtNanos, pendingVelocityNanos)
                        > COMBAT_SIGNAL_WINDOW_NANOS) {
            return false;
        }
        if (lastSuccessfulBlockhitNanos != Long.MIN_VALUE
                && nowNanos - lastSuccessfulBlockhitNanos < DUPLICATE_WINDOW_NANOS) {
            return false;
        }
        lastSuccessfulBlockhitNanos = nowNanos;
        pendingHurtNanos = Long.MIN_VALUE;
        pendingVelocityNanos = Long.MIN_VALUE;
        return true;
    }

    private static long absoluteDifference(long first, long second) {
        return first >= second ? first - second : second - first;
    }

    synchronized void reset() {
        swordBlocking = false;
        pendingHurtNanos = Long.MIN_VALUE;
        pendingVelocityNanos = Long.MIN_VALUE;
        lastSuccessfulBlockhitNanos = Long.MIN_VALUE;
    }
}
