package net.weavemc.mods.blockhitsound;

/**
 * Vanilla 1.8.9 damage arithmetic (EntityPlayer.damageEntity and friends) plus the timing windows
 * the detector uses. Pure functions so the decision logic can be tested without Minecraft.
 */
final class DamageMath {
    static final long MILLIS = 1_000_000L;
    /** Candidate attacks this close to a hit (either side) may explain it. */
    static final long ATTACK_WINDOW_NANOS = 400L * MILLIS;
    /** Vanilla overlap rules apply while hurtResistantTime > 10 ticks, i.e. 500 ms after a fresh hit. */
    static final long HURT_CHAIN_NANOS = 500L * MILLIS;
    /** Health and absorption updates of one hit arrive in separate packets, normally the same tick. */
    static final long MERGE_NANOS = 75L * MILLIS;
    /** A hurt status (fresh hit) precedes its health update by at most a couple of server ticks. */
    static final long HURT_STATUS_LINK_NANOS = 200L * MILLIS;
    /** Kept long enough that a chained hit can still re-explain the hit before it. */
    static final long ATTACK_RETENTION_NANOS = 1_500L * MILLIS;
    /** Extra slack on top of the round trip when deciding whether the server saw the block. */
    static final long BLOCK_GRACE_MARGIN_MILLIS = 150L;
    static final long BLOCK_GRACE_MAX_MILLIS = 750L;

    static final int DIFFICULTY_PEACEFUL = 0;
    static final int DIFFICULTY_EASY = 1;
    static final int DIFFICULTY_NORMAL = 2;
    static final int DIFFICULTY_HARD = 3;

    private DamageMath() {
    }

    /**
     * The server applies the block if it saw the block when the hit landed. The hit reaches us a
     * full round trip after the block state it was judged against left this client.
     */
    static long blockingGraceNanos(int pingMillis) {
        long ping = Math.max(0, pingMillis);
        return Math.min(BLOCK_GRACE_MAX_MILLIS, ping + BLOCK_GRACE_MARGIN_MILLIS) * MILLIS;
    }

    static boolean withinHurtChain(long now, long chainStart) {
        return now >= chainStart && now - chainStart < HURT_CHAIN_NANOS;
    }

    static boolean inAttackWindow(long hitTime, long attackTime) {
        return attackTime >= hitTime - ATTACK_WINDOW_NANOS
                && attackTime <= hitTime + ATTACK_WINDOW_NANOS;
    }

    /** True once every packet a hit of this size produces has been seen. */
    static boolean completeSample(boolean hasHealth, boolean hasAbsorption,
            float absorptionBefore, float loss) {
        return (hasHealth && (absorptionBefore <= 0.0F || hasAbsorption))
                || (hasAbsorption && loss < absorptionBefore - 0.001F);
    }

    /** EntityPlayer.attackEntityFrom difficulty scaling. */
    static float difficulty(float raw, boolean scaled, int difficultyId) {
        if (!scaled) {
            return raw;
        }
        switch (difficultyId) {
            case DIFFICULTY_PEACEFUL:
                return 0.0F;
            case DIFFICULTY_EASY:
                return Math.min(raw / 2.0F + 1.0F, raw);
            case DIFFICULTY_HARD:
                return raw * 3.0F / 2.0F;
            default:
                return raw;
        }
    }

    /** EnchantmentHelper.getEnchantmentModifierDamage rolls (v+1)/2 + rand(v/2 + 1), v capped at 25. */
    static int protectionMin(int protection) {
        return (Math.min(25, Math.max(0, protection)) + 1) / 2;
    }

    static int protectionMax(int protection) {
        return Math.min(25, Math.max(0, protection));
    }

    /** Damage left after blocking, armor, resistance and protection, in vanilla's float order. */
    static float reduce(float amount, boolean blocked, boolean unblockable, boolean absolute,
            int armorPoints, int resistanceAmplifier, int protection) {
        float damage = amount;
        if (blocked && !unblockable && damage > 0.0F) {
            damage = (1.0F + damage) * 0.5F;
        }
        if (!unblockable) {
            damage = damage * (float) (25 - armorPoints) / 25.0F;
        }
        if (!absolute) {
            if (resistanceAmplifier >= 0) {
                damage = damage * (float) (25 - (resistanceAmplifier + 1) * 5) / 25.0F;
            }
            if (damage <= 0.0F) {
                return 0.0F;
            }
            damage = damage * (float) (25 - Math.min(20, protection)) / 25.0F;
        }
        return Math.max(0.0F, damage);
    }

    static boolean matches(float loss, float computed) {
        return loss > 0.0F && Math.abs(loss - computed) <= Math.max(0.035F, computed * 0.015F);
    }
}
