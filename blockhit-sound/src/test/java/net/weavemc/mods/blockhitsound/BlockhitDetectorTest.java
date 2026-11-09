package net.weavemc.mods.blockhitsound;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BlockhitDetectorTest {
    private static final long MS = DamageMath.MILLIS;
    private static final int ATTACKER = 7;
    private static final BlockhitDetector.Kind PLAYER_HIT =
            new BlockhitDetector.Kind(new Object(), false, false, true, false, false);
    private static final BlockhitDetector.Kind FALL =
            new BlockhitDetector.Kind(new Object(), true, false, false, false, false);

    private final FakeContext context = new FakeContext();
    private int plays;
    private BlockhitDetector detector;

    @Before
    public void setUp() {
        detector = new BlockhitDetector(context, new Runnable() {
            @Override
            public void run() {
                plays++;
            }
        });
        context.detector = detector;
        detector.reset(20.0F);
    }

    @Test
    public void blockedSwordHitPlaysImmediatelyAfterAimedSwing() {
        context.blocking = true;
        detector.addLiveAttack(swing(1_000 * MS, 8.0F)); // diamond sword
        detector.recordHealth(1_040 * MS, 20.0F - 4.5F);
        assertEquals(1, plays);
        detector.tick(2_000 * MS);
        assertEquals("never plays twice for one hit", 1, plays);
    }

    @Test
    public void unblockedDamageIsNotABlockhitEvenWhileBlocking() {
        context.blocking = true;
        detector.addLiveAttack(swing(1_000 * MS, 8.0F));
        detector.recordHealth(1_040 * MS, 12.0F);
        detector.tick(2_000 * MS);
        assertEquals(0, plays);
    }

    @Test
    public void blockedDamageNeedsTheServerToHaveSeenTheBlock() {
        context.blocking = false;
        detector.addLiveAttack(swing(1_000 * MS, 8.0F));
        detector.recordHealth(1_040 * MS, 15.5F);
        detector.tick(2_000 * MS);
        assertEquals(0, plays);
    }

    @Test
    public void plainFistBlockhitCountsEvenThoughBlockingCannotChangeIt() {
        // (1 + 1) / 2 == 1: a blocked fist deals the same as an unblocked one.
        context.blocking = true;
        detector.addLiveAttack(swing(1_000 * MS, 1.0F));
        detector.recordHealth(1_040 * MS, 19.0F);
        assertEquals(1, plays);
    }

    @Test
    public void plainFistWithoutBlockIsIgnored() {
        context.blocking = false;
        detector.addLiveAttack(swing(1_000 * MS, 1.0F));
        detector.recordHealth(1_040 * MS, 19.0F);
        detector.tick(2_000 * MS);
        assertEquals(0, plays);
    }

    @Test
    public void critFistBlockIsRecognisedByItsDamage() {
        context.blocking = true;
        detector.addLiveAttack(swing(1_000 * MS, 1.0F, 1.5F));
        detector.recordHealth(1_040 * MS, 20.0F - 1.25F);
        assertEquals(1, plays);
    }

    @Test
    public void fistAloneDoesNotExcuseAnUnblockedSwordHit() {
        context.blocking = true;
        detector.addLiveAttack(swing(1_000 * MS, 1.0F));
        detector.addLiveAttack(new BlockhitDetector.Attack(1_010 * MS, PLAYER_HIT,
                new float[] {8.0F}, 8, true, true, -1));
        context.nearby = new java.util.HashSet<Integer>(java.util.Arrays.asList(ATTACKER, 8));
        detector.recordHealth(1_040 * MS, 12.0F);
        detector.tick(2_000 * MS);
        assertEquals(0, plays);
    }

    @Test
    public void unblockableEnvironmentDamageMakesTheHitAmbiguous() {
        context.blocking = true;
        detector.addLiveAttack(new BlockhitDetector.Attack(1_000 * MS, PLAYER_HIT,
                new float[] {8.0F}, ATTACKER, true, false, -1)); // swing not aimed: wait
        context.extra = BlockhitDetector.Attack.simple(0L, FALL, 4.5F);
        detector.recordHealth(1_040 * MS, 15.5F);
        detector.tick(1_500 * MS);
        assertEquals(0, plays);
    }

    @Test
    public void unaimedSwingStillConfirmsWhenTheHitSettles() {
        context.blocking = true;
        detector.addLiveAttack(new BlockhitDetector.Attack(1_000 * MS, PLAYER_HIT,
                new float[] {8.0F}, ATTACKER, true, false, -1));
        detector.recordHealth(1_040 * MS, 15.5F);
        assertEquals(0, plays);
        detector.tick(1_300 * MS);
        assertEquals(0, plays);
        detector.tick(1_440 * MS);
        assertEquals(1, plays);
    }

    @Test
    public void chainedHitOnlyDealsTheExcessOverThePreviousHit() {
        context.blocking = true;
        detector.addLiveAttack(swing(1_000 * MS, 5.0F));          // wood sword, blocked: 3
        detector.recordHurtStatus(1_030 * MS);
        detector.recordHealth(1_040 * MS, 17.0F);
        assertEquals(1, plays);
        detector.addLiveAttack(swing(1_200 * MS, 8.0F));          // diamond during overlap
        detector.recordHealth(1_240 * MS, 17.0F - 2.0F);          // blocked (1 + 3) / 2
        assertEquals(2, plays);
    }

    @Test
    public void hurtStatusMarksAFreshHitInsideTheTimingWindow() {
        context.blocking = true;
        detector.addLiveAttack(swing(1_000 * MS, 5.0F));
        detector.recordHurtStatus(1_030 * MS);
        detector.recordHealth(1_040 * MS, 17.0F);
        // Server's overlap ran out a little before 500 ms on our clock: the hurt status says fresh.
        detector.addLiveAttack(swing(1_480 * MS, 8.0F));
        detector.recordHurtStatus(1_510 * MS);
        detector.recordHealth(1_520 * MS, 17.0F - 4.5F);
        assertEquals(2, plays);
    }

    @Test
    public void healWaitingForAbsorptionMakesHitAmbiguous() {
        context.blocking = true;
        context.absorption = 4.0F;
        detector.addLiveAttack(new BlockhitDetector.Attack(1_000 * MS, PLAYER_HIT,
                new float[] {8.0F}, ATTACKER, true, false, -1));
        detector.recordHealth(1_040 * MS, 19.5F);
        detector.recordHealth(1_060 * MS, 20.0F);
        detector.recordAbsorption(1_070 * MS, 4.0F, 0.0F, true);
        detector.tick(2_000 * MS);
        assertEquals(0, plays);
    }

    @Test
    public void healAfterACompleteSampleKeepsTheHit() {
        context.blocking = true;
        detector.addLiveAttack(new BlockhitDetector.Attack(1_000 * MS, PLAYER_HIT,
                new float[] {8.0F}, ATTACKER, true, false, -1));
        detector.recordHealth(1_040 * MS, 15.5F);
        detector.recordHealth(1_100 * MS, 16.5F); // natural regen
        detector.tick(1_500 * MS);
        assertEquals(1, plays);
    }

    @Test
    public void blockGraceFollowsRoundTrip() {
        detector.markSwordBlocking(1_000 * MS);
        assertTrue(detector.serverSawBlock(1_240 * MS, false, 100));
        assertFalse(detector.serverSawBlock(1_260 * MS, false, 100));
        assertTrue(detector.serverSawBlock(1_740 * MS, false, 2_000));
        assertFalse(detector.serverSawBlock(1_760 * MS, false, 2_000));
        assertTrue(detector.serverSawBlock(9_000 * MS, true, 0));
    }

    @Test
    public void resetForgetsPendingHits() {
        context.blocking = true;
        detector.addLiveAttack(new BlockhitDetector.Attack(1_000 * MS, PLAYER_HIT,
                new float[] {8.0F}, ATTACKER, true, false, -1));
        detector.recordHealth(1_040 * MS, 15.5F);
        detector.reset(20.0F);
        detector.tick(2_000 * MS);
        assertEquals(0, plays);
    }

    @Test
    public void vanillaReductionOrder() {
        assertEquals(4.5F, DamageMath.reduce(8.0F, true, false, false, 0, -1, 0), 0.0001F);
        assertEquals(1.0F, DamageMath.reduce(1.0F, true, false, false, 0, -1, 0), 0.0001F);
        assertEquals(8.0F * 20 / 25.0F, DamageMath.reduce(8.0F, false, false, false, 5, -1, 0),
                0.0001F);
        assertEquals(8.0F, DamageMath.reduce(8.0F, true, true, false, 0, -1, 0), 0.0001F);
        assertEquals(3, DamageMath.protectionMin(5));
        assertEquals(5, DamageMath.protectionMax(5));
    }

    private static BlockhitDetector.Attack swing(long time, float... raw) {
        return new BlockhitDetector.Attack(time, PLAYER_HIT, raw, ATTACKER, true, true, -1);
    }

    private static final class FakeContext implements BlockhitDetector.Context {
        boolean blocking;
        float absorption;
        java.util.Set<Integer> nearby = Collections.singleton(Integer.valueOf(ATTACKER));
        BlockhitDetector.Attack extra;
        BlockhitDetector detector;

        @Override
        public BlockhitDetector.Snapshot snapshot(long now) {
            BlockhitDetector.Snapshot state = new BlockhitDetector.Snapshot();
            state.blocking = blocking;
            state.absorption = absorption;
            state.nearby = nearby;
            return state;
        }

        @Override
        public void collectHitCandidates(long now) {
            if (extra != null) {
                detector.addAttack(new BlockhitDetector.Attack(now, extra.kind, extra.raw,
                        extra.attackerId, extra.melee, extra.aimed, extra.key));
            }
        }
    }
}
