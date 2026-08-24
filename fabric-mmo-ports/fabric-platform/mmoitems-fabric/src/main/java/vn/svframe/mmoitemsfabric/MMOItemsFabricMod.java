package vn.svframe.mmoitemsfabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import vn.svframe.compat.YamlLite;
import vn.svframe.mmoitemsfabric.runtime.ability.AbilityDefinition;
import vn.svframe.mmoitemsfabric.runtime.ability.AbilityRuntime;
import vn.svframe.mmoitemsfabric.runtime.ability.ItemAbilityMapper;
import vn.svframe.mmoitemsfabric.runtime.gameplay.EquipmentStats;
import vn.svframe.mmoitemsfabric.runtime.gameplay.ItemStatProfile;
import vn.svframe.mmoitemsfabric.runtime.stat.LegacyItemDefinition;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;
import vn.svframe.mythiclibfabric.MythicLibPassiveMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class MMOItemsFabricMod implements ModInitializer {
    public static final String ID = "mmoitemsfabric";
    private static final Logger LOG = Logger.getLogger("MMOItems-Fabric");
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("MMOItems");
    private static final String NBT_TYPE = "mmoitems_type";
    private static final String NBT_ID = "mmoitems_id";
    private static final String NBT_UPGRADE = "mmoitems_upgrade";
    private static final Map<String, RegisteredItem> ITEMS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SNEAK = new ConcurrentHashMap<>();
    private static volatile AbilityRuntime abilities = new AbilityRuntime();
    private static volatile MinecraftServer server;
    private static long ticks;

    @Override
    public void onInitialize() {
        reload();
        ServerLifecycleEvents.SERVER_STARTED.register(value -> {
            server = value;
            LOG.info("MMOItems Fabric online; " + definitionSummary());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(value -> { server = null; SNEAK.clear(); });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                trigger(serverPlayer, hand, serverPlayer.isSneaking() ? AbilityDefinition.Trigger.SHIFT_RIGHT_CLICK : AbilityDefinition.Trigger.RIGHT_CLICK, null);
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) trigger(serverPlayer, hand, serverPlayer.isSneaking() ? AbilityDefinition.Trigger.SHIFT_RIGHT_CLICK : AbilityDefinition.Trigger.RIGHT_CLICK, entity.getUuid());
            return ActionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
                trigger(serverPlayer, hand, AbilityDefinition.Trigger.ATTACK, entity.getUuid());
                trigger(serverPlayer, hand, serverPlayer.isSneaking() ? AbilityDefinition.Trigger.SHIFT_LEFT_CLICK : AbilityDefinition.Trigger.LEFT_CLICK, entity.getUuid());
            }
            return ActionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) trigger(serverPlayer, hand, serverPlayer.isSneaking() ? AbilityDefinition.Trigger.SHIFT_LEFT_CLICK : AbilityDefinition.Trigger.LEFT_CLICK, null);
            return ActionResult.PASS;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            Entity attacker = source.getAttacker();
            UUID attackerId = attacker == null ? null : attacker.getUuid();
            triggerEquipped(player, AbilityDefinition.Trigger.DAMAGED, attackerId, Map.of("damage", (double) damageTaken, "blocked", blocked));
            if (attacker != null) triggerEquipped(player, AbilityDefinition.Trigger.DAMAGED_BY_ENTITY, attackerId, Map.of("damage", (double) damageTaken, "blocked", blocked));
        });
        ServerTickEvents.END_SERVER_TICK.register(value -> {
            ticks++;
            for (ServerPlayerEntity player : value.getPlayerManager().getPlayerList()) {
                boolean current = player.isSneaking();
                boolean before = SNEAK.getOrDefault(player.getUuid(), false);
                if (current && !before) triggerEquipped(player, AbilityDefinition.Trigger.SNEAK, null, Map.of());
                SNEAK.put(player.getUuid(), current);
                if (ticks % 20 == 0) triggerEquipped(player, AbilityDefinition.Trigger.TIMER, null, Map.of("tick", ticks));
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = literal("mmoitems")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(literal("status").executes(ctx -> { ctx.getSource().sendFeedback(() -> Text.literal("MMOItems Fabric | " + definitionSummary()), false); return 1; }))
                    .then(literal("reload").executes(ctx -> {
                        boolean ok = reload();
                        if (!ok) { ctx.getSource().sendError(Text.literal("MMOItems reload failed.")); return 0; }
                        ctx.getSource().sendFeedback(() -> Text.literal("MMOItems reloaded | " + definitionSummary()), true); return 1;
                    }))
                    .then(literal("give")
                            .then(argument("player", EntityArgumentType.player())
                                    .then(argument("type", StringArgumentType.word())
                                            .then(argument("id", StringArgumentType.word())
                                                    .executes(ctx -> give(ctx.getSource().getPlayerOrThrow(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "type"), StringArgumentType.getString(ctx, "id"), 1))
                                                    .then(argument("amount", IntegerArgumentType.integer(1, 64)).executes(ctx -> give(ctx.getSource().getPlayerOrThrow(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "type"), StringArgumentType.getString(ctx, "id"), IntegerArgumentType.getInteger(ctx, "amount"))))))));
            dispatcher.register(root);
            dispatcher.register(literal("mi").requires(source -> source.hasPermissionLevel(2)).then(literal("status").executes(ctx -> { ctx.getSource().sendFeedback(() -> Text.literal("MMOItems Fabric | " + definitionSummary()), false); return 1; })));
        });
    }

    public static String definitionSummary() {
        int abilityCount = ITEMS.values().stream().mapToInt(item -> item.abilities.size()).sum();
        return "items=" + ITEMS.size() + ",abilities=" + abilityCount;
    }
    public static boolean hasItem(String type, String id) { return ITEMS.containsKey(key(type, id)); }
    public static ItemStack createStack(String type, String id, int amount) {
        RegisteredItem item = ITEMS.get(key(type, id));
        if (item == null) return ItemStack.EMPTY;
        Item vanilla = Registries.ITEM.get(identifier(item.definition.material()));
        if (vanilla == Items.AIR) vanilla = Items.STONE;
        ItemStack stack = new ItemStack(vanilla, Math.max(1, amount));
        if (item.definition.name() != null && !item.definition.name().equals("null") && !item.definition.name().isBlank()) stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(stripTags(item.definition.name())));
        Object customModel = item.definition.stats().get("custom-model-data");
        if (customModel instanceof Number number) stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(number.intValue()));
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
            nbt.putString(NBT_TYPE, item.type);
            nbt.putString(NBT_ID, item.definition.id());
            nbt.putInt(NBT_UPGRADE, 0);
        });
        return stack;
    }
    public static EquipmentStats equipmentStats(ServerPlayerEntity player) {
        EquipmentStats out = new EquipmentStats();
        addStats(out, player.getMainHandStack()); addStats(out, player.getOffHandStack());
        for (ItemStack stack : player.getArmorItems()) addStats(out, stack);
        return out;
    }

    public static boolean fireItemPacketTrigger(ServerPlayerEntity player, String triggerName) {
        if (player == null || triggerName == null || triggerName.isBlank()) return false;
        final AbilityDefinition.Trigger trigger;
        try {
            trigger = AbilityDefinition.Trigger.valueOf(triggerName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return false;
        }

        ItemStack stack = switch (triggerName.trim().toUpperCase(Locale.ROOT)) {
            case "SHOOT_BOW", "SHOOT_TRIDENT" -> player.getActiveItem().isEmpty() ? player.getMainHandStack() : player.getActiveItem();
            default -> player.getMainHandStack();
        };
        if (stack.isEmpty()) return false;
        triggerStack(player, stack, trigger, null, Map.of("packet-trigger", true));
        return true;
    }

    private static int give(ServerPlayerEntity source, ServerPlayerEntity target, String type, String id, int amount) {
        ItemStack stack = createStack(type, id, amount);
        if (stack.isEmpty()) { source.getCommandSource().sendError(Text.literal("Unknown MMOItem " + type + ":" + id)); return 0; }
        if (!target.getInventory().insertStack(stack)) target.dropItem(stack, false);
        source.getCommandSource().sendFeedback(() -> Text.literal("Gave " + type + ":" + id + " x" + amount + " to " + target.getName().getString()), true);
        return 1;
    }

    private static boolean reload() {
        try {
            Map<String, RegisteredItem> next = new LinkedHashMap<>();
            AbilityRuntime nextRuntime = new AbilityRuntime();
            for (Path file : yamlFiles(ROOT.resolve("item"))) {
                String type = removeExtension(file.getFileName().toString()).toUpperCase(Locale.ROOT);
                Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
                for (Map.Entry<String, Object> entry : root.entrySet()) {
                    if (!(entry.getValue() instanceof Map<?, ?> raw)) continue;
                    @SuppressWarnings("unchecked") Map<String, Object> section = (Map<String, Object>) raw;
                    LegacyItemDefinition definition = LegacyItemDefinition.from(entry.getKey(), section);
                    List<AbilityDefinition> itemAbilities = ItemAbilityMapper.fromItemSection(section);
                    next.put(key(type, entry.getKey()), new RegisteredItem(type, definition, itemAbilities));
                    for (AbilityDefinition ability : itemAbilities) nextRuntime.register(ability.type(), MMOItemsFabricMod::executeAbility);
                }
            }
            ITEMS.clear(); ITEMS.putAll(next); abilities = nextRuntime; return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load MMOItems legacy configuration", e); return false;
        }
    }

    private static boolean executeAbility(AbilityDefinition definition, AbilityRuntime.Context context) {
        UUID target = null;
        Object targetValue = context.data().get("target");
        if (targetValue instanceof UUID uuid) target = uuid;
        Map<String, Object> parameters = new LinkedHashMap<>(definition.parameters());
        parameters.putAll(context.data());
        return MythicLibFabricMod.castSkill(definition.type(), context.player(), target == null ? context.player() : target, parameters);
    }

    private static void trigger(ServerPlayerEntity player, Hand hand, AbilityDefinition.Trigger trigger, UUID target) {
        triggerStack(player, player.getStackInHand(hand), trigger, target, Map.of());
        firePassive(player, trigger, target, Map.of());
    }

    private static void triggerEquipped(ServerPlayerEntity player, AbilityDefinition.Trigger trigger, UUID target, Map<String, Object> data) {
        Set<String> seen = ConcurrentHashMap.newKeySet();
        List<ItemStack> stacks = new ArrayList<>(); stacks.add(player.getMainHandStack()); stacks.add(player.getOffHandStack()); player.getArmorItems().forEach(stacks::add);
        for (ItemStack stack : stacks) {
            RegisteredItem item = item(stack); if (item == null || !seen.add(item.type + ':' + item.definition.id())) continue;
            triggerStack(player, stack, trigger, target, data);
        }
        if (trigger != AbilityDefinition.Trigger.TIMER) firePassive(player, trigger, target, data);
    }

    private static void firePassive(ServerPlayerEntity player, AbilityDefinition.Trigger trigger, UUID target, Map<String, ?> data) {
        try {
            MythicLibPassiveMod.fire(player.getUuid(), trigger.name(), target == null ? player.getUuid() : target, data);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void triggerStack(ServerPlayerEntity player, ItemStack stack, AbilityDefinition.Trigger trigger, UUID target, Map<String, Object> extra) {
        RegisteredItem item = item(stack); if (item == null || item.abilities.isEmpty()) return;
        Map<String, Object> data = new LinkedHashMap<>(extra); if (target != null) data.put("target", target); data.put("item_type", item.type); data.put("item_id", item.definition.id());
        abilities.fire(item.abilities, new AbilityRuntime.Context(player.getUuid(), trigger, Map.copyOf(data)), System.currentTimeMillis());
    }
    private static RegisteredItem item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA); if (component == null) return null;
        NbtCompound nbt = component.copyNbt();
        String type = nbt.getString(NBT_TYPE); String id = nbt.getString(NBT_ID);
        if (type.isEmpty() || id.isEmpty()) return null;
        return ITEMS.get(key(type, id));
    }
    private static void addStats(EquipmentStats target, ItemStack stack) { RegisteredItem item = item(stack); if (item != null) target.add(ItemStatProfile.from(item.definition.stats())); }
    private static List<Path> yamlFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        try (var stream = Files.walk(directory)) { return stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml")).sorted().toList(); }
    }
    private static String key(String type, String id) { return (type + ':' + id).trim().toUpperCase(Locale.ROOT); }
    private static String removeExtension(String name) { int dot = name.lastIndexOf('.'); return dot < 0 ? name : name.substring(0, dot); }
    private static Identifier identifier(String material) {
        String value = material == null ? "stone" : material.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("minecraft:")) return Identifier.of(value);
        value = value.replace(' ', '_'); return Identifier.of("minecraft:" + value);
    }
    private static String stripTags(String value) { return value.replaceAll("<[^>]+>", "").replaceAll("&[0-9A-FK-ORa-fk-or]", ""); }
    private record RegisteredItem(String type, LegacyItemDefinition definition, List<AbilityDefinition> abilities) {}
}
