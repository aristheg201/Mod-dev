# SVRTP 1.0.2 — Fabric 1.21.1

Java 21, Fabric Loader 0.18.4 and Fabric API. Install the remapped release JAR on the **server only**. Players need no SVRTP client mod. BEconomy and CobbleDollars provide the two payment choices; unavailable economies fail safely.

1.0.1 separates the source/rollback check from random-arrival validation. Players may start on a dry solid slab, carpet, a chunk edge, or ordinary uneven terrain. The exact source bounding box must have safe collision support, clear headroom, loaded blocks and a permitted border position. Rollback preserves the exact fractional source position. Version 1.0.2 repairs destination sampling and permits dry natural slopes without weakening body-collision, hazard or border checks.

`/rtp` opens a native six-row chest menu. Choose Resource, Nether or End, then choose **5 BeastCoin** or **50,000 CobbleDollars**. `/rtp resource`, `/rtp nether` and `/rtp end` open the corresponding payment menu. The server validates each click. Inventory transfers through the menu are disabled.

`/rtp reload` validates and swaps the complete configuration, cancels pending searches and clears the location cache. Invalid reloads retain the previous configuration. `/rtp status` shows mappings and request metrics. Both require `svrtp.admin`. Console access is supported. Existing `/rtp` commands are detected during registration rather than replaced.

## Configuration and permissions

The first start creates `config/svrtp.json`. **Resource starts unconfigured**, so it cannot silently become Overworld. For this server, set its dimension to `minigamedim:minigame`. All three mappings are editable. The production example is in `config/svrtp.json.example`; it is a starting point, not a claim that the entire configured annulus is pregenerated.

LuckPerms checks use the player's current permission context: `svrtp.use`, `svrtp.use.resource`, `svrtp.use.nether`, `svrtp.use.end` and `svrtp.admin`. Nodes are configurable. Without LuckPerms, permission checks fall back to OP level 2. Grant ordinary player nodes explicitly when LuckPerms is installed.

The default limits are two requests, 24 attempts, 15 seconds, four validation work units per tick and 256 cached positions. Generation is **unsupported and disabled**; setting `allowChunkGeneration=true` is rejected. Pregenerate the destination areas separately during maintenance. The sampler indexes existing region headers and chunk Status metadata on one bounded I/O worker. It selects FULL centers with the surrounding statuses Minecraft 1.21.1 actually requires for its loading ticket, rather than demanding every neighbour be FULL. That native dependency halo is read again before acquiring a temporary ticket. No live world state is accessed by the worker. A snapshot lasts at most 60 seconds and reload clears it. Index limits: 256 nonempty regions, 65,536 entries, 8,192 metadata reads, five seconds, and 16,384 candidates per slot; reaching a limit produces a partial, safety-checked pool and is recorded in the log. Request preflight is bounded to two seconds. World collision checks run on the server thread; Nether searches scan at most 16 heights per work unit. Cache entries are revalidated.

Safe landing checks require dry solid support, an unobstructed actual player bounding box, and a 3×3 neighbourhood whose floor changes by at most one block. Each neighbouring surface needs clear two-block headroom. Nether arrivals additionally need six blocks of open vertical space at the center. Up to four columns per candidate chunk are inspected within the tick work budget; these checks never read an unloaded neighbour. The End requires a real solid island. No emergency platforms are created. Nether roof, liquids, fire and other hazardous blocks are rejected. A freshly created End requiring vanilla dragon-arena scanning is rejected until an administrator initializes it separately; otherwise first entry can trigger a synchronous vanilla arena scan.

The ChunkyBorder adapter uses its actual native shape, alongside vanilla WorldBorder. The audited supported pair is **Chunky 1.4.23 / ChunkyBorder 1.2.18**; another installed pair fails closed. This avoids an upstream `version.properties` classpath collision which makes its compatibility flag unreliable with the audited pair. Update and test the adapter before changing either version.

## Payment and recovery

No money is charged while searching. Immediately before transfer, the server persists a receipt, debits the selected wallet, then records the charged state. Arrival is checked immediately, at the next tick start, and during a one-second confirmation period. Unsafe displacement restores the verified origin when available and refunds the exact charged amount. Disconnect, dimension change, timeout and reload clean up pending work and tickets.

Cooldowns and receipts live in `<world>/svrtp/journal.json`. Writes use a forced temporary file and atomic rename where supported. These small durability writes are synchronous; production disk latency still needs measurement. A corrupt journal pauses paid RTP rather than overwriting it.

A crash between economy and journal writes may leave an ambiguous receipt. RTP then refuses further payment for that player. **Do not automatically credit or delete that receipt.** Reconcile its transaction ID, currency, amount and state against the economy's records, back up both stores, and resolve it during maintenance. Economy APIs do not offer a shared atomic transaction across the two files.

## Resource pack

With `resourcePackGui=true`, the title uses the `svrtp:gui` bitmap font and menu items use CustomModelData 7032100. Without the pack, use `false` for normal chest icons. This changes only the RTP menu, not vanilla chest textures globally.

Use the **merged** `SVFrame-resource-pack-with-SVRTP.zip` in production. `SVRTP-resource-pack.zip` is a vanilla/local preview; stacking it over the existing pack can replace the paper model used by existing cosmetics. Rebuild the merged pack with:

```
python3 tools/build_resourcepack.py --base /path/to/current/resource_pack.zip --output /path/to/merged.zip
```

The merger preserves every existing entry except the paper-model override list, checks duplicate entries and checks the reserved model ID. Keep a backup of `/pyp/input/resource_pack.zip` and record its hash before replacement. Publish the updated pack using the server's existing PYP workflow and update any download hash it requires.

## Build and internal QA

Copy the existing licensed server JARs into `libs/`: Cobblemon-fabric-1.8.1+1.21.1.jar, BEconomy-1.5.jar, CobbleDollars-fabric-2.0.0+Beta-6.1+1.21.1.jar, Chunky-Fabric-1.4.23.jar and ChunkyBorder-1.2.18.jar. They are compile-only and are not embedded or redistributed in this source ZIP.

```
JAVA_HOME=/path/to/jdk21 ./gradlew test remapJar
```

Use `build/libs/svrtp-1.0.2.jar`, not the development JAR. The separate QA driver is never included in the release. `runQaServer` and `runQaClient` require an isolated local fixture world, LuckPerms and Placeholder API, offline test accounts, and a real graphical display. Never run fixture setup against production. See the release QA report for observed results and unverified cases.
