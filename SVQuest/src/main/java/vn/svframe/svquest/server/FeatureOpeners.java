package vn.svframe.svquest.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import vn.svframe.svquest.SVQuest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Opens server systems through their real runtime APIs instead of inventing commands. */
public final class FeatureOpeners {
    private FeatureOpeners() {}

    /** @return true when this id is owned by a direct opener, even if opening failed safely. */
    public static boolean handle(ServerPlayerEntity player, String id) {
        return switch (id) {
            case "battle_tower" -> { openBattleTower(player); yield true; }
            case "breeding" -> { openSoulBreeding(player); yield true; }
            default -> false;
        };
    }

    private static void openBattleTower(ServerPlayerEntity player) {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon_battle_tower")) {
            player.sendMessage(Text.literal("§cBattle Tower chưa được nạp trên server."), false);
            return;
        }
        try {
            ServerWorld world = player.getServerWorld();
            BlockPos origin = player.getBlockPos();
            Class<?> towerBlockClass = Class.forName("battle.tower.block.HoloBattleTowerBlock");
            BlockPos nearest = null;
            int nearestSq = Integer.MAX_VALUE;

            int horizontal = 16;
            int vertical = 8;
            for (int dy = -vertical; dy <= vertical; dy++) {
                for (int dx = -horizontal; dx <= horizontal; dx++) {
                    for (int dz = -horizontal; dz <= horizontal; dz++) {
                        BlockPos pos = origin.add(dx, dy, dz);
                        Object block = world.getBlockState(pos).getBlock();
                        if (!towerBlockClass.isInstance(block)) continue;
                        int d = dx * dx + dy * dy + dz * dz;
                        if (d < nearestSq) {
                            nearestSq = d;
                            nearest = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
                        }
                    }
                }
            }

            if (nearest == null) {
                player.sendMessage(Text.literal("§eBattle Tower mở bằng terminal. Hãy đứng gần Holo Battle Tower block rồi bấm lại."), false);
                return;
            }

            Class<?> helper = Class.forName("battle.tower.platform.NetworkHelper");
            Method open = helper.getMethod("sendBattleTowerOpenScreen", ServerPlayerEntity.class, BlockPos.class);
            open.invoke(null, player, nearest);
        } catch (Throwable t) {
            SVQuest.LOGGER.warn("Battle Tower block opener failed safely for {}: {}", player.getName().getString(), t.toString());
            player.sendMessage(Text.literal("§cKhông mở được Battle Tower terminal lúc này."), false);
        }
    }

    private static void openSoulBreeding(ServerPlayerEntity player) {
        if (!FabricLoader.getInstance().isModLoaded("soulbreeding")) {
            player.sendMessage(Text.literal("§cSoulBreeding chưa được nạp trên server."), false);
            return;
        }
        try {
            Class<?> permissions = Class.forName("org.dev.fil.soulbreeding.util.Permissions");
            Object permissionInstance = permissions.getField("INSTANCE").get(null);
            Method hasPermission = permissions.getMethod("hasPermission", ServerPlayerEntity.class, String.class);
            boolean allowed = Boolean.TRUE.equals(hasPermission.invoke(permissionInstance, player, "soulbreeding.breed"));
            if (!allowed) {
                player.sendMessage(Text.literal("§cBạn không có quyền dùng Daycare/Breeding."), false);
                return;
            }

            Class<?> guiClass = Class.forName("org.dev.fil.soulbreeding.gui.NestListGui");
            Constructor<?> constructor = guiClass.getConstructor(ServerPlayerEntity.class);
            Object gui = constructor.newInstance(player);
            guiClass.getMethod("open").invoke(gui);
        } catch (Throwable t) {
            SVQuest.LOGGER.warn("SoulBreeding opener failed safely for {}: {}", player.getName().getString(), t.toString());
            player.sendMessage(Text.literal("§cKhông mở được SoulBreeding lúc này."), false);
        }
    }
}
