package vn.svframe.lively.integration.cobblemon;

import com.cobblemon.mod.common.api.types.ElementalType;

import java.util.Locale;

/** Standard eighteen-type chart. Unknown/custom types deliberately fall back to neutral. */
final class TypeMatchup {
    private TypeMatchup() {}

    static double multiplier(ElementalType attack, Iterable<ElementalType> defenders) {
        if (attack == null || defenders == null) return 1D;
        double result = 1D;
        for (ElementalType defender : defenders) result *= single(name(attack), name(defender));
        return result;
    }

    private static String name(ElementalType type) {
        return type == null ? "" : type.getName().toLowerCase(Locale.ROOT);
    }

    private static double single(String a, String d) {
        if (a.isBlank() || d.isBlank()) return 1D;
        return switch (a) {
            case "normal" -> switch (d) { case "rock", "steel" -> .5D; case "ghost" -> 0D; default -> 1D; };
            case "fire" -> switch (d) { case "grass", "ice", "bug", "steel" -> 2D; case "fire", "water", "rock", "dragon" -> .5D; default -> 1D; };
            case "water" -> switch (d) { case "fire", "ground", "rock" -> 2D; case "water", "grass", "dragon" -> .5D; default -> 1D; };
            case "electric" -> switch (d) { case "water", "flying" -> 2D; case "electric", "grass", "dragon" -> .5D; case "ground" -> 0D; default -> 1D; };
            case "grass" -> switch (d) { case "water", "ground", "rock" -> 2D; case "fire", "grass", "poison", "flying", "bug", "dragon", "steel" -> .5D; default -> 1D; };
            case "ice" -> switch (d) { case "grass", "ground", "flying", "dragon" -> 2D; case "fire", "water", "ice", "steel" -> .5D; default -> 1D; };
            case "fighting" -> switch (d) { case "normal", "ice", "rock", "dark", "steel" -> 2D; case "poison", "flying", "psychic", "bug", "fairy" -> .5D; case "ghost" -> 0D; default -> 1D; };
            case "poison" -> switch (d) { case "grass", "fairy" -> 2D; case "poison", "ground", "rock", "ghost" -> .5D; case "steel" -> 0D; default -> 1D; };
            case "ground" -> switch (d) { case "fire", "electric", "poison", "rock", "steel" -> 2D; case "grass", "bug" -> .5D; case "flying" -> 0D; default -> 1D; };
            case "flying" -> switch (d) { case "grass", "fighting", "bug" -> 2D; case "electric", "rock", "steel" -> .5D; default -> 1D; };
            case "psychic" -> switch (d) { case "fighting", "poison" -> 2D; case "psychic", "steel" -> .5D; case "dark" -> 0D; default -> 1D; };
            case "bug" -> switch (d) { case "grass", "psychic", "dark" -> 2D; case "fire", "fighting", "poison", "flying", "ghost", "steel", "fairy" -> .5D; default -> 1D; };
            case "rock" -> switch (d) { case "fire", "ice", "flying", "bug" -> 2D; case "fighting", "ground", "steel" -> .5D; default -> 1D; };
            case "ghost" -> switch (d) { case "psychic", "ghost" -> 2D; case "dark" -> .5D; case "normal" -> 0D; default -> 1D; };
            case "dragon" -> switch (d) { case "dragon" -> 2D; case "steel" -> .5D; case "fairy" -> 0D; default -> 1D; };
            case "dark" -> switch (d) { case "psychic", "ghost" -> 2D; case "fighting", "dark", "fairy" -> .5D; default -> 1D; };
            case "steel" -> switch (d) { case "ice", "rock", "fairy" -> 2D; case "fire", "water", "electric", "steel" -> .5D; default -> 1D; };
            case "fairy" -> switch (d) { case "fighting", "dragon", "dark" -> 2D; case "fire", "poison", "steel" -> .5D; default -> 1D; };
            default -> 1D;
        };
    }
}
