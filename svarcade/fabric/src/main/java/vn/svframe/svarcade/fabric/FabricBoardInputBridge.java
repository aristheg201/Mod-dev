package vn.svframe.svarcade.fabric;

import java.util.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import vn.svframe.svarcade.config.Node;
import vn.svframe.svarcade.runtime.*;
import vn.svframe.svarcade.security.IntentGate;
import vn.svframe.svarcade.systems.board.*;

/** Server-side block-hit adapter for any definition that composes BoardInputSystem and arena board geometry. */
final class FabricBoardInputBridge {
    private FabricBoardInputBridge() { }

    static ActionResult interact(GenericGameRuntime runtime, long tick, PlayerEntity rawPlayer, World world, Hand hand, BlockHitResult hit) {
        if (world.isClient() || hand != Hand.MAIN_HAND || !(rawPlayer instanceof ServerPlayerEntity player) || runtime == null) return ActionResult.PASS;
        Optional<GenericSession> found = runtime.sessionFor(player.getUuid()); if (found.isEmpty()) return ActionResult.PASS;
        GenericSession session = found.get();
        if (session.status() != GenericSession.Status.RUNNING || session.definition().systems().stream().noneMatch(spec -> spec.id().equals(BoardInputSystem.ID))) return ActionResult.PASS;
        Node arena = session.definition().arenas().get(session.lease().arena().arena()); if (arena == null || !arena.has("board")) return ActionResult.PASS;
        if (!world.getRegistryKey().getValue().toString().equals(arena.string("world"))) return ActionResult.PASS;

        BoardAccess board = session.services().require(BoardSystem.ACCESS); BoardInputAccess input = session.services().require(BoardInputSystem.ACCESS);
        int square = square(arena.node("board"), hit.getBlockPos(), board.position());
        if (square < 0 && !player.isSneaking()) return ActionResult.PASS;
        BlockPos pos = hit.getBlockPos(); double distanceSquared = player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        BoardInputAccess.Result result = input.click(new IntentGate.Facts(player.getUuid(), tick, distanceSquared, true), square, player.isSneaking());
        return switch (result.status()) {
            case SELECTED, CANCELLED, MOVED -> ActionResult.SUCCESS;
            case REJECTED -> ActionResult.FAIL;
        };
    }

    private static int square(Node board, BlockPos block, GridPosition position) {
        board.only("origin", "file_axis", "rank_axis", "square_size");
        double[] origin = vector(board, "origin"), file = vector(board, "file_axis"), rank = vector(board, "rank_axis");
        double size = number(board.require("square_size"), "square_size"); if (!(size > 0 && size <= 64)) throw board.error("square_size", "Expected 0..64");
        double fx = norm(file), rx = norm(rank), dot = dot(file, rank);
        if (fx < 0.999 || fx > 1.001 || rx < 0.999 || rx > 1.001 || Math.abs(dot) > 0.001) throw board.error("file_axis", "Board axes must be orthonormal unit vectors");
        double dx = block.getX() + 0.5 - origin[0], dy = block.getY() + 0.5 - origin[1], dz = block.getZ() + 0.5 - origin[2];
        double fileDistance = dx * file[0] + dy * file[1] + dz * file[2], rankDistance = dx * rank[0] + dy * rank[1] + dz * rank[2];
        double nx = file[1] * rank[2] - file[2] * rank[1], ny = file[2] * rank[0] - file[0] * rank[2], nz = file[0] * rank[1] - file[1] * rank[0];
        double planeDistance = Math.abs(dx * nx + dy * ny + dz * nz); if (planeDistance > 2.5) return -1;
        int fileIndex = (int)Math.floor(fileDistance / size), rankIndex = (int)Math.floor(rankDistance / size);
        return position.square(fileIndex, rankIndex);
    }
    private static double[] vector(Node node, String key) {
        List<?> raw = node.list(key); if (raw.size() != 3) throw node.error(key, "Expected [x,y,z]");
        return new double[]{number(raw.get(0), key), number(raw.get(1), key), number(raw.get(2), key)};
    }
    private static double number(Object value, String key) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Board " + key + " must be numeric");
        double result = number.doubleValue(); if (!Double.isFinite(result)) throw new IllegalArgumentException("Board " + key + " must be finite"); return result;
    }
    private static double norm(double[] v) { return Math.sqrt(dot(v, v)); }
    private static double dot(double[] a, double[] b) { return a[0]*b[0] + a[1]*b[1] + a[2]*b[2]; }
}
