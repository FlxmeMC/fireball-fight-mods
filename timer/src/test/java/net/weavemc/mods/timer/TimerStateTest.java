package net.weavemc.mods.timer;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TimerStateTest {
    @Test
    public void commandCycleStartsFreezesAndRestarts() {
        AtomicLong clock = new AtomicLong(10_000L);
        TimerState state = new TimerState(clock::get);

        state.toggle();
        assertTrue(state.isRunning());
        assertTrue(state.isVisible());
        clock.addAndGet(1_234_567_890L);
        assertEquals(1_234L, state.elapsedMillis());

        state.toggle();
        assertFalse(state.isRunning());
        clock.addAndGet(5_000_000_000L);
        assertEquals(1_234L, state.elapsedMillis());

        state.toggle();
        assertTrue(state.isRunning());
        assertEquals(0L, state.elapsedMillis());
    }

    @Test
    public void resetHidesTimer() {
        TimerState state = new TimerState(() -> 100L);
        state.toggle();
        state.reset();
        assertFalse(state.isRunning());
        assertFalse(state.isVisible());
        assertEquals(0L, state.elapsedMillis());
    }

    @Test
    public void finishFreezesLatestTimeAndKeepsTimerVisible() {
        AtomicLong clock = new AtomicLong(10_000L);
        TimerState state = new TimerState(clock::get);

        state.startFresh();
        clock.addAndGet(2_345_678_901L);
        state.finish();

        assertFalse(state.isRunning());
        assertTrue(state.isVisible());
        assertTrue(state.isFinishedMatch());
        assertEquals(2_345L, state.elapsedMillis());

        clock.addAndGet(10_000_000_000L);
        assertEquals(2_345L, state.elapsedMillis());
    }

    @Test
    public void freshStartClearsFinishedMatchMarker() {
        TimerState state = new TimerState(() -> 100L);

        state.startFresh();
        state.finish();
        state.startFresh();

        assertFalse(state.isFinishedMatch());
        assertTrue(state.isRunning());
    }
}
