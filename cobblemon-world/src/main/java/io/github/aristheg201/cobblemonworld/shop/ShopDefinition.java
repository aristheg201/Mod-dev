package io.github.aristheg201.cobblemonworld.shop;

import java.util.List;
import java.util.Map;

public record ShopDefinition(String id, String npc, String titleKey, String descriptionKey,
                             List<String> categories, List<String> requiredFlags,
                             List<String> registryNamespaces, List<String> excludedItems,
                             List<Rule> rules, Map<String, Override> overrides, List<ShopEntry> entries) {
    public record Rule(String pattern, List<String> categories, int price) {}
    public record Override(List<String> categories, Integer price, Boolean excluded) {}
}
