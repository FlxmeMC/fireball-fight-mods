package net.weavemc.mods.blockhitsound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.ServersideAttributeMap;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S0EPacketSpawnObject;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * Client thread side of blockhit detection: turns packets and world state into samples and
 * candidate attacks for {@link BlockhitDetector}.
 */
final class CombatSampler implements BlockhitDetector.Context {
    private static final double NEARBY_SQ = 36.0;
    private static final double MOB_REACH_SQ = 9.0;
    private static final int ABSORPTION_WATCHER_ID = 17;
    private static final int SWING_ANIMATION = 0;
    private static final int ARROW_OBJECT = 60;
    private static final int HURT_STATUS = 2;

    private final BlockhitDetector detector;
    private final Map<Integer, ArrowOwner> arrows = new HashMap<Integer, ArrowOwner>();
    private final Map<DamageSource, BlockhitDetector.Kind> fixedKinds =
            new HashMap<DamageSource, BlockhitDetector.Kind>();
    private EntityPlayerSP player;
    private WorldClient world;
    private float recentFallDistance;
    private long fallSampleNanos = BlockhitDetector.NEVER;

    CombatSampler(Runnable onBlockhit) {
        this.detector = new BlockhitDetector(this, onBlockhit);
    }

    static boolean accepts(Packet<?> packet) {
        return packet instanceof S06PacketUpdateHealth
                || packet instanceof S1CPacketEntityMetadata
                || packet instanceof S0BPacketAnimation
                || packet instanceof S0EPacketSpawnObject
                || packet instanceof S19PacketEntityStatus
                || packet instanceof S27PacketExplosion;
    }

    void reset() {
        player = null;
        world = null;
        arrows.clear();
        recentFallDistance = 0.0F;
        fallSampleNanos = BlockhitDetector.NEVER;
        detector.reset(Float.NaN);
    }

    /**
     * Runs on the client thread, queued ahead of vanilla's handler for the same packet, so the
     * player still holds the values the packet is about to replace.
     */
    void handle(Packet<?> packet) {
        if (!ready()) {
            return;
        }
        long now = System.nanoTime();
        if (packet instanceof S06PacketUpdateHealth) {
            detector.recordHealth(now, ((S06PacketUpdateHealth) packet).getHealth());
        } else if (packet instanceof S1CPacketEntityMetadata) {
            metadata(now, (S1CPacketEntityMetadata) packet);
        } else if (packet instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus status = (S19PacketEntityStatus) packet;
            if (status.getOpCode() == HURT_STATUS && status.getEntity(world) == player) {
                detector.recordHurtStatus(now);
            }
        } else if (packet instanceof S0BPacketAnimation) {
            animation(now, (S0BPacketAnimation) packet);
        } else if (packet instanceof S0EPacketSpawnObject) {
            spawn((S0EPacketSpawnObject) packet);
        } else if (packet instanceof S27PacketExplosion) {
            explosion(now, (S27PacketExplosion) packet);
        }
    }

    void tick() {
        if (!ready()) {
            if (player != null) {
                reset();
            }
            return;
        }
        long now = System.nanoTime();
        if (isSwordBlocking(player)) {
            detector.markSwordBlocking(now);
        }
        if (player.fallDistance > 0.0F) {
            recentFallDistance = player.fallDistance;
            fallSampleNanos = now;
        } else if (now - fallSampleNanos > DamageMath.ATTACK_WINDOW_NANOS) {
            recentFallDistance = 0.0F;
        }
        // Every tick: a projectile is usually gone by the time its damage packet lands.
        scanProjectiles(now);
        detector.tick(now);
        for (Iterator<Integer> it = arrows.keySet().iterator(); it.hasNext();) {
            if (world.getEntityByID(it.next().intValue()) == null) {
                it.remove();
            }
        }
    }

    private boolean ready() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }
        if (player != mc.thePlayer || world != mc.theWorld) {
            reset();
            player = mc.thePlayer;
            world = mc.theWorld;
            detector.reset(player.getHealth());
        }
        return !player.isDead && player.getHealth() > 0.0F && !player.capabilities.disableDamage;
    }

    private void metadata(long now, S1CPacketEntityMetadata packet) {
        List<DataWatcher.WatchableObject> values = packet.func_149376_c();
        if (packet.getEntityId() != player.getEntityId() || values == null) {
            return;
        }
        for (DataWatcher.WatchableObject value : values) {
            if (value.getDataValueId() == ABSORPTION_WATCHER_ID
                    && value.getObject() instanceof Float) {
                PotionEffect effect = player.getActivePotionEffect(Potion.absorption);
                detector.recordAbsorption(now, player.getAbsorptionAmount(),
                        ((Float) value.getObject()).floatValue(),
                        effect != null && effect.getDuration() > 2);
            }
        }
    }

    private void animation(long now, S0BPacketAnimation packet) {
        if (packet.getAnimationType() != SWING_ANIMATION) {
            return;
        }
        Entity entity = world.getEntityByID(packet.getEntityID());
        if (!(entity instanceof EntityLivingBase) || entity == player || entity.isDead
                || player.getDistanceSqToEntity(entity) > NEARBY_SQ) {
            return;
        }
        if (entity instanceof EntityPlayer && ((EntityPlayer) entity).isSpectator()) {
            return;
        }
        BlockhitDetector.Attack attack = melee((EntityLivingBase) entity, now, true);
        if (attack != null) {
            detector.addLiveAttack(attack);
        }
    }

    private void spawn(S0EPacketSpawnObject packet) {
        if (packet.getType() != ARROW_OBJECT) {
            return;
        }
        int shooterId = packet.func_149009_m();
        Entity shooter = world.getEntityByID(shooterId);
        ItemStack held = shooter instanceof EntityLivingBase
                ? ((EntityLivingBase) shooter).getHeldItem() : null;
        int power = held != null && held.getItem() instanceof ItemBow
                ? EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, held) : -1;
        arrows.put(Integer.valueOf(packet.getEntityID()), new ArrowOwner(shooterId, power));
    }

    /** Explosion.doExplosionA's entity damage, measured from our side of the packet. */
    private void explosion(long now, S27PacketExplosion packet) {
        double radius = packet.getStrength() * 2.0;
        if (radius <= 0.0) {
            return;
        }
        double distance = player.getDistance(packet.getX(), packet.getY(), packet.getZ()) / radius;
        if (distance > 1.0) {
            return;
        }
        Vec3 center = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        double exposure = world.getBlockDensity(center, player.getEntityBoundingBox());
        double impact = (1.0 - distance) * exposure;
        float damage = (float) (int) ((impact * impact + impact) / 2.0 * 8.0 * radius + 1.0);
        detector.addAttack(BlockhitDetector.Attack.simple(now,
                kind(DamageSource.setExplosionSource(null)), damage));
    }

    @Override
    public BlockhitDetector.Snapshot snapshot(long now) {
        BlockhitDetector.Snapshot state = new BlockhitDetector.Snapshot();
        state.blocking = detector.serverSawBlock(now, isSwordBlocking(player), pingMillis());
        state.armorPoints = player.getTotalArmorValue();
        PotionEffect resistance = player.getActivePotionEffect(Potion.resistance);
        state.resistance = resistance == null ? -1 : resistance.getAmplifier();
        state.fireImmune = player.isPotionActive(Potion.fireResistance);
        state.difficulty = world.getDifficulty().getDifficultyId();
        state.absorption = player.getAbsorptionAmount();
        Set<Integer> nearby = new HashSet<Integer>();
        for (Entity entity : world.loadedEntityList) {
            if (entity != player && player.getDistanceSqToEntity(entity) <= NEARBY_SQ) {
                nearby.add(Integer.valueOf(entity.getEntityId()));
            }
        }
        state.nearby = nearby;
        final ItemStack[] armor = new ItemStack[4];
        for (int slot = 0; slot < armor.length; slot++) {
            ItemStack piece = player.inventory.armorInventory[slot];
            armor[slot] = piece == null ? null : piece.copy();
        }
        state.protection = new BlockhitDetector.Protection() {
            @Override
            public int against(Object source) {
                return protection(armor, (DamageSource) source);
            }
        };
        return state;
    }

    @Override
    public void collectHitCandidates(long now) {
        scanProjectiles(now);
        environment(now);
    }

    /**
     * EntityPlayer.attackTargetEntityWithCurrentItem. Other players' attack attributes are not
     * synced, so rebuild them from the held item and visible potions.
     */
    private BlockhitDetector.Attack melee(EntityLivingBase attacker, long now, boolean swing) {
        ItemStack held = attacker.getHeldItem();
        float base;
        if (attacker instanceof EntityPlayer) {
            IAttributeInstance damage = new ServersideAttributeMap()
                    .registerAttribute(SharedMonsterAttributes.attackDamage);
            damage.setBaseValue(1.0);
            if (held != null) {
                for (AttributeModifier modifier : held.getAttributeModifiers()
                        .get(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName())) {
                    if (!damage.hasModifier(modifier)) {
                        damage.applyModifier(modifier);
                    }
                }
            }
            addPotion(damage, attacker, Potion.damageBoost);
            addPotion(damage, attacker, Potion.weakness);
            base = (float) damage.getAttributeValue();
        } else {
            IAttributeInstance damage =
                    attacker.getEntityAttribute(SharedMonsterAttributes.attackDamage);
            if (damage == null) {
                return null;
            }
            base = (float) damage.getAttributeValue();
        }
        float enchant = EnchantmentHelper.getModifierForCreature(held, player.getCreatureAttribute());
        if (base <= 0.0F && enchant <= 0.0F) {
            return null;
        }
        float normal = base + enchant;
        float[] raw = mayCrit(attacker) && base > 0.0F
                ? new float[] {normal, base * 1.5F + enchant} : new float[] {normal};
        DamageSource source = attacker instanceof EntityPlayer
                ? DamageSource.causePlayerDamage((EntityPlayer) attacker)
                : DamageSource.causeMobDamage(attacker);
        return new BlockhitDetector.Attack(now, kind(source), raw, attacker.getEntityId(), swing,
                !swing || aimingAtUs(attacker), -1);
    }

    /**
     * The server crits on fallDistance > 0, which other clients never see. Airborne is the best
     * visible proxy; the extra candidate only adds explanations, so it cannot invent a block.
     */
    private static boolean mayCrit(EntityLivingBase attacker) {
        return attacker instanceof EntityPlayer && !attacker.onGround && !attacker.isOnLadder()
                && !attacker.isInWater() && !attacker.isPotionActive(Potion.blindness)
                && attacker.ridingEntity == null;
    }

    private static void addPotion(IAttributeInstance damage, EntityLivingBase entity, Potion potion) {
        PotionEffect effect = entity.getActivePotionEffect(potion);
        AttributeModifier template = potion.getAttributeModifierMap()
                .get(SharedMonsterAttributes.attackDamage);
        if (effect != null && template != null) {
            damage.applyModifier(new AttributeModifier(template.getID(), template.getName(),
                    potion.getAttributeModifierAmount(effect.getAmplifier(), template),
                    template.getOperation()));
        }
    }

    /** Whether the attacker's look ray reaches our hitbox before any block. */
    private boolean aimingAtUs(EntityLivingBase attacker) {
        Vec3 eyes = attacker.getPositionEyes(1.0F);
        Vec3 look = attacker.getLook(1.0F);
        Vec3 end = eyes.addVector(look.xCoord * 6.0, look.yCoord * 6.0, look.zCoord * 6.0);
        double border = player.getCollisionBorderSize() + 0.2;
        AxisAlignedBB box = player.getEntityBoundingBox().expand(border, border, border);
        if (box.isVecInside(eyes)) {
            return true;
        }
        MovingObjectPosition target = box.calculateIntercept(eyes, end);
        if (target == null) {
            return false;
        }
        MovingObjectPosition block = world.rayTraceBlocks(eyes, end, false, false, true);
        return block == null || block.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || eyes.squareDistanceTo(target.hitVec) < eyes.squareDistanceTo(block.hitVec);
    }

    private void scanProjectiles(long now) {
        AxisAlignedBB box = player.getEntityBoundingBox().expand(0.3, 0.3, 0.3);
        for (Entity entity : new ArrayList<Entity>(world.loadedEntityList)) {
            if (!(entity instanceof EntityArrow || entity instanceof EntityFireball
                    || entity instanceof EntityPotion)) {
                continue;
            }
            Vec3 from = new Vec3(entity.posX, entity.posY, entity.posZ);
            Vec3 to = from.addVector(entity.motionX, entity.motionY, entity.motionZ);
            if (!box.isVecInside(from) && box.calculateIntercept(from, to) == null) {
                continue;
            }
            int key = entity.getEntityId();
            if (entity instanceof EntityArrow) {
                arrow(now, (EntityArrow) entity);
            } else if (entity instanceof EntitySmallFireball || entity instanceof EntityLargeFireball) {
                EntityFireball fireball = (EntityFireball) entity;
                detector.addAttack(new BlockhitDetector.Attack(now,
                        kind(DamageSource.causeFireballDamage(fireball, fireball.shootingEntity)),
                        new float[] {entity instanceof EntitySmallFireball ? 5.0F : 6.0F},
                        -1, false, false, key));
            } else if (entity instanceof EntityWitherSkull) {
                EntityLivingBase shooter = ((EntityWitherSkull) entity).shootingEntity;
                detector.addAttack(new BlockhitDetector.Attack(now,
                        kind(shooter == null ? DamageSource.magic : DamageSource.causeMobDamage(shooter)),
                        new float[] {shooter == null ? 5.0F : 8.0F}, -1, false, false, key));
            } else if (entity instanceof EntityPotion) {
                detector.addAttack(new BlockhitDetector.Attack(now, kind(DamageSource.magic),
                        new float[] {1.0F, 2.0F, 3.0F, 4.0F, 5.0F, 6.0F}, -1, false, false, key));
            }
        }
    }

    /** EntityArrow.onUpdate: ceil(speed * damage), crits add up to half again plus one. */
    private void arrow(long now, EntityArrow arrow) {
        double speed = Math.sqrt(arrow.motionX * arrow.motionX + arrow.motionY * arrow.motionY
                + arrow.motionZ * arrow.motionZ);
        if (speed < 0.05) {
            return;
        }
        ArrowOwner owner = arrows.get(Integer.valueOf(arrow.getEntityId()));
        Entity shooter = arrow.shootingEntity;
        if (shooter == null && owner != null) {
            shooter = world.getEntityByID(owner.id);
        }
        int power = owner == null ? -1 : owner.power;
        Set<Float> values = new LinkedHashSet<Float>();
        for (int level = power < 0 ? 0 : power; level <= (power < 0 ? 5 : power); level++) {
            double damage = 2.0 + (level > 0 ? level * 0.5 + 0.5 : 0.0);
            int low = (int) Math.ceil(speed * damage);
            int high = arrow.getIsCritical() ? low + low / 2 + 1 : low;
            for (int value = low; value <= high; value++) {
                values.add(Float.valueOf(value));
            }
        }
        float[] raw = new float[values.size()];
        int index = 0;
        for (Float value : values) {
            raw[index++] = value.floatValue();
        }
        detector.addAttack(new BlockhitDetector.Attack(now,
                kind(DamageSource.causeArrowDamage(arrow, shooter)), raw,
                shooter == null ? -1 : shooter.getEntityId(), false, false, arrow.getEntityId()));
    }

    /**
     * Unblockable and non-melee damage the player could be taking. Mostly here to make a hit that
     * one of these also explains count as ambiguous rather than blocked.
     */
    private void environment(long now) {
        AxisAlignedBB box = player.getEntityBoundingBox();
        if (world.isMaterialInBB(box, Material.fire)) {
            addSimple(now, DamageSource.inFire, 1.0F);
        }
        if (player.isInLava()) {
            addSimple(now, DamageSource.lava, 4.0F);
        }
        if (player.isBurning()) {
            addSimple(now, DamageSource.onFire, 1.0F);
        }
        if (player.isPotionActive(Potion.poison)) {
            addSimple(now, DamageSource.magic, 1.0F);
        }
        if (player.isPotionActive(Potion.wither)) {
            addSimple(now, DamageSource.wither, 1.0F);
        }
        if (player.isEntityInsideOpaqueBlock()) {
            addSimple(now, DamageSource.inWall, 1.0F);
        }
        if (player.getAir() <= 0) {
            addSimple(now, DamageSource.drown, 2.0F);
        }
        if (player.getFoodStats().getFoodLevel() == 0) {
            addSimple(now, DamageSource.starve, 1.0F);
        }
        if (player.posY < -64.0) {
            addSimple(now, DamageSource.outOfWorld, 4.0F);
        }
        float fall = Math.max(player.fallDistance, recentFallDistance);
        if (fall > 3.0F) {
            PotionEffect jump = player.getActivePotionEffect(Potion.jump);
            int jumpBoost = jump == null ? 0 : jump.getAmplifier() + 1;
            addSimple(now, DamageSource.fall, Math.max(0, (int) Math.ceil(fall - 3.0F - jumpBoost)));
        }
        BlockPos min = new BlockPos(box.minX - 0.1, box.minY, box.minZ - 0.1);
        BlockPos max = new BlockPos(box.maxX + 0.1, box.maxY, box.maxZ + 0.1);
        for (BlockPos pos : BlockPos.getAllInBox(min, max)) {
            if (world.getBlockState(pos).getBlock() == Blocks.cactus) {
                addSimple(now, DamageSource.cactus, 1.0F);
                break;
            }
        }
        for (Entity effect : world.weatherEffects) {
            if (effect instanceof EntityLightningBolt && effect.getDistanceSqToEntity(player) < 16.0) {
                addSimple(now, DamageSource.lightningBolt, 5.0F);
            }
        }
        for (Entity entity : new ArrayList<Entity>(world.loadedEntityList)) {
            if (entity instanceof EntityFallingBlock
                    && entity.getEntityBoundingBox().expand(0.0, 0.5, 0.0).intersectsWith(box)) {
                float[] raw = new float[20];
                for (int i = 0; i < raw.length; i++) {
                    raw[i] = (i + 1) * 2.0F;
                }
                boolean anvil = ((EntityFallingBlock) entity).getBlock().getBlock() == Blocks.anvil;
                detector.addAttack(BlockhitDetector.Attack.simple(now,
                        kind(anvil ? DamageSource.anvil : DamageSource.fallingBlock), raw));
            }
            if (!(entity instanceof EntityLivingBase) || entity == player
                    || entity.getDistanceSqToEntity(player) > NEARBY_SQ) {
                continue;
            }
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!(living instanceof EntityPlayer) && !living.isDead
                    && living.getDistanceSqToEntity(player) <= MOB_REACH_SQ) {
                BlockhitDetector.Attack attack = melee(living, now, false);
                if (attack != null) {
                    detector.addAttack(attack);
                }
            }
            if (EnchantmentHelper.getMaxEnchantmentLevel(Enchantment.thorns.effectId,
                    living.getInventory()) > 0) {
                detector.addAttack(new BlockhitDetector.Attack(now,
                        kind(DamageSource.causeThornsDamage(living)),
                        new float[] {1.0F, 2.0F, 3.0F, 4.0F}, living.getEntityId(), false, false, -1));
            }
        }
    }

    private void addSimple(long now, DamageSource source, float amount) {
        if (amount > 0.0F) {
            detector.addAttack(BlockhitDetector.Attack.simple(now, kind(source), amount));
        }
    }

    /** Fixed sources (fall, lava, ...) are singletons; reuse their Kind so protection caches hit. */
    private BlockhitDetector.Kind kind(DamageSource source) {
        BlockhitDetector.Kind kind = fixedKinds.get(source);
        if (kind != null) {
            return kind;
        }
        kind = new BlockhitDetector.Kind(source, source.isUnblockable(), source.isDamageAbsolute(),
                source.isDifficultyScaled(), source.isFireDamage(), source == DamageSource.outOfWorld);
        if (source.getClass() == DamageSource.class) {
            fixedKinds.put(source, kind);
        }
        return kind;
    }

    /** EnchantmentHelper.getEnchantmentModifierDamage before its cap and random roll. */
    private static int protection(ItemStack[] armor, DamageSource source) {
        int total = 0;
        for (ItemStack piece : armor) {
            if (piece == null) {
                continue;
            }
            for (Map.Entry<Integer, Integer> entry : EnchantmentHelper.getEnchantments(piece).entrySet()) {
                Enchantment enchantment = Enchantment.getEnchantmentById(entry.getKey().intValue());
                int level = entry.getValue().intValue();
                if (enchantment != null && level > 0) {
                    total += enchantment.calcModifierDamage(level, source);
                }
            }
        }
        return total;
    }

    static boolean isSwordBlocking(EntityPlayer player) {
        ItemStack held = player == null ? null : player.getHeldItem();
        return held != null && held.getItem() instanceof ItemSword && player.isBlocking();
    }

    private int pingMillis() {
        NetHandlerPlayClient handler = Minecraft.getMinecraft().getNetHandler();
        NetworkPlayerInfo info = handler == null ? null : handler.getPlayerInfo(player.getUniqueID());
        return info == null ? 0 : Math.max(0, info.getResponseTime());
    }

    private static final class ArrowOwner {
        final int id;
        final int power;

        ArrowOwner(int id, int power) {
            this.id = id;
            this.power = power;
        }
    }
}
