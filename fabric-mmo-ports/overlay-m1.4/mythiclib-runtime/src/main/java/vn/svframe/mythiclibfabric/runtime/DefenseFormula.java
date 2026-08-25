package vn.svframe.mythiclibfabric.runtime;

import vn.svframe.mythiclibfabric.runtime.script.ExpressionRuntime;

import java.util.Map;
import java.util.Objects;

/** Configurable defense application matching MythicLib's formula contract. */
public final class DefenseFormula {
    public static final String DEFAULT_NATURAL = "#damage# - #defense#";
    public static final String DEFAULT_ELEMENTAL = "#damage# * (1 - (#defense# / (5 * #damage# + #defense#)))";

    private final boolean elemental;
    private final String naturalFormula;
    private final String elementalFormula;

    public DefenseFormula() { this(false); }
    public DefenseFormula(boolean elemental) { this(elemental, DEFAULT_NATURAL, DEFAULT_ELEMENTAL); }
    public DefenseFormula(boolean elemental, String naturalFormula, String elementalFormula) {
        this.elemental = elemental;
        this.naturalFormula = Objects.requireNonNullElse(naturalFormula, DEFAULT_NATURAL);
        this.elementalFormula = Objects.requireNonNullElse(elementalFormula, DEFAULT_ELEMENTAL);
    }

    public double getAppliedDamage(double defense, double damage) {
        return calculateDamage(elemental, defense, damage, naturalFormula, elementalFormula);
    }

    public static double calculateDamage(boolean elemental, double defense, double damage) {
        return calculateDamage(elemental, defense, damage, DEFAULT_NATURAL, DEFAULT_ELEMENTAL);
    }

    public static double calculateDamage(boolean elemental, double defense, double damage, String naturalFormula, String elementalFormula) {
        String formula = elemental ? Objects.requireNonNullElse(elementalFormula, DEFAULT_ELEMENTAL) : Objects.requireNonNullElse(naturalFormula, DEFAULT_NATURAL);
        String expanded = formula.replace("#defense#", Double.toString(defense)).replace("#damage#", Double.toString(damage));
        try {
            return Math.max(0.0d, new ExpressionRuntime().evaluate(expanded, Map.of()));
        } catch (RuntimeException ex) {
            return Math.max(0.0d, damage);
        }
    }
}
