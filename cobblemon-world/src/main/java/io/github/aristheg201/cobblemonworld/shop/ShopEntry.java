package io.github.aristheg201.cobblemonworld.shop;

import java.util.List;

/** Canonical server catalog row. The client can request its id, never its item or price. */
public record ShopEntry(String id, String item, int quantity, int price, List<String> categories,
                        List<String> requiredFlags, int sortOrder, boolean available) {}
