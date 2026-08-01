package net.weavemc.mods.timer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class TimerTextTest {
    @Test
    public void formatsMillisecondsWithoutLabels() {
        assertEquals("00:00.000", TimerText.fromMillis(0L).plainText());
        assertEquals("01:05.432", TimerText.fromMillis(65_432L).plainText());
        assertEquals("1:01:01.007", TimerText.fromMillis(3_661_007L).plainText());
    }

    @Test
    public void clampsNegativeElapsedTime() {
        assertEquals("00:00.000", TimerText.fromMillis(-1L).plainText());
    }
}
