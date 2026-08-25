package vn.svframe.mythiclibfabric;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import vn.svframe.mythiclibfabric.runtime.NativeStatEngine;
import vn.svframe.mythiclibfabric.runtime.NativeStatHandler;

import java.text.DecimalFormat;
import java.util.Objects;

/** Native Fabric counterpart of MythicLib 1.7.1 AttributeStatHandler. */
public class FabricAttributeStatHandler extends NativeStatHandler {
    private static final Identifier ATTRIBUTE_KEY = Identifier.of(MythicLibFabricMod.ID, "main");
    private static final double EPSILON = 1.0E-4d;

    private final RegistryEntry<EntityAttribute> attribute;
    private final double playerDefaultBase;

    public FabricAttributeStatHandler(String stat, RegistryEntry<EntityAttribute> attribute, double playerDefaultBase) {
        this(stat, attribute, playerDefaultBase, 0.0d, null, null, new DecimalFormat("0.#"));
    }

    public FabricAttributeStatHandler(String stat,
                                      RegistryEntry<EntityAttribute> attribute,
                                      double playerDefaultBase,
                                      MythicLibStatSettings.Entry settings) {
        this(stat, attribute, playerDefaultBase,
                settings.baseValue(), settings.minValue(), settings.maxValue(), settings.decimalFormat());
    }

    public FabricAttributeStatHandler(String stat,
                                      RegistryEntry<EntityAttribute> attribute,
                                      double playerDefaultBase,
                                      double configuredBase,
                                      Double minValue,
                                      Double maxValue,
                                      DecimalFormat decimalFormat) {
        super(stat, configuredBase, minValue, maxValue, decimalFormat);
        this.attribute = Objects.requireNonNull(attribute, "attribute");
        this.playerDefaultBase = playerDefaultBase;
        setUpdateOnLogin(true);
        addUpdateListener(this::updateAttributeModifierValue);
    }

    public RegistryEntry<EntityAttribute> attribute() {
        return attribute;
    }

    @Override
    public double getBaseValue(NativeStatEngine.StatInstance instance) {
        EntityAttributeInstance vanilla = requireAttribute(instance);
        return configuredBaseValue() + vanilla.getBaseValue();
    }

    @Override
    public double getPlayerDefaultBase() {
        return playerDefaultBase;
    }

    @Override
    public double getFinalValue(NativeStatEngine.StatInstance instance, NativeStatEngine.EquipmentSlot actionHand) {
        return requireAttribute(instance).getValue();
    }

    protected ServerPlayerEntity requirePlayer(NativeStatEngine.StatInstance instance) {
        MinecraftServer server = MythicLibFabricMod.server();
        if (server == null) throw new IllegalStateException("Minecraft server is not available for stat " + stat());
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(instance.entityId());
        if (player == null) throw new IllegalStateException("Player " + instance.entityId() + " is not online for stat " + stat());
        return player;
    }

    protected EntityAttributeInstance requireAttribute(NativeStatEngine.StatInstance instance) {
        EntityAttributeInstance vanilla = requirePlayer(instance).getAttributeInstance(attribute);
        if (vanilla == null) {
            throw new IllegalStateException("Player attribute " + attribute + " is unavailable for stat " + stat());
        }
        return vanilla;
    }

    private void updateAttributeModifierValue(NativeStatEngine.StatInstance instance) {
        EntityAttributeInstance vanilla = requireAttribute(instance);
        vanilla.removeModifier(ATTRIBUTE_KEY);
        double total = instance.total(playerDefaultBase + configuredBaseValue(), NativeStatEngine.EquipmentSlot.MAIN_HAND);
        double amount = total - playerDefaultBase;
        if (Math.abs(amount) > EPSILON) {
            vanilla.addTemporaryModifier(new EntityAttributeModifier(
                    ATTRIBUTE_KEY,
                    amount,
                    EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }
}
