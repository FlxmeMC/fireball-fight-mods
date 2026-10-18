package net.weavemc.mods.fireballindicator;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class FireballTrackerTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    public void indicatorFadesInStaysAndFadesOutAfterFireballIsGone() {
        FireballTracker tracker = new FireballTracker();
        tick(tracker, 7, false, 10, 1000L);
        FireballTracker.Indicator indicator = tracker.snapshot().get(0);

        assertEquals(0.0, FireballTracker.alpha(indicator, 1000L), EPSILON);
        assertEquals(1.0, FireballTracker.alpha(indicator, 1000L + FireballTracker.FADE_IN_MS), EPSILON);

        tracker.beginTick();
        tracker.endTick(2000L);
        assertEquals(1, tracker.snapshot().size());
        assertEquals(0.5, FireballTracker.alpha(indicator, 2000L + FireballTracker.FADE_OUT_MS / 2), EPSILON);

        tracker.beginTick();
        tracker.endTick(2000L + FireballTracker.FADE_OUT_MS);
        assertTrue(tracker.isEmpty());
    }

    @Test
    public void otherPlayersFireballShowsWhileItExistsEvenWhenFlyingAway() {
        FireballTracker tracker = new FireballTracker();
        tick(tracker, 7, false, 10, 1000L);
        tick(tracker, 7, false, 30, 2000L);
        tick(tracker, 7, false, 90, 3000L);

        FireballTracker.Indicator indicator = tracker.snapshot().get(0);
        assertEquals(1.0, FireballTracker.alpha(indicator, 3000L), EPSILON);
    }

    @Test
    public void ownFireballIsNeverShownWhileFlyingAway() {
        FireballTracker tracker = new FireballTracker();
        tick(tracker, 7, true, 2, 1000L);
        tick(tracker, 7, true, 6, 1050L);
        tick(tracker, 7, true, 10, 1100L);

        FireballTracker.Indicator indicator = tracker.snapshot().get(0);
        assertEquals(0.0, FireballTracker.alpha(indicator, 1000L + FireballTracker.FADE_IN_MS), EPSILON);
        assertEquals(0.0, FireballTracker.alpha(indicator, 1200L), EPSILON);
    }

    @Test
    public void ownFireballHitBackAtUsIsShown() {
        FireballTracker tracker = new FireballTracker();
        tick(tracker, 7, true, 2, 1000L);
        tick(tracker, 7, true, 12, 1050L);
        tick(tracker, 7, true, 10, 1100L);
        FireballTracker.Indicator indicator = tracker.snapshot().get(0);
        assertEquals(0.0, FireballTracker.alpha(indicator, 1200L), EPSILON);

        tick(tracker, 7, true, 8, 1150L);
        assertEquals(1.0, FireballTracker.alpha(indicator, 1150L + FireballTracker.FADE_IN_MS), EPSILON);
    }

    @Test
    public void indicatorFollowsFireballBetweenTicks() {
        FireballTracker tracker = new FireballTracker();
        tracker.beginTick();
        tracker.observe(7, false, 0, 10, 10, 1000L);
        tracker.endTick(1000L);
        tracker.beginTick();
        tracker.observe(7, false, 0, 9, 9, 1050L);
        tracker.endTick(1050L);

        List<FireballTracker.Indicator> indicators = tracker.snapshot();
        assertEquals(1, indicators.size());
        assertEquals(10.0, indicators.get(0).prevZ, EPSILON);
        assertEquals(9.0, indicators.get(0).z, EPSILON);
        assertEquals(9.0, indicators.get(0).distance, EPSILON);
    }

    private static void tick(FireballTracker tracker, int id, boolean own, double distance, long now) {
        tracker.beginTick();
        tracker.observe(id, own, 0, distance, distance, now);
        tracker.endTick(now);
    }
}
