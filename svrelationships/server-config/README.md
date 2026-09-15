# SVRelationships server config pack

This folder is a **drop-in server configuration** for the matching SVRelationships JAR on branch `feature/svrelationships-production-20260915`.

Copy:

`svrelationships/server-config/config/svrelationships/`

into your server's:

`config/svrelationships/`

and overwrite the matching GUI/language files. Then run:

`/svrel config reload`

or restart the server.

## MiniMessage

The current JAR parses localization values using **Kyori MiniMessage** and converts them back to native Minecraft `Text` through Adventure Platform Fabric.

Examples used by this pack:

- `<red>`, `<gold>`, `<aqua>`, `<light_purple>`
- `<#RRGGBB>`
- `<gradient:#ff6fae:#b388ff>...</gradient>`
- `<bold>...</bold>`
- `<italic:false>`
- `<gray>`, `<dark_gray>`, `<white>`

Runtime placeholders such as `<player>`, `<pokemon>`, `<bond>`, `<romance>`, `<page>` and `<pages>` are inserted as **unparsed placeholders**, so player/Pokémon values cannot inject MiniMessage markup.

The GUI localization namespace is `ui.*` to keep it separate from legacy `gui.*` keys that may still exist in the base locale files.

## Pokémon model icons

The `owned_pokemon` and `partners` repeaters use:

`visual: pokemon_model`

This uses Cobblemon's native `pokemon_model` renderer and carries the Pokémon's species and aspects (including shiny/form/skin aspects where present), rather than using placeholder heads or blocks.
