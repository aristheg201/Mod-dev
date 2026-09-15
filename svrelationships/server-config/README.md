# SVRelationships server config pack

This folder is a **drop-in server configuration**, not Java source and not a JAR replacement.

Copy:

`svrelationships/server-config/config/svrelationships/`

into your server's:

`config/svrelationships/`

and overwrite the matching GUI/language files. Then run:

`/svrel config reload`

or restart the server.

## Important styling note

The current runtime resolves placeholders and then creates literal Minecraft text. It does **not** parse MiniMessage tags yet. Therefore this pack intentionally uses clean Unicode hierarchy (`♥`, `✦`, `•`, `→`) instead of fake `<gold>` / `<gradient>` tags that would otherwise appear literally in-game.

The localization keys are grouped under `ui.*` so they do not collide with older `gui.*` keys that may still exist in the base locale file.
