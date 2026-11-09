package net.weavemc.mods.blockhitsound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Confirms a blockhit from the damage the server actually dealt.
 *
 * <p>Each health or absorption drop becomes a {@link Hit}. A hit counts as a blockhit only when its
 * loss equals the sword-blocked damage of a plausible attack and equals no unblocked damage of any
 * plausible attack. Being hurt while blocking is not enough: the server may not have seen the block,
 * and blocking does nothing against unblockable damage.
 *
 * <p>Minecraft-free: the client side feeds samples in through {@link Context} and the record methods.
 * All calls must come from one thread.
 */
final class BlockhitDetector {
    static final long NEVER = Long.MIN_VALUE / 4L;
    private static final int MAX_ATTACKS = 512;
    private static final int MAX_PENDING = 32;
    private static final int MAX_CHAIN_DEPTH = 32;

    /** Client-side samples the detector needs when a new hit opens. */
    interface Context {
        /** The local player's state right now. */
        Snapshot snapshot(long now);

        /** Adds environmental and projectile attacks that could explain a hit opening now. */
        void collectHitCandidates(long now);
    }

    /** Armor enchantment protection (before the random roll) against one damage source. */
    interface Protection {
        int against(Object source);
    }

    /** What the server will do with one damage source. */
    static final class Kind {
        final Object source;
        final boolean unblockable;
        final boolean absolute;
        final boolean difficultyScaled;
        final boolean fire;
        final boolean ignoresResistance;

        Kind(Object source, boolean unblockable, boolean absolute, boolean difficultyScaled,
                boolean fire, boolean ignoresResistance) {
            this.source = source;
            this.unblockable = unblockable;
            this.absolute = absolute;
            this.difficultyScaled = difficultyScaled;
            this.fire = fire;
            this.ignoresResistance = ignoresResistance;
        }
    }

    /** One way the local player could have been damaged, with every raw amount it could deal. */
    static final class Attack {
        final long time;
        final Kind kind;
        final float[] raw;
        final int attackerId;
        final boolean melee;
        final boolean aimed;
        /** Entity id of a projectile, so repeated scans refresh one entry instead of piling up. */
        final int key;

        Attack(long time, Kind kind, float[] raw, int attackerId, boolean melee, boolean aimed,
                int key) {
            this.time = time;
            this.kind = kind;
            this.raw = raw;
            this.attackerId = attackerId;
            this.melee = melee;
            this.aimed = aimed;
            this.key = key;
        }

        static Attack simple(long time, Kind kind, float... raw) {
            return new Attack(time, kind, raw, -1, false, false, -1);
        }
    }

    /** The local player at the moment a hit was registered. */
    static final class Snapshot {
        boolean blocking;
        int armorPoints;
        int resistance = -1;
        boolean fireImmune;
        int difficulty = DamageMath.DIFFICULTY_NORMAL;
        float absorption;
        Set<Integer> nearby = Collections.emptySet();
        Protection protection = new Protection() {
            @Override
            public int against(Object source) {
                return 0;
            }
        };
    }

    static final class Hit {
        final long time;
        final Snapshot state;
        final Set<Float> possibleRaw = new LinkedHashSet<Float>();
        private final Map<Object, Integer> protection = new IdentityHashMap<Object, Integer>();
        Hit previous;
        long chainStart;
        float loss;
        boolean ambiguous;
        boolean hasHealth;
        boolean hasAbsorption;
        boolean played;

        Hit(long time, Snapshot state, Hit previous) {
            this.time = time;
            this.state = state;
            this.previous = previous;
            this.chainStart = previous == null ? time : previous.chainStart;
        }

        boolean complete() {
            return DamageMath.completeSample(hasHealth, hasAbsorption, state.absorption, loss);
        }

        int protectionAgainst(Object source) {
            Integer cached = protection.get(source);
            if (cached == null) {
                cached = Integer.valueOf(state.protection.against(source));
                protection.put(source, cached);
            }
            return cached.intValue();
        }
    }

    private final Context context;
    private final Runnable onBlockhit;
    private final List<Attack> attacks = new ArrayList<Attack>();
    private final List<Hit> pending = new ArrayList<Hit>();
    private Hit lastHit;
    private float serverHealth = Float.NaN;
    private long lastSwordBlocking = NEVER;
    private long lastHurtStatus = NEVER;

    BlockhitDetector(Context context, Runnable onBlockhit) {
        this.context = context;
        this.onBlockhit = onBlockhit;
    }

    /** Forgets everything; {@code health} is the player's current health, or NaN if unknown. */
    void reset(float health) {
        attacks.clear();
        pending.clear();
        lastHit = null;
        serverHealth = health;
        lastSwordBlocking = NEVER;
        lastHurtStatus = NEVER;
    }

    void markSwordBlocking(long now) {
        lastSwordBlocking = now;
    }

    /** Whether the server may have seen a sword block when a hit arriving now was dealt. */
    boolean serverSawBlock(long now, boolean swordBlockingNow, int pingMillis) {
        if (swordBlockingNow) {
            return true;
        }
        return lastSwordBlocking != NEVER
                && now - lastSwordBlocking <= DamageMath.blockingGraceNanos(pingMillis);
    }

    /** S06: the server's view of our health. */
    void recordHealth(long now, float health) {
        if (Float.isNaN(serverHealth)) {
            serverHealth = health;
            return;
        }
        float loss = serverHealth - health;
        serverHealth = health;
        if (loss > 0.0F) {
            damage(now, loss, false, health <= 0.0F);
        } else if (loss < 0.0F) {
            invalidateIncomplete(now);
        }
    }

    /**
     * Absorption metadata. Drops only count while the potion is active, since its expiry also
     * clears absorption.
     */
    void recordAbsorption(long now, float before, float after, boolean absorptionEffectActive) {
        float loss = before - after;
        if (loss > 0.0F) {
            if (absorptionEffectActive) {
                damage(now, loss, true, false);
            }
        } else if (loss < 0.0F) {
            invalidateIncomplete(now);
        }
    }

    /**
     * S19 status 2 on the local player. Vanilla sends it only for a fresh hit, never for damage
     * dealt during the hurt-resistance overlap, so it settles which kind the next drop is.
     */
    void recordHurtStatus(long now) {
        lastHurtStatus = now;
    }

    void addAttack(Attack attack) {
        if (attack.key >= 0) {
            for (Iterator<Attack> it = attacks.iterator(); it.hasNext();) {
                Attack old = it.next();
                if (old.key == attack.key && old.kind.source == attack.kind.source
                        && attack.time - old.time < DamageMath.ATTACK_WINDOW_NANOS) {
                    it.remove();
                }
            }
        }
        attacks.add(attack);
        while (attacks.size() > MAX_ATTACKS) {
            attacks.remove(0);
        }
    }

    /** An attack seen live (a swing): may confirm a waiting hit right away. */
    void addLiveAttack(Attack attack) {
        addAttack(attack);
        confirmEarly();
    }

    void tick(long now) {
        for (Iterator<Hit> it = pending.iterator(); it.hasNext();) {
            Hit hit = it.next();
            if (now - hit.time < DamageMath.ATTACK_WINDOW_NANOS) {
                continue;
            }
            if (!hit.played && classify(hit, 0)) {
                hit.played = true;
                onBlockhit.run();
            }
            it.remove();
        }
        for (Iterator<Attack> it = attacks.iterator(); it.hasNext();) {
            if (now - it.next().time > DamageMath.ATTACK_RETENTION_NANOS) {
                it.remove();
            }
        }
    }

    private void damage(long now, float amount, boolean absorption, boolean fatal) {
        Hit hit = pending.isEmpty() ? null : pending.get(pending.size() - 1);
        boolean channelFree = hit != null && (absorption ? !hit.hasAbsorption : !hit.hasHealth);
        if (!channelFree || now - hit.time > DamageMath.MERGE_NANOS) {
            Hit previous = lastHit != null && DamageMath.withinHurtChain(now, lastHit.chainStart)
                    ? lastHit : null;
            if (lastHurtStatus != NEVER && now >= lastHurtStatus
                    && now - lastHurtStatus <= DamageMath.HURT_STATUS_LINK_NANOS) {
                previous = null;
                lastHurtStatus = NEVER;
            }
            hit = new Hit(now, context.snapshot(now), previous);
            lastHit = hit;
            pending.add(hit);
            context.collectHitCandidates(now);
        }
        if (absorption) {
            hit.hasAbsorption = true;
        } else {
            hit.hasHealth = true;
        }
        hit.loss += amount;
        hit.ambiguous |= fatal;
        confirmEarly();
        while (pending.size() > MAX_PENDING) {
            pending.remove(0);
        }
    }

    /** A heal can only corrupt a hit whose packets are not all in yet. */
    private void invalidateIncomplete(long now) {
        for (Hit hit : pending) {
            if (now - hit.time <= DamageMath.ATTACK_WINDOW_NANOS && !hit.complete()) {
                hit.ambiguous = true;
            }
        }
    }

    /** Plays as soon as a sampled hit matches and a nearby swing was aimed at us. */
    private void confirmEarly() {
        for (Hit hit : pending) {
            if (hit.played || hit.ambiguous || !hit.complete() || !aimedSwingNear(hit)) {
                continue;
            }
            if (classify(hit, 0)) {
                hit.played = true;
                onBlockhit.run();
            }
        }
    }

    private boolean aimedSwingNear(Hit hit) {
        for (Attack attack : attacks) {
            if (attack.melee && attack.aimed && hit.state.nearby.contains(attack.attackerId)
                    && DamageMath.inAttackWindow(hit.time, attack.time)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the hit's loss fits a blocked attack and no unblocked one. Also records in
     * {@link Hit#possibleRaw} the raw amounts that explain it, which a chained hit after it needs:
     * vanilla deals only the excess over the previous raw amount during the overlap.
     */
    boolean classify(Hit hit, int depth) {
        hit.possibleRaw.clear();
        if (hit.ambiguous || hit.loss <= 0.0F || depth >= MAX_CHAIN_DEPTH) {
            return false;
        }
        Hit previous = hit.previous;
        if (previous != null) {
            if (!previous.complete()) {
                return false;
            }
            classify(previous, depth + 1);
            if (previous.possibleRaw.isEmpty()) {
                return false;
            }
        }
        Set<Float> before = previous == null
                ? Collections.singleton(Float.valueOf(0.0F)) : previous.possibleRaw;
        Snapshot state = hit.state;
        boolean blockedMatch = false;
        // An unblocked explanation only rules a block out when blocking would have changed that
        // same attack's damage. A plain fist (1 damage) blocks to (1 + 1) / 2 = 1, so a fist hit
        // proves nothing either way and the client's block timing has to decide.
        boolean decisiveUnblocked = false;
        for (Attack attack : attacks) {
            if (!DamageMath.inAttackWindow(hit.time, attack.time)
                    || (attack.melee && !state.nearby.contains(attack.attackerId))) {
                continue;
            }
            Kind kind = attack.kind;
            if (state.fireImmune && kind.fire) {
                continue;
            }
            int protection = hit.protectionAgainst(kind.source);
            int resistance = kind.ignoresResistance ? -1 : state.resistance;
            for (float raw : attack.raw) {
                float scaled = DamageMath.difficulty(raw, kind.difficultyScaled, state.difficulty);
                for (Float previousRaw : before) {
                    float effective = scaled - previousRaw.floatValue();
                    if (effective <= 0.0F) {
                        continue;
                    }
                    for (int epf = DamageMath.protectionMin(protection);
                            epf <= DamageMath.protectionMax(protection); epf++) {
                        float unblocked = DamageMath.reduce(effective, false, kind.unblockable,
                                kind.absolute, state.armorPoints, resistance, epf);
                        boolean fitsUnblocked = DamageMath.matches(hit.loss, unblocked);
                        boolean fitsBlocked = !kind.unblockable && DamageMath.matches(hit.loss,
                                DamageMath.reduce(effective, true, false, kind.absolute,
                                        state.armorPoints, resistance, epf));
                        if (fitsUnblocked) {
                            hit.possibleRaw.add(Float.valueOf(scaled));
                            if (!fitsBlocked) {
                                decisiveUnblocked = true;
                            }
                        }
                        if (state.blocking && fitsBlocked) {
                            blockedMatch = true;
                            hit.possibleRaw.add(Float.valueOf(scaled));
                        }
                    }
                }
            }
        }
        return state.blocking && blockedMatch && !decisiveUnblocked;
    }
}
