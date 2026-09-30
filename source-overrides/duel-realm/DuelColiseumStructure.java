package vn.svarcade.tcg.fabric;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Physical Duel Coliseum in the dedicated void dimension.
 * Kept intentionally sparse: real blocks, real stands and architecture, but no
 * hidden solid mass that can stall the server while a match is being created.
 */
public final class DuelColiseumStructure {
    public static final int FLOOR_Y = 96;
    public static final int ARENA_SPACING = 192;
    private static final int FLAGS = 0;

    private DuelColiseumStructure() {}

    public static void build(ServerWorld world, BlockPos center) {
        buildBoard(world, center);
        buildArenaRing(world, center);
        buildStands(world, center);
        buildArchitecture(world, center);
        buildPodiums(world, center);
        buildSpectatorGalleries(world, center);
        buildLighting(world, center);
    }

    private static void buildBoard(ServerWorld w, BlockPos c) {
        fill(w, c.add(-20, 0, -13), c.add(20, 0, 13), Blocks.POLISHED_TUFF.getDefaultState());
        frame(w, c.add(-20, 1, -13), c.add(20, 1, 13), Blocks.CUT_COPPER.getDefaultState());
        lineX(w, c, -18, 18, 0, Blocks.QUARTZ_BLOCK.getDefaultState());

        for (int side : new int[]{-1, 1}) {
            int monsterZ = side * 4;
            int spellZ = side * 9;
            for (int i = 0; i < 5; i++) {
                int x = (i - 2) * 6;
                zone(w, c, x, monsterZ,
                    side < 0 ? Blocks.SMOOTH_SANDSTONE.getDefaultState() : Blocks.DEEPSLATE_TILES.getDefaultState());
                zone(w, c, x, spellZ,
                    side < 0 ? Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState() : Blocks.POLISHED_DEEPSLATE.getDefaultState());
            }
        }

        // Extra Monster Zones.
        zone(w, c, -3, 0, Blocks.GILDED_BLACKSTONE.getDefaultState());
        zone(w, c, 3, 0, Blocks.GILDED_BLACKSTONE.getDefaultState());

        // Field / Deck / Graveyard / Extra Deck / Banished landmarks.
        zone(w, c, -18, -9, Blocks.MOSSY_STONE_BRICKS.getDefaultState());
        zone(w, c, 18, 9, Blocks.MOSSY_STONE_BRICKS.getDefaultState());
        marker(w, c, 18, -9, Blocks.ENDER_CHEST.getDefaultState());
        marker(w, c, -18, 9, Blocks.ENDER_CHEST.getDefaultState());
        marker(w, c, 18, -4, Blocks.SOUL_SAND.getDefaultState());
        marker(w, c, -18, 4, Blocks.SOUL_SAND.getDefaultState());
        marker(w, c, -18, -4, Blocks.AMETHYST_BLOCK.getDefaultState());
        marker(w, c, 18, 4, Blocks.AMETHYST_BLOCK.getDefaultState());
        marker(w, c, 18, 0, Blocks.CRYING_OBSIDIAN.getDefaultState());
        marker(w, c, -18, 0, Blocks.CRYING_OBSIDIAN.getDefaultState());

        disc(w, c.add(0, 2, 0), 4, Blocks.CALCITE.getDefaultState());
        disc(w, c.add(0, 3, 0), 2, Blocks.SEA_LANTERN.getDefaultState());
    }

    private static void buildArenaRing(ServerWorld w, BlockPos c) {
        // Real annular platform around the board, not a giant hidden filled ellipse.
        ellipseRing(w, c.add(0, -1, 0), 34, 27, 22, 16, Blocks.POLISHED_BLACKSTONE.getDefaultState());
        ellipseRing(w, c, 34, 27, 31, 24, Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState());
    }

    private static void buildStands(ServerWorld w, BlockPos c) {
        // Open stepped spectator rings. They read as a coliseum from free-look while
        // keeping block count and chunk generation bounded.
        for (int tier = 0; tier < 4; tier++) {
            int ox = 27 + tier * 2;
            int oz = 20 + tier * 2;
            int y = 2 + tier * 2;
            ellipseRing(w, c.add(0, y, 0), ox, oz, ox - 2, oz - 2,
                tier % 2 == 0 ? Blocks.DEEPSLATE_TILES.getDefaultState() : Blocks.POLISHED_DEEPSLATE.getDefaultState());
            ellipseRing(w, c.add(0, y + 1, 0), ox, oz, ox - 1, oz - 1, Blocks.DARK_PRISMARINE.getDefaultState());
        }
    }

    private static void buildArchitecture(ServerWorld w, BlockPos c) {
        // Thin crowns provide the high silhouette without an 11-layer solid wall.
        for (int y : new int[]{2, 6, 10}) {
            ellipseRing(w, c.add(0, y, 0), 34, 27, 32, 25,
                y == 10 ? Blocks.CHISELED_DEEPSLATE.getDefaultState() : Blocks.DEEPSLATE_BRICKS.getDefaultState());
        }

        pillar(w, c.add(-32, 2, 0), 11);
        pillar(w, c.add(32, 2, 0), 11);
        pillar(w, c.add(0, 2, -25), 11);
        pillar(w, c.add(0, 2, 25), 11);
        pillar(w, c.add(-24, 2, -19), 8);
        pillar(w, c.add(24, 2, -19), 8);
        pillar(w, c.add(-24, 2, 19), 8);
        pillar(w, c.add(24, 2, 19), 8);

        archX(w, c.add(0, 11, -25), 9);
        archX(w, c.add(0, 11, 25), 9);
        archZ(w, c.add(-32, 11, 0), 8);
        archZ(w, c.add(32, 11, 0), 8);
    }

    private static void buildPodiums(ServerWorld w, BlockPos c) {
        podium(w, c.add(0, 2, 18), false);
        podium(w, c.add(0, 2, -18), true);
    }

    private static void buildSpectatorGalleries(ServerWorld w, BlockPos c) {
        for (int i = 0; i < 4; i++) {
            int z = -9 + i * 6;
            gallerySeat(w, c.add(-27, 5, z), true);
            gallerySeat(w, c.add(27, 5, z), false);
        }
    }

    private static void buildLighting(ServerWorld w, BlockPos c) {
        for (int x : new int[]{-18, -9, 0, 9, 18}) {
            lanternPost(w, c.add(x, 2, -15));
            lanternPost(w, c.add(x, 2, 15));
        }
        for (int z : new int[]{-10, 0, 10}) {
            lanternPost(w, c.add(-23, 2, z));
            lanternPost(w, c.add(23, 2, z));
        }
    }

    private static void podium(ServerWorld w, BlockPos p, boolean north) {
        fill(w, p.add(-4, 0, -2), p.add(4, 0, 2), Blocks.CUT_COPPER.getDefaultState());
        fill(w, p.add(-3, 1, -1), p.add(3, 1, 1), Blocks.WAXED_CUT_COPPER.getDefaultState());
        int dz = north ? -2 : 2;
        fill(w, p.add(-4, 1, dz), p.add(4, 3, dz), Blocks.TINTED_GLASS.getDefaultState());
    }

    private static void gallerySeat(ServerWorld w, BlockPos p, boolean eastFacing) {
        fill(w, p.add(-2, 0, -2), p.add(2, 0, 2), Blocks.SMOOTH_QUARTZ.getDefaultState());
        int dx = eastFacing ? -2 : 2;
        fill(w, p.add(dx, 1, -2), p.add(dx, 3, 2), Blocks.TINTED_GLASS.getDefaultState());
    }

    private static void pillar(ServerWorld w, BlockPos p, int height) {
        for (int y = 0; y < height; y++) {
            BlockState state = y % 4 == 0
                ? Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState()
                : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
            fill(w, p.add(-1, y, -1), p.add(1, y, 1), state);
        }
        place(w, p.add(0, height, 0), Blocks.BEACON.getDefaultState());
    }

    private static void archX(ServerWorld w, BlockPos c, int half) {
        for (int x = -half; x <= half; x++) {
            int rise = (int)Math.round(4.0 * (1.0 - Math.pow(x / (double)half, 2)));
            place(w, c.add(x, rise, 0), Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
            place(w, c.add(x, rise - 1, 0), Blocks.CYAN_STAINED_GLASS.getDefaultState());
        }
    }

    private static void archZ(ServerWorld w, BlockPos c, int half) {
        for (int z = -half; z <= half; z++) {
            int rise = (int)Math.round(4.0 * (1.0 - Math.pow(z / (double)half, 2)));
            place(w, c.add(0, rise, z), Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
            place(w, c.add(0, rise - 1, z), Blocks.CYAN_STAINED_GLASS.getDefaultState());
        }
    }

    private static void lanternPost(ServerWorld w, BlockPos p) {
        fill(w, p, p.add(0, 3, 0), Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState());
        place(w, p.add(0, 4, 0), Blocks.SEA_LANTERN.getDefaultState());
    }

    private static void zone(ServerWorld w, BlockPos c, int cx, int cz, BlockState state) {
        fill(w, c.add(cx - 2, 1, cz - 2), c.add(cx + 2, 1, cz + 2), state);
        frame(w, c.add(cx - 2, 1, cz - 2), c.add(cx + 2, 1, cz + 2), Blocks.CUT_COPPER.getDefaultState());
    }

    private static void marker(ServerWorld w, BlockPos c, int cx, int cz, BlockState state) {
        fill(w, c.add(cx - 1, 1, cz - 1), c.add(cx + 1, 2, cz + 1), state);
    }

    private static void disc(ServerWorld w, BlockPos c, int radius, BlockState state) {
        int r2 = radius * radius;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
            if (x * x + z * z <= r2) place(w, c.add(x, 0, z), state);
    }

    private static void ellipseRing(ServerWorld w, BlockPos c, int orx, int orz, int irx, int irz, BlockState state) {
        for (int x = -orx; x <= orx; x++) for (int z = -orz; z <= orz; z++) {
            double outer = x * x / (double)(orx * orx) + z * z / (double)(orz * orz);
            double inner = x * x / (double)(irx * irx) + z * z / (double)(irz * irz);
            if (outer <= 1.0 && inner >= 1.0) place(w, c.add(x, 0, z), state);
        }
    }

    private static void fill(ServerWorld w, BlockPos a, BlockPos b, BlockState state) {
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++)
            place(w, new BlockPos(x, y, z), state);
    }

    private static void frame(ServerWorld w, BlockPos a, BlockPos b, BlockState state) {
        int minX = Math.min(a.getX(), b.getX()), maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()), maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ()), maxZ = Math.max(a.getZ(), b.getZ());
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                place(w, new BlockPos(x, y, minZ), state);
                place(w, new BlockPos(x, y, maxZ), state);
            }
            for (int z = minZ; z <= maxZ; z++) {
                place(w, new BlockPos(minX, y, z), state);
                place(w, new BlockPos(maxX, y, z), state);
            }
        }
    }

    private static void lineX(ServerWorld w, BlockPos c, int from, int to, int z, BlockState state) {
        for (int x = from; x <= to; x++) place(w, c.add(x, 1, z), state);
    }

    private static void place(ServerWorld w, BlockPos p, BlockState state) {
        w.setBlockState(p, state, FLAGS);
    }
}
