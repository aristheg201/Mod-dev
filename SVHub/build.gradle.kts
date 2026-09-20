@file:Suppress("UnstableApiUsage")

plugins {
    java
    idea
    id("fabric-loom") version "1.10.5"
    kotlin("jvm") version "2.2.20"
}

val modId = providers.gradleProperty("mod_id").get()
val minecraftVersion = providers.gradleProperty("minecraft_version").get()
version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()
base.archivesName.set(providers.gradleProperty("mod_name").get())

// Keep client-only Minecraft classes compile-isolated, but explicitly declare both
// source sets as one Fabric mod. Loom then packages common + client output into the
// same deployable JAR instead of compiling client classes and dropping them.
loom {
    splitEnvironmentSourceSets()
    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.named("client").get())
        }
    }
}

repositories {
    mavenCentral()
    maven("https://api.modrinth.com/maven")
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://repo.lucko.me")
    maven("https://maven.pokeskies.com/releases/")
    maven("https://maven.nucleoid.xyz/") { name = "Nucleoid" }
    maven("https://maven.wispforest.io/releases/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_version").get()}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")
    modImplementation("com.cobblemon:fabric:${providers.gradleProperty("cobblemon_version").get()}")
    // Equipment assets are resolved through the live item registry; these mods are
    // external runtime dependencies, never bundled into the SVHub JAR.
    modRuntimeOnly("maven.modrinth:cobblemon-mega-showdown:TKdAixuR")
    modRuntimeOnly("maven.modrinth:accessories:Xlt4eWBe") // Fabric 1.1.0-beta.53
    // Use Wisp Forest's Maven here instead of Modrinth Maven. The creator POM
    // carries owo's required standalone Endec runtime dependencies; Modrinth's
    // Maven artifact does not expose those transitively and runClient crashes
    // before Minecraft initializes with NoClassDefFoundError: MapCarrier.
    modRuntimeOnly("io.wispforest:owo-lib:0.12.15.4+1.21")
    modRuntimeOnly("maven.modrinth:architectury-api:Pzc2FP5K") // Fabric 13.0.11
    modCompileOnly("me.lucko:fabric-permissions-api:0.3.1")

    // Fabric-native Placeholder API. JIJ it so SVHub formatting works without
    // forcing a separate dependency download on clients or servers.
    modImplementation(include("eu.pb4:placeholder-api:${providers.gradleProperty("placeholder_api_version").get()}")!!)

    // Deliberately do NOT JIJ any net.kyori Adventure/Examination runtime.
    // Multiple server mods commonly provide different Adventure platform versions;
    // bundling another global net.kyori.* copy here can poison the shared classpath
    // and break unrelated boss bars (for example NovaRaids). SVHub's rich client
    // text is parsed directly into vanilla Minecraft Components instead.

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("fabric.mod.json") {
        expand("version" to version)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.remapJar {
    archiveFileName.set("SVHub-fabric-${minecraftVersion}-${version}.jar")
}

// Re-run the startup/data contract tests with main classes and resources coming
// from the remapped production JAR, never src/main/resources or build/classes.
tasks.register<Test>("verifyPackagedTft") {
    dependsOn(tasks.remapJar, tasks.testClasses)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = files(tasks.remapJar.flatMap { it.archiveFile }) +
        sourceSets.test.get().output + configurations.testRuntimeClasspath.get()
    useJUnitPlatform()
    filter { includeTestsMatching("io.github.aristheg201.svhub.native.game.tft.TftSetRegistryTest") }
    systemProperty("svhub.test.packaged", "true")
}
