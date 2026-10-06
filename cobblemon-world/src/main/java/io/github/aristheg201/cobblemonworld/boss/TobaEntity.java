package io.github.aristheg201.cobblemonworld.boss;

import io.github.aristheg201.cobblemonworld.integration.SvFrameRpgBridge;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class TobaEntity extends Monster {
    public static final int IDLE = 0;
    public static final int SWEEP = 1;
    public static final int SLAM = 2;
    public static final int CORRUPTION_WAVE = 3;
    public static final int PULL = 4;
    public static final int CHANNEL = 5;
    public static final int STAGGER = 6;

    private static final EntityDataAccessor<Integer> ATTACK_STATE =
            SynchedEntityData.defineId(TobaEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.literal("THE ONE BELOW ALL"),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS
    );

    private UUID ownerUuid;
    private int stateTicks;
    private int idleTicks = 35;
    private boolean acceptingRpgDamage;

    public TobaEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 0;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600.0)
                .add(Attributes.ATTACK_DAMAGE, 18.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public int getAttackState() {
        return entityData.get(ATTACK_STATE);
    }

    public boolean isBreakableState() {
        return getAttackState() == SLAM || getAttackState() == CHANNEL;
    }

    public void stagger(int ticks) {
        entityData.set(ATTACK_STATE, STAGGER);
        stateTicks = 0;
        idleTicks = Math.max(20, ticks);
        playSound(SoundEvents.WARDEN_HURT, 1.5F, 0.65F);
    }

    public boolean applyRpgDamage(ServerPlayer source, float amount, SvFrameRpgBridge.DamageFlavor flavor) {
        acceptingRpgDamage = true;
        try {
            if (SvFrameRpgBridge.attack(source, this, amount, flavor)) return true;
            return super.hurt(level().damageSources().playerAttack(source), amount);
        } finally {
            acceptingRpgDamage = false;
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!acceptingRpgDamage) return false;
        return super.hurt(source, amount);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACK_STATE, IDLE);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel serverLevel)) return;

        bossEvent.setProgress(Math.max(0.0F, getHealth() / getMaxHealth()));
        ServerPlayer owner = owner(serverLevel);
        if (owner == null || !owner.isAlive()) return;

        double distanceSqr = distanceToSqr(owner);
        if (distanceSqr > 64.0 * 64.0) return;

        int state = getAttackState();
        stateTicks++;

        if (state == STAGGER) {
            if (stateTicks >= idleTicks) setState(IDLE);
            return;
        }

        if (state == IDLE) {
            if (distanceSqr > 16.0) getNavigation().moveTo(owner, 1.05);
            if (--idleTicks <= 0) startRandomAttack();
            return;
        }

        switch (state) {
            case SWEEP -> tickSweep(serverLevel, owner);
            case SLAM -> tickSlam(serverLevel, owner);
            case CORRUPTION_WAVE -> tickWave(serverLevel, owner);
            case PULL -> tickPull(serverLevel, owner);
            case CHANNEL -> tickChannel(serverLevel, owner);
            default -> setState(IDLE);
        }
    }

    private void startRandomAttack() {
        int choice = random.nextInt(5);
        setState(switch (choice) {
            case 0 -> SWEEP;
            case 1 -> SLAM;
            case 2 -> CORRUPTION_WAVE;
            case 3 -> PULL;
            default -> CHANNEL;
        });
    }

    private void tickSweep(ServerLevel level, ServerPlayer player) {
        telegraph(level, ParticleTypes.SOUL_FIRE_FLAME, 4.5, 8);
        if (stateTicks == 18 && distanceToSqr(player) <= 6.0 * 6.0) {
            hit(player, 14.0F);
        }
        if (stateTicks >= 34) setState(IDLE);
    }

    private void tickSlam(ServerLevel level, ServerPlayer player) {
        telegraph(level, ParticleTypes.LARGE_SMOKE, 7.0, 12);
        if (stateTicks == 30 && distanceToSqr(player) <= 10.0 * 10.0) {
            hit(player, 20.0F);
            if (!TobaCombatService.isAnchored(player)) {
                Vec3 away = player.position().subtract(position()).normalize().scale(1.4).add(0.0, 0.7, 0.0);
                player.setDeltaMovement(player.getDeltaMovement().add(away));
            }
        }
        if (stateTicks >= 48) setState(IDLE);
    }

    private void tickWave(ServerLevel level, ServerPlayer player) {
        telegraph(level, ParticleTypes.SCULK_SOUL, 10.0, 18);
        if (stateTicks == 34 && distanceToSqr(player) <= 24.0 * 24.0) {
            TobaCombatService.addCorruption(player, 1);
            hit(player, 10.0F + TobaCombatService.corruption(player) * 2.0F);
        }
        if (stateTicks >= 52) setState(IDLE);
    }

    private void tickPull(ServerLevel level, ServerPlayer player) {
        telegraph(level, ParticleTypes.PORTAL, 8.0, 8);
        if (!TobaCombatService.isAnchored(player) && stateTicks % 2 == 0) {
            Vec3 pull = position().subtract(player.position());
            if (pull.lengthSqr() > 1.0) {
                player.setDeltaMovement(player.getDeltaMovement().add(pull.normalize().scale(0.16)));
            }
        }
        if (stateTicks == 44 && distanceToSqr(player) <= 5.0 * 5.0) hit(player, 16.0F);
        if (stateTicks >= 50) setState(IDLE);
    }

    private void tickChannel(ServerLevel level, ServerPlayer player) {
        telegraph(level, ParticleTypes.DRAGON_BREATH, 3.0, 14);
        if (stateTicks % 10 == 0) heal(4.0F);
        if (stateTicks == 70) {
            TobaCombatService.addCorruption(player, 2);
            if (distanceToSqr(player) <= 30.0 * 30.0) hit(player, 24.0F);
        }
        if (stateTicks >= 82) setState(IDLE);
    }

    private void hit(ServerPlayer player, float damage) {
        player.hurt(level().damageSources().mobAttack(this), damage);
    }

    private void telegraph(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle, double radius, int count) {
        if (stateTicks % 4 != 0) return;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 * i / count) + stateTicks * 0.04;
            level.sendParticles(particle,
                    getX() + Math.cos(angle) * radius,
                    getY() + 0.2,
                    getZ() + Math.sin(angle) * radius,
                    1, 0, 0.05, 0, 0);
        }
    }

    private void setState(int state) {
        entityData.set(ATTACK_STATE, state);
        stateTicks = 0;
        if (state == IDLE) idleTicks = 25 + random.nextInt(31);
        if (state == SLAM) playSound(SoundEvents.WARDEN_ROAR, 1.3F, 0.6F);
        if (state == CHANNEL) playSound(SoundEvents.PORTAL_TRIGGER, 1.0F, 0.55F);
    }

    private ServerPlayer owner(ServerLevel level) {
        return ownerUuid == null ? null : level.getServer().getPlayerList().getPlayer(ownerUuid);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (ownerUuid == null || ownerUuid.equals(player.getUUID())) bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        TobaEncounterService.onTobaDefeated(this);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) tag.putUUID("CWorldOwner", ownerUuid);
        tag.putInt("CWorldAttackState", getAttackState());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerUuid = tag.hasUUID("CWorldOwner") ? tag.getUUID("CWorldOwner") : null;
        entityData.set(ATTACK_STATE, tag.getInt("CWorldAttackState"));
    }
}
