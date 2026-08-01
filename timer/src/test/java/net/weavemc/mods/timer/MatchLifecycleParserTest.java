package net.weavemc.mods.timer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class MatchLifecycleParserTest {
    @Test
    public void recognizesServerLifecycleMessages() {
        assertEquals(MatchLifecycleParser.Signal.START,
                MatchLifecycleParser.parse("\u00A7aMatch started!"));
        assertEquals(MatchLifecycleParser.Signal.END,
                MatchLifecycleParser.parse("Match Results (Click to view)"));
        assertEquals(MatchLifecycleParser.Signal.END,
                MatchLifecycleParser.parse("Opponent forfeited."));
        assertEquals(MatchLifecycleParser.Signal.END,
                MatchLifecycleParser.parse("Opponent disconnected."));
    }

    @Test
    public void rejectsPlayerAuthoredLookalikes() {
        assertEquals(MatchLifecycleParser.Signal.NONE,
                MatchLifecycleParser.parse("[xFlxme] Match started!"));
        assertEquals(MatchLifecycleParser.Signal.NONE,
                MatchLifecycleParser.parse("[Famous] xFlxme AURA: Match started!"));
        assertEquals(MatchLifecycleParser.Signal.NONE,
                MatchLifecycleParser.parse("Alex: Match Results (Click to view)"));
        assertEquals(MatchLifecycleParser.Signal.NONE,
                MatchLifecycleParser.parse("Match started! good luck"));
    }
}
