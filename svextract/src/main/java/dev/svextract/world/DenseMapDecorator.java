package dev.svextract.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

/**
 * Deterministic bounded block-detail pass over existing handcrafted map blueprints.
 * No runtime RNG or per-tick scene generation. Decorations use bounded native blocks.
 * Static cover and interiors improve play spaces without increasing active AI cost.
 */
public final class DenseMapDecorator {
    private DenseMapDecorator() {}

    private static final class Builder {
        private final LinkedHashMap<BlockPos, BlockState> cells = new LinkedHashMap<>();
        Builder(List<StationBlueprint.Change> base) {
            for (StationBlueprint.Change c : base) cells.put(c.position(), c.state());
        }
        void at(int x, int y, int z, Block block) {
            cells.put(new BlockPos(x, y, z), block.getDefaultState());
        }
        void box(int x1,int y1,int z1,int x2,int y2,int z2,Block block) {
            for (int x=x1;x<=x2;x++)for (int y=y1;y<=y2;y++)for (int z=z1;z<=z2;z++)at(x,y,z,block);
        }
        void shell(int x1,int z1,int x2,int z2,int height,Block walls,Block roofing) {
            box(x1,65,z1,x2,65+height,z1,walls);
            box(x1,65,z2,x2,65+height,z2,walls);
            box(x1,65,z1,x1,65+height,z2,walls);
            box(x2,65,z1,x2,65+height,z2,walls);
            box(x1,66+height,z1,x2,66+height,z2,roofing);
            // The caller chooses and carves the actual entrance for each structure.
        }
        void streetLamp(int x,int z) {
            box(x,65,z,x,69,z,Blocks.POLISHED_BLACKSTONE);
            box(x-1,69,z,x+1,69,z,Blocks.IRON_BARS);
            at(x-1,69,z,Blocks.SEA_LANTERN);
            at(x+1,69,z,Blocks.SEA_LANTERN);
        }
        void cratePile(int x,int z) {
            box(x,65,z,x+2,66,z+1,Blocks.SPRUCE_PLANKS);
            at(x+1,67,z+1,Blocks.STRIPPED_SPRUCE_LOG);
            box(x,65,z+2,x+2,65,z+2,Blocks.IRON_BARS);
        }
        void barricade(int x,int z) {
            box(x,65,z,x+3,65,z,Blocks.DEEPSLATE_BRICK_WALL);
            at(x+1,66,z,Blocks.IRON_BARS);
            at(x+2,66,z,Blocks.IRON_BARS);
        }
        void crate(int x,int z) {
            // Reapply authoritatively after decoration so caches never vanish behind props.
            at(x,65,z,dev.svextract.Content.SUPPLY_CRATE);
            at(x,66,z,Blocks.CHAIN);
        }
        List<StationBlueprint.Change> result() {
            List<StationBlueprint.Change> result = new ArrayList<>(cells.size());
            for(Map.Entry<BlockPos,BlockState> e : cells.entrySet())
                result.add(new StationBlueprint.Change(e.getKey(),e.getValue()));
            return List.copyOf(result);
        }
    }

    public static List<StationBlueprint.Change> blackglass(List<StationBlueprint.Change> base) {
        Builder b=new Builder(base);
        // SECURITY CHECKPOINT: checkpoint control hut, cover, vehicle barriers and search lanes.
        b.box(11,64,-47,43,64,-33,Blocks.STONE_BRICKS);
        b.shell(15,-45,31,-35,5,Blocks.GRAY_CONCRETE,Blocks.DEEPSLATE_TILES);
        b.box(21,65,-35,24,68,-35,Blocks.AIR); // south entrance
        b.box(15,67,-42,15,68,-39,Blocks.GLASS_PANE);
        b.box(31,67,-42,31,68,-39,Blocks.GLASS_PANE);
        b.box(17,65,-43,20,65,-41,Blocks.POLISHED_ANDESITE);
        b.at(18,66,-42,Blocks.LECTERN);
        b.box(35,65,-47,35,66,-39,Blocks.IRON_BARS);
        b.box(39,65,-47,39,66,-39,Blocks.IRON_BARS);
        b.box(35,65,-42,39,66,-42,Blocks.AIR); // vehicle gate
        b.barricade(12,-33);b.barricade(37,-33);
        b.streetLamp(40,-35);

        // QUARANTINE CLINIC: lit infirmary, beds, ambulance loading and broken windows.
        b.box(-47,64,5,-28,64,24,Blocks.POLISHED_ANDESITE);
        b.shell(-46,7,-30,21,5,Blocks.WHITE_CONCRETE,Blocks.LIGHT_GRAY_CONCRETE);
        b.box(-30,65,12,-30,68,15,Blocks.AIR); // east courtyard entrance
        b.box(-46,67,11,-46,68,15,Blocks.GLASS_PANE);
        b.box(-43,65,10,-40,65,11,Blocks.WHITE_WOOL);
        b.box(-43,65,17,-40,65,18,Blocks.WHITE_WOOL);
        b.box(-45,66,13,-45,67,15,Blocks.RED_CONCRETE);
        b.box(-39,65,11,-37,65,11,Blocks.IRON_BLOCK);
        b.at(-38,66,11,Blocks.CAULDRON);
        b.cratePile(-34,18);
        b.streetLamp(-28,22);

        // RADIO RELAY: reinforced tower with visible antennas and walkable fenced base.
        b.box(-47,64,-44,-37,64,-26,Blocks.CRACKED_STONE_BRICKS);
        for(int x : new int[]{-46,-38})for(int z : new int[]{-42,-29})
            b.box(x,65,z,x,78,z,Blocks.IRON_BLOCK);
        b.box(-46,75,-42,-38,75,-29,Blocks.IRON_BLOCK);
        b.box(-46,76,-42,-38,76,-42,Blocks.IRON_BARS);
        b.box(-46,76,-29,-38,76,-29,Blocks.IRON_BARS);
        b.box(-46,76,-41,-46,76,-30,Blocks.IRON_BARS);
        b.box(-38,76,-41,-38,76,-30,Blocks.IRON_BARS);
        b.box(-42,77,-35,-42,87,-35,Blocks.IRON_BARS);
        b.at(-42,88,-35,Blocks.END_ROD);
        b.box(-47,65,-26,-40,66,-26,Blocks.TUFF_BRICK_WALL);
        b.box(-43,65,-26,-41,66,-26,Blocks.AIR);
        b.cratePile(-45,-38);

        // POWER SUBSTATION: transformer yards, electrical hazards and raised cable racks.
        b.box(36,64,6,49,64,29,Blocks.STONE_BRICKS);
        for(int z : new int[]{10,19,26}) {
            b.box(38,65,z,42,67,z+2,Blocks.IRON_BLOCK);
            b.box(39,68,z,41,68,z+2,Blocks.COPPER_BLOCK);
            b.box(39,69,z+1,41,69,z+1,Blocks.LIGHTNING_ROD);
        }
        b.box(45,65,10,45,68,27,Blocks.IRON_BARS);
        b.box(46,65,10,48,65,11,Blocks.CUT_COPPER);
        b.at(47,66,11,Blocks.REDSTONE_LAMP);
        b.streetLamp(47,5);

        // DRAINAGE / UTILITY ACCESS: tactical cover and open drainage routes.
        b.box(-48,64,-19,-35,64,2,Blocks.COBBLED_DEEPSLATE);
        b.box(-46,64,-16,-43,64,-1,Blocks.WATER);
        b.box(-47,65,-16,-47,66,-1,Blocks.IRON_BARS);
        b.box(-42,65,-16,-42,66,-1,Blocks.IRON_BARS);
        b.box(-40,65,-16,-37,67,-15,Blocks.DEEPSLATE_BRICKS);
        b.box(-40,65,-9,-37,67,-8,Blocks.DEEPSLATE_BRICKS);
        b.box(-39,66,-15,-38,67,-15,Blocks.GLASS_PANE);
        b.streetLamp(-36,-4);

        // Freight district: salvage apparatus, overhead gantries and readable cover.
        for(int x : new int[]{-46,-19,8,35}) {
            b.box(x,65,30,x,72,30,Blocks.IRON_BLOCK);
            b.box(x,72,30,x+6,72,30,Blocks.IRON_BARS);
            b.at(x+6,71,30,Blocks.CHAIN);
        }
        for(int z : new int[]{32,45}) {
            b.cratePile(37,z);
            b.barricade(-47,z);
        }
        b.box(10,65,39,12,66,41,Blocks.DEEPSLATE_BRICKS);
        b.box(-7,65,39,-5,66,41,Blocks.DEEPSLATE_BRICKS);
        b.streetLamp(42,44);b.streetLamp(-44,47);

        // Foundry interior: visible furnaces, cooling trough, working gantry access.
        b.box(-31,65,-39,-27,66,-39,Blocks.BLAST_FURNACE);
        b.box(-30,65,-29,-25,65,-27,Blocks.MAGMA_BLOCK);
        b.box(-34,65,-22,-34,67,-22,Blocks.AIR);
        b.box(-18,65,-36,-18,65,-27,Blocks.IRON_BLOCK);
        b.box(-18,66,-36,-18,69,-36,Blocks.IRON_BARS);
        b.box(-14,65,-30,-11,66,-28,Blocks.DEEPSLATE_TILES);
        b.streetLamp(-9,-21);

        // Research wing: desks, observation glass and interior floor furniture.
        b.box(16,65,21,18,65,23,Blocks.IRON_BLOCK);
        b.at(17,66,22,Blocks.LECTERN);
        b.box(29,65,17,33,65,19,Blocks.SMOOTH_QUARTZ);
        b.box(29,66,18,33,66,18,Blocks.GLASS_PANE);
        b.box(12,68,19,12,69,23,Blocks.GLASS_PANE);
        b.barricade(35,29);

        // Broken road, warning lamps and rubble are bounded detail, not ticking entities.
        for(int x=-45;x<=45;x+=10) {
            b.at(x,65,-7,Blocks.CRACKED_DEEPSLATE_BRICKS);
            b.at(x+2,65,6,Blocks.GRAVEL);
        }
        for(int z=-45;z<=45;z+=12){
            b.at(-6,65,z,Blocks.TUFF);
            b.at(6,65,z+1,Blocks.COBBLED_DEEPSLATE);
        }
        for(int z : new int[]{-42,-18,12,36}) {
            b.streetLamp(-8,z);b.streetLamp(8,z);
        }

        // Server-validated, physical optional contract terminals.
        b.at(18,66,-42,Blocks.LECTERN);  // security manifest
        b.at(-44,65,-38,Blocks.COPPER_BLOCK); // relay evidence terminal
        b.at(-38,65,11,Blocks.IRON_BLOCK); // clinic records
        b.at(47,66,11,Blocks.REDSTONE_LAMP); // substation logs

        // Do not overwrite mission terminals, extraction machinery or physical supply caches.
        b.at(-12,65,8,Blocks.REDSTONE_LAMP);
        b.at(16,65,16,Blocks.CHISELED_BOOKSHELF);
        b.at(24,65,16,Blocks.LODESTONE);
        b.at(-19,65,-12,Blocks.LEVER);
        b.at(43,65,-35,Blocks.LEVER);
        for(int[] c : RaidLootLocations.ALL) b.crate(c[0],c[1]);
        return b.result();
    }

    public static List<StationBlueprint.Change> redline(List<StationBlueprint.Change> base) {
        Builder b=new Builder(base);
        // Passenger concourse: waiting benches and lit information kiosks.
        for(int x : new int[]{-28,-20,-4,4}) {
            b.box(x,65,4,x+3,65,5,Blocks.DARK_OAK_SLAB);
            b.box(x,65,7,x+3,65,7,Blocks.POLISHED_BLACKSTONE_SLAB);
        }
        b.box(-7,65,-23,7,65,-21,Blocks.DEEPSLATE_TILES);
        b.box(-5,66,-23,5,67,-23,Blocks.GLASS_PANE);
        b.at(-1,65,-22,Blocks.LECTERN);
        for(int x : new int[]{-30,-14,2,18,32}) {
            b.at(x,65,-26,Blocks.STRIPPED_SPRUCE_LOG);
            b.at(x,65,26,Blocks.IRON_BLOCK);
            b.at(x,66,26,Blocks.LANTERN);
        }
        // Maintenance tool storage and a repair bench by the eastern platform.
        b.box(25,65,-24,33,65,-18,Blocks.STONE_BRICKS);
        b.box(27,66,-23,31,66,-23,Blocks.IRON_BARS);
        b.at(27,65,-21,Blocks.ANVIL);
        b.at(32,65,-20,Blocks.CRAFTING_TABLE);
        b.box(29,65,-18,31,65,-18,Blocks.AIR);
        // Debris, secured luggage and stenciled markings leave walkable paths.
        for(int x : new int[]{-31,-26,-8,8,26,31}) {
            b.box(x,65,-5,x+1,65,-5,Blocks.MOSSY_STONE_BRICKS);
            b.box(x,65,23,x+1,65,23,Blocks.TUFF_BRICKS);
        }
        b.box(-35,66,-18,-35,69,-10,Blocks.IRON_BARS);
        b.box(35,66,8,35,69,17,Blocks.IRON_BARS);
        b.at(-28,66,-10,Blocks.REDSTONE_LAMP);
        b.at(28,66,10,Blocks.REDSTONE_LAMP);
        // Signal room furnished without obstructing its primary repair terminal.
        b.at(-17,65,6,Blocks.LECTERN);
        b.at(-8,65,7,Blocks.COPPER_BLOCK);
        b.box(-20,65,9,-18,65,9,Blocks.DEEPSLATE_TILES);
        // Keep all objective markers, the extraction lever and searchable caches usable.
        b.at(-12,65,8,Blocks.REDSTONE_LAMP);
        b.at(16,65,16,Blocks.CHISELED_BOOKSHELF);
        b.at(24,65,16,Blocks.LODESTONE);
        b.at(-19,65,-12,Blocks.LEVER);
        for(int[] c : RaidLootLocations.ALL) b.crate(c[0],c[1]);
        return b.result();
    }
}
