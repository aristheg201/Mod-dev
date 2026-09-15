package vn.svframe.svrelationships.family;

public record InheritanceDefinition(
        String id,
        int guaranteedIvCount,
        double natureChance,
        double abilityChance,
        boolean inheritMoves,
        String aspectMode,
        String personalityMode
) {
    public InheritanceDefinition {
        if (guaranteedIvCount < 0 || guaranteedIvCount > 6) throw new IllegalArgumentException("guaranteedIvCount");
        if (natureChance < 0 || natureChance > 1) throw new IllegalArgumentException("natureChance");
        if (abilityChance < 0 || abilityChance > 1) throw new IllegalArgumentException("abilityChance");
    }
}
