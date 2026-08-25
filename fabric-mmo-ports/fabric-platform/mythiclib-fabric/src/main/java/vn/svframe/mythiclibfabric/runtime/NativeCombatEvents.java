package vn.svframe.mythiclibfabric.runtime;

import java.util.Objects;
import java.util.UUID;

/** Public native Fabric combat-event surface corresponding to MythicLib 1.7.1 cancellable events. */
public final class NativeCombatEvents {
    private NativeCombatEvents() {}

    public abstract static class CancellableCombatEvent {
        private final UUID playerId;
        private final UUID otherEntityId;
        private final NativeDamageMetadata damage;
        private boolean cancelled;

        protected CancellableCombatEvent(UUID playerId, UUID otherEntityId, NativeDamageMetadata damage) {
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.otherEntityId = otherEntityId;
            this.damage = Objects.requireNonNull(damage, "damage");
        }

        public UUID playerId() { return playerId; }
        public UUID otherEntityId() { return otherEntityId; }
        public NativeDamageMetadata damage() { return damage; }
        public boolean cancelled() { return cancelled; }
        public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    }

    /** Equivalent of DamageMitigationEvent. playerId is the damaged player. */
    public static final class DamageMitigation extends CancellableCombatEvent {
        private final NativeCombatEffectRegistry.Effect type;

        public DamageMitigation(UUID playerId, UUID attackerId, NativeCombatEffectRegistry.Effect type,
                                NativeDamageMetadata damage) {
            super(playerId, attackerId, damage);
            this.type = Objects.requireNonNull(type, "type");
        }

        public UUID attackerId() { return otherEntityId(); }
        public NativeCombatEffectRegistry.Effect type() { return type; }
    }

    /** Equivalent of OnHitEffectEvent. playerId is the attacking player. */
    public static final class OnHitEffect extends CancellableCombatEvent {
        private final NativeCombatEffectRegistry.Effect effect;

        public OnHitEffect(UUID playerId, UUID targetId, NativeCombatEffectRegistry.Effect effect,
                           NativeDamageMetadata damage) {
            super(playerId, targetId, damage);
            this.effect = Objects.requireNonNull(effect, "effect");
        }

        public UUID targetId() { return otherEntityId(); }
        public NativeCombatEffectRegistry.Effect effect() { return effect; }
    }
}
