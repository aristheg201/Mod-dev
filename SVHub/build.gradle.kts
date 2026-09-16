@file:Suppress("UnstableApiUsage")

plugins {
    java
    idea
    id("quiet-fabric-loom") version "1.13-SNAPSHOT"
    kotlin("jvm") version "2.2.20"
}

val modId = providers.gradleProperty("mod_id").get()
val minecraftVersion = providers.gradleProperty("minecraft_version").get()
version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()
base.archivesName.set(providers.gradleProperty("mod_name").get())

repositories {
    mavenCentral()
    maven("https://api.modrinth.com/maven")
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://repo.lucko.me")
    maven("https://maven.pokeskies.com/releases/")
    maven("https://maven.nucleoid.xyz/") { name = "Nucleoid" }
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
    modCompileOnly("me.lucko:fabric-permissions-api:0.3.1")

    // Fabric-native Placeholder API. JIJ it so SVHub formatting works without
    // forcing a separate dependency download on clients or servers.
    modImplementation(include("eu.pb4:placeholder-api:${providers.gradleProperty("placeholder_api_version").get()}")!!)

    // MiniMessage is used only for safe presentation formatting. SVHub deliberately
    // exposes color/decoration/gradient/rainbow/reset/newline tags, not MiniMessage
    // click/run-command tags; gameplay actions still travel through authoritative IDs.
    val adventureVersion = providers.gradleProperty("adventure_version").get()
    val examinationVersion = providers.gradleProperty("examination_version").get()
    implementation(include("net.kyori:adventure-text-minimessage:$adventureVersion")!!)
    implementation(include("net.kyori:adventure-api:$adventureVersion")!!)
    implementation(include("net.kyori:adventure-key:$adventureVersion")!!)
    implementation(include("net.kyori:examination-api:$examinationVersion")!!)
    implementation(include("net.kyori:examination-string:$examinationVersion")!!)

    testImplementation(kotlin("test"))
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
