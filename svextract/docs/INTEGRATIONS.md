> Historical record of the pre-rebuild bundle. Current architecture and requirements are in README.md, docs/REBUILD_TRACKER.md and docs/MAPS.md. Old screenshots and benchmarks do not verify the current modular artifact. The all-mods bundle is retired.

# Inspected and built integrations

Source repository supplied by the user: https://github.com/aristheg201/Cobblemon-MMO . Separate branch checkouts were made outside SVEXTRACT. Working existing projects were not overwritten. No changes were pushed.

| Module | Branch/base revision | Built release |
| --- | --- | --- |
| SVFrameLib + SVFrameMMO | `svframemmo-fabric-1.21.1` / `6baffdb0bda1598b04e104d5d23c18217222ab78` | Lib 1.7.2-fabric / MMO 1.13.2-fabric |
| SVFrameItems | `svframeitems-fabric-1.21.1` / `09ab5eceabe7bef92701227ec1206141fe5c23f7` | 0.1.1-fabric |
| SVFrameMobs | `svframemobs-fabric-1.21.1` / `76a2daa88b48bc13e56ed8d6da9befd01c30dabc` | 0.1.0-fabric |

`tools/patches/svframemobs-mission-spawn.patch` adds public registry discovery and a server-thread checked spawn method that delegates the existing configured-mob factory. This dependency change was committed on its isolated feature branch (`7a55e98`). It preserves the framework's stats, skills and registration rather than cloning it.

`tools/patches/svframemmo-shutdown-save.patch` avoids asking the already closed external-skill writer to save when vanilla disconnects players after the upstream final shutdown flush. It does not suppress a failed live save. This patch was prompted by an actual native server shutdown log and has its own isolated source branch, committed as `d33e32a`, and passed the upstream native smoke test plus 72 skill-bar mapping cases.

For fresh reproduction, install Java 21 and Gradle **8.10.2** for these upstream Loom 1.7 builds. SVEXTRACT itself uses its checked-in Gradle 9.5 wrapper.

```sh
python3 tools/build_private_sources.py --gradle /path/to/gradle-8.10.2/bin/gradle --beconomy /path/to/BEconomy.jar
python3 tools/fetch_personal_dependencies.py --cobblemon
python3 tools/prepare_personal_repository.py
./gradlew build -PpersonalBundle --max-workers=2
```

The source builder refuses a nonempty destination to protect existing checkouts. It builds the actual modules and runs their applicable native smoke checks. Network/proxy configuration may be required for Gradle in your environment. JDK/downloaded build tools are not part of the delivered mod.

| Published dependency | Private build selection |
| --- | --- |
| Vic's Point Blank | Fabric 1.21 build 2.2.0, Modrinth project `og4KPYmA` |
| GeckoLib | Fabric 1.21.1 4.9.3 |
| Fabric Language Kotlin | 1.14.2+kotlin.2.4.21 |
| Cobblemon (optional integration) | Fabric 1.8.2+1.21.1 |
| Placeholder API | 2.4.2+1.21 |
| Fabric Permissions API | 0.3.1 |
| Fabric API | 0.116.17+1.21.1 |
| BEconomy | User-supplied 1.5 JAR; metadata contains literal `${version}` |

The supplied BEconomy JAR lacks declarations for two runtime libraries it imports: Placeholder API and Fabric Permissions API. Both are included in the private build after the initial runtime failure exposed them. Currency IDs are discovered through the live API. Its API has no transaction idempotency key; crash-safe extraction payouts and purchase reconciliation remain unfinished. No fake Beast Coin/Hunter Coin balance is substituted.

SVFrameMMO provides the authoritative registry, progression, resources and abilities. SVEXTRACT calls its existing stat/equipment restoration functions at transitions. Source already includes the prelogin health synchronization fix. Real class skill effects still need complete mission runtime QA.

Point Blank optional mixins target inspected 2.2.0 methods: actual fire sound, reload/fire transaction boundaries and its existing hit damage-source local variable. Actor firearms reuse its crosshair ray resolver, item ammunition components and `hurtEntity`. No duplicate projectile or damage call is added. This is a pinned compatibility adapter, not a promise that arbitrary future upstream versions work unchanged.

The bundle preserves upstream JAR nesting and their namespaces/assets. It is a local private packaging profile requested by the user. Vault Hunters/Mowzie's content and third-party downloaded map assets are absent; no claim of porting those mods has been made.
