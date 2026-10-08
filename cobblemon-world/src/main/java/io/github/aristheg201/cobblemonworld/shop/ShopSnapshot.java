package io.github.aristheg201.cobblemonworld.shop;

import java.util.List;

public record ShopSnapshot(String id, String titleKey, String descriptionKey, String merchant,
                           String balance, boolean economyAvailable, List<String> categories,
                           List<ShopEntry> entries, String result) {}
