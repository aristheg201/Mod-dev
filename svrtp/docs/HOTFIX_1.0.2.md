# SVRTP 1.0.2 destination-search hotfix

The 1.0.1 sampler chose uniform coordinates over largely uncreated terrain, required all 25 neighbouring chunks to be FULL, and demanded a perfectly flat 5x5 landing area. Those requirements caused repeated no-location failures on the actual server's sparse/natural terrain. The 25th attempt in rejection logs was an off-by-one diagnostic error.

1.0.2 indexes existing region entries and their Status on a bounded read-only worker. It selects FULL center chunks with the actual Minecraft 1.21.1 loading-ticket dependency halo and rechecks that halo before activation. It never accesses live world state on the worker or creates region files for speculative disk queries. Index snapshots expire after 60 seconds and reload cancels them. Surface validation accepts dry natural slopes within a supported 3x3 area, while preserving body collision, headroom, liquid, hazard, height and both border checks. Nether additionally requires open space above the center; End requires solid terrain. Up to four columns per candidate are checked within the tick budget. Rejections log their category. Sampling and retries preserve the configured annulus. Payments and existing journal schema are unchanged.

## Observed QA

- PASS: 10 JUnit tests and remapped Java 21/Fabric release build.
- PASS: nine real local graphical-client paid arrivals on natural terrain (three Resource, three Nether, three End), using copied server terrain and the actual block-provider mods. Real native chest clicks and Minecraft movement packets were used. Each cost exactly 5 BeastCoin and preserved health; tickets/jobs/I/O were cleaned up.
- PASS: the first End sample contained three FULL chunks with only air. All candidates were rejected without charging; adding a copied natural island sample allowed safe End arrivals.
- OPEN: cold dimension arrival produced multi-second stalls. A later diagnostic run also observed newly FULL chunks while another search was pending in the same world; that observation does not yet distinguish search work from ongoing ordinary player-view generation after the previous arrival. Do not claim generation/performance acceptance is complete.
- NOT TESTED on this final build: the full thirteen-case payment/rollback/permission regression rerun and simultaneous requests. Historical 1.0.1 results are not a 1.0.2 runtime pass.
- NOT TESTED: production deployment, production compatibility and production performance after this hotfix. The user requested immediate JAR delivery before further profiling. Production files were not changed.

The native tests used an isolated localhost server, dedicated offline QA account, Minecraft 1.21.1 / Loader 0.18.4 / Cobblemon 1.8.1 / Chunky 1.4.23 / ChunkyBorder 1.2.18 / BEconomy 1.5 / CobbleDollars Beta 6.1 / LuckPerms, with FAIs Mythical Monstrosities and Cobblemon Parts Refabricated to read the actual terrain blocks. This is a compatible subset, not proof of compatibility with every production mod.

## Install and rollback

Back up the current SVRTP JAR and world/svrtp/journal.json. During your own maintenance window, replace the previous SVRTP JAR with svrtp-1.0.2-fabric-1.21.1.jar; do not retain two SVRTP JARs in mods. Keep config/svrtp.json, economy data and the existing resource pack. Resource remains minigamedim:minigame; no configuration migration is required. SVRTP is server-only. Restart to load the new JAR. If rolling back, stop the server, restore the previous JAR and restart; do not delete player progression, currencies, worlds or the transaction journal. Reconcile any unresolved receipt before changing its contents.

Source QA tasks can use -Dsvrtp.qa.natural=true, -Dsvrtp.qa.terrainMods=/path/to/local/mods and a separate run directory. Native tests require separately prepared local terrain samples and dependencies; production world/region files, third-party JARs and raw JFR recordings are deliberately excluded from this handoff. Never point a QA fixture task at a production world.
