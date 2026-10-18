package net.weavemc.mods.fireballindicator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks every loaded fireball between ticks and fades indicators in and out. A fireball is shown
 * for as long as it exists, except our own, which stay hidden unless they turn back towards us.
 */
final class FireballTracker {
    static final long FADE_IN_MS = 80L;
    static final long FADE_OUT_MS = 300L;
    /** How far an own fireball must come back from its farthest point to count as returned. */
    static final double RETURN_DISTANCE = 3.0;

    static final class Indicator {
        final int entityId;
        final boolean own;
        double prevX;
        double prevZ;
        double x;
        double z;
        double distance;
        double maxDistance;
        boolean returned;
        boolean shown;
        long fadeInStartMs;
        long hiddenAtMs;
        boolean present;
        boolean observedThisTick;

        Indicator(int entityId, boolean own, double x, double z, double distance) {
            this.entityId = entityId;
            this.own = own;
            this.prevX = x;
            this.prevZ = z;
            this.x = x;
            this.z = z;
            this.distance = distance;
            this.maxDistance = distance;
        }
    }

    private final Map<Integer, Indicator> indicators = new LinkedHashMap<Integer, Indicator>();

    boolean isTracked(int entityId) {
        return indicators.containsKey(entityId);
    }

    void beginTick() {
        for (Indicator indicator : indicators.values()) {
            indicator.observedThisTick = false;
        }
    }

    /** @param own only used the first time a fireball is seen */
    void observe(int entityId, boolean own, double x, double z, double distance, long now) {
        Indicator indicator = indicators.get(entityId);
        if (indicator == null) {
            indicator = new Indicator(entityId, own, x, z, distance);
            indicators.put(entityId, indicator);
        } else {
            indicator.prevX = indicator.x;
            indicator.prevZ = indicator.z;
            indicator.x = x;
            indicator.z = z;
            indicator.distance = distance;
            indicator.maxDistance = Math.max(indicator.maxDistance, distance);
            // Our own fireball was hit back at us.
            if (indicator.own && distance <= indicator.maxDistance - RETURN_DISTANCE) {
                indicator.returned = true;
            }
        }
        indicator.present = true;
        indicator.observedThisTick = true;
        setShown(indicator, !indicator.own || indicator.returned, now);
    }

    void endTick(long now) {
        Iterator<Indicator> iterator = indicators.values().iterator();
        while (iterator.hasNext()) {
            Indicator indicator = iterator.next();
            if (indicator.observedThisTick) {
                continue;
            }
            if (indicator.present) {
                indicator.present = false;
                setShown(indicator, false, now);
                // Freeze movement so the faded arc stays put.
                indicator.prevX = indicator.x;
                indicator.prevZ = indicator.z;
            } else if (now - indicator.hiddenAtMs >= FADE_OUT_MS) {
                iterator.remove();
            }
        }
    }

    private static void setShown(Indicator indicator, boolean shown, long now) {
        if (shown && !indicator.shown) {
            // Resume from the current brightness instead of popping back to full.
            indicator.fadeInStartMs = now - (long) (FADE_IN_MS * alpha(indicator, now));
            indicator.shown = true;
        } else if (!shown && indicator.shown) {
            indicator.shown = false;
            indicator.hiddenAtMs = now;
        }
    }

    static double alpha(Indicator indicator, long now) {
        double fadeIn = Math.min(1.0, (now - indicator.fadeInStartMs) / (double) FADE_IN_MS);
        if (indicator.shown) {
            return Math.max(0.0, fadeIn);
        }
        if (indicator.hiddenAtMs == 0L) {
            return 0.0;
        }
        double fadeOut = 1.0 - (now - indicator.hiddenAtMs) / (double) FADE_OUT_MS;
        return Math.max(0.0, Math.min(fadeIn, fadeOut));
    }

    List<Indicator> snapshot() {
        return new ArrayList<Indicator>(indicators.values());
    }

    boolean isEmpty() {
        return indicators.isEmpty();
    }

    void clear() {
        indicators.clear();
    }
}
