package vn.worldcomesalive.furniture;
import net.minecraft.block.Block;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
public final class Semantics {
    public static final TagKey<Block> TABLE_DISPLAY=tag("table_display"),WALL_DECORATION=tag("wall_decoration"),CLUTTER=tag("domestic_clutter");
    public static final TagKey<Block> SITTABLE=tag("sittable"),DINING=tag("dining_surface"),FOOD_STORAGE=tag("food_storage"),DRINK_STORAGE=tag("drink_storage"),COOKING=tag("cooking_station"),READING=tag("reading_place"),SLEEPING=tag("sleeping_place");
    private static TagKey<Block> tag(String name){return TagKey.of(RegistryKeys.BLOCK,Identifier.of("worldcomesalive",name));}
    private Semantics(){}
}
