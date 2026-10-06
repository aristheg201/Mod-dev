plugins {
    java
    id("dev.architectury.loom") version "1.7-SNAPSHOT"
    id("architectury-plugin") version "3.4-SNAPSHOT"
}

group = property("maven_group")!!
version = property("mod_version")!!

base {
    archivesName.set(property("archives_base_name").toString())
}

architectury {
    platformSetupLoomIde()
    fabric()
}

loom {
    silentMojangMappingsLicense()

}

repositories {
    mavenCentral()
    maven("https://maven.cobblemon.com/releases")
    maven("https://maven.wispforest.io/releases")
    maven("https://maven.impactdev.net/repository/development/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    minecraft("net.minecraft:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modImplementation("com.cobblemon:fabric:${property("cobblemon_version")}")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}


val generatedRuntimePngs = mapOf(
    "assets/cobblemonworld/textures/entity/npc/mara_voss.png" to "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAABL0lEQVR42u2aPQ8BMRiAe3Iz1ttMBrvfYJMYbOYTViIWEbFhsrGJP+F3+A2SG09yk0GYFa1qExd9num+2lyevO/bu7aB0HDcTW+q+0mSKNs3xttA5JiC8BwEIAABCECAzwS6cX442Sg7GPSbVi/w6+8EUsB3AeGnD67mXaPU+LsIaK0OL4//vgjq0P0MUQQpgjlPAflCtVa/qUI8S0/KkB1ValbzB/tLatS/3J+uPRGAAAQ81gA5501ztlcqvr0XL8pCCCG2o/PXw2YURVbDsK4mkAIIQAACEIAABPjL04zQ9ZLl+oU7s/bD+TJeEwEIAAAAAAAAAAAwwPn2FNf7C2zX/3UwJYYAaoDbnJex2V/wSXvbmkAKIAABCEAAAhDgL6HrDm33F7he/ycCEKDmDvU7Y8btIIr0AAAAAElFTkSuQmCC",
    "assets/cobblemonworld/textures/entity/npc/dr_orin.png" to "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAABNElEQVR42u2asQ4BMRiAe3KJZ7hXEAuWG1hFxMpg9gYWs5nZYjHbJCw2sRJ2A4tbJGbTmRWt0yYu+n3T5XptLl/+/2+vV09o2Ez6sao9iiJl/0Zv5IkUkxGOgwAEIAABCHAZTzfPDxdb5QDtQmD0Ar9eJ5ACrgvwP32wWy8mSo2/i4DWYP7y+u+LoA7dxxBFkCKY8hSQb+QqtVgV4tfDXhmyp/PJaP8gLIWJxpfH0/UnAhCAgMcaIOd80pzdrWZv2zrTtRBCiHGz/PW0GQSB0TSsqwmkAAIQgAAEIAAB7vK0I3Q7H6WlUjZVL3xZTh5v5KtEAAIAAAAAAAAAABJg/XiK7fMFpv//dbAlhgBqgN2clzE5X/BJf9OaQAogAAEIQAACEOAuvu0BTc8X2P7/TwQgQM0dZkNjkMckXmYAAAAASUVORK5CYII=",
    "assets/cobblemonworld/textures/entity/npc/rook.png" to "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAABSElEQVR42u2aIY7CQBRAf5cG0tRimmDYjIUbIDFYJAlmT4ABQbJis2gMJ0BxAiSGa+BrsA0GwupOYKaTlmzDvKegdH7ax/+dmc4EYmG3mNxNv6dpamw/2x4CqTEf4jkIQAACEIAAnwls/fxsezQGmA9VqQv473ECJeC7gLDoievpwKk03i4DlvvTw89vnwH6Py8ishopEVGFJkM8AxBQ83GAfqDT+byb5vu328XYbyvVK/X+IMvOTvH1eLb2ZAACEJDvBvWat9FoRLnzu93nc4HvMBIRkZ/rpXD8OG7n4idJ4nRDenvbM4ESQAACEIAABCDA44GQfqDVatb6gn/H/dz3r01KBiAAAAAAAAAAAMCByvfoVb2/oOz6vw1eiSHAc8Kqa96Gy/6CR7iu/5MBCEAAAhCAAAQg4GUDIZ2y+wuqXv8nAxBg5g+k7FV1Ys02pgAAAABJRU5ErkJggg==",
    "assets/cobblemonworld/textures/entity/npc/selene_kade.png" to "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAABMUlEQVR42u2asQ7BQBiAW/oSXcTkBQxCTCxeQCRisBjFWlajhcXMIhGr2eQ9iKWzmAxSg6nFXesu0fS+b2p7vUvz5f//a69nWxLO+2Ugavd9X9i/OpjaVorJWYaDAAQgAAEIMBlbNs93RjvhAHOvrvQA/35PIAVMF+DEvXG7aCdKjcxFQHO8/nic+SIoQ/YxRBGkCKY8BaIXSsVKIArx2/0iDNl2zVNaPzie1onGj44n608EIAAB4RoQzfmkOdsqd7+29RuvttVh8/O06bqu0jQsqwmkAAIQgAAEIAAB5vK2IvTIX1P9wLNJIXTeG/pEAAIAAAAAAAAAABKgfXuK7v0Fqv//ZbAkhgBqgN6cj6KyvyBOf9WaQAogAAEIQAACEGAuju4BVfcX6P7/TwQgQMwTiQdhohzyUUwAAAAASUVORK5CYII=",
    "assets/cobblemonworld/textures/entity/npc/aurelia.png" to "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAABMklEQVR42u2aIQ7CMBRA/8gMnIAlODQYxBR2CA6B4QYcgTtgUNwAg2AChSUINAhCNgEegQC9AS2jTVjoe2pp12Z5+f936+qJhiSe3FX9aZoqx3cGY09KTEUcBwEIQAACEOAynm6dl2qs7E4PLaMH+PV7AingugD/4zuvUaHU+LsI6I5mL6//vwhq0H0MUQQpgiVPgXxDGA3vqhA/7hbKkD2fE6P9g3a7U2j+/Hy68UQAAhCQrQH5nC+as5vV9G3fadETEZFGf/n1shkEgdEyrKsJpAACEIAABCAAAe7ytCN02a+zDbVmqR74tp1nG+ohEYAAAAAAAAAAAIACWD+eYvt8gen/fx1siSGAGmA35/OYnC/4ZLxpTSAFEIAABCAAAQhwF9/2hKbnC2z//ycCEKDmAaV6YgjVTXWsAAAAAElFTkSuQmCC",
    "assets/cobblemonworld/textures/entity/npc/sixth_warden.png" to "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAABQ0lEQVR42u2aLU/DQBiA35L+iRoczQwK25AgJpaRLOlPAIMgSzBk/ATUEIiZie0HoJZMTs4RPA5zBkMmG1J0D7j2uFtoes+j+nWX5un7vtdeL5Ia1g+T0nReKWVsf3G/iKTFHEjgIAABCEAAAkImqhvnb2YrYwe3oxOnG/jv9wRSIHQBcdMLp1fnVqnRuQi4nm9+3O58BOhPXkTk8fKs8ccQNQABLX8P0A+kvePS9L2/+3g3jdtwZVFo/2Kw+SrVfbM9WnwhAAALyfUAx58vmbHx9++e982QoIiJf6eLgYVMpJcfsU0gBBCAAAQhAAAL85dd6QBT+1PqB04dW7vzuRRMBCAAAAAAAAAAAKIHzPXqu9xdU/f9vgyUxBHhO6DrnrZye5cqb9hfsIrroZi77BFIAAQhAAAIQgAAmQs6our/A9f9/IgABZrYIz1nxQqyvVQAAAABJRU5ErkJggg==",
    "assets/cobblemonworld/textures/entity/npc/seventh_warden.png" to "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAABRUlEQVR42u2au07DMBRAb1DEkJnFAyA1YuzA1I0OReIDkKoOrciK1C9gZUGdqzI2Ev0NBuiflMFL5w4wpHNCsZvGFVF8zpSHbUUn99qO40AsLGdPmem+1tpYv/88D6TGnIjnIAABCEAAAnwmsI3zo+mnsYHJ/VWlB/jveQIp4LuAcN+Cb+ObUqnRuAhIXt93Hjc+AopvXkQkfezt/TFEH4CAms8DihfUZTszfu9/r43jdtwZVFo/2Kw+SrVfbM9WnwhAAALyfUAx58vmbHx9++e982QoIiJf6eLgYVMpJcfsU0gBBCAAAQhAAAL85dd6QBT+1PqB04dW7vzuRRMBCAAAAAAAAAAAKIHzPXqu9xdU/f9vgyUxBHhO6DrnrZye5cqb9hfsIrroZi77BFIAAQhAAAIQgAAmQs6our/A9f9/IgABZrYIz1nxQqyvVQAAAABJRU5ErkJggg==",
    "assets/cobblemonworld/textures/entity/npc/resonance_heart.png" to "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAABSklEQVR42u2aq44CMRRA7xBCsoSwhoSMWoniD9AYDGb5AgwgENgliF2LwIDBYDAYPoJ/mZBgICgM6Ba2nWaGMKHnqHm1aQ73Tu/QBmJhPh5dTfejKDK2/12uA8kwOfEcBCAAAQhAgM8Etnn+Z7kxdjBoNRIN4NV1Aingu4B83Af/ut9OqfF2ETDd7h4ev30E6L+8iMio3Yj9McQ7AAEZrwP0C5/hl1IXHO9C/GKct6u1urGu2NtS5nRw6v+uP0t7IgABCFCnQT3n7RSuak7W/n2ytJiJiMi5N4zffbmi9h+GbsPT2tveCaQAAhCAAAQgAAEeF0L6hUrxQzk/ZmzAk05TOe/PVkQAAgAAAAAAAAAAHEh9j17a+wuSrv/b4C8xBHhOPu2ctxN/f8FDHNf/iQAEIAABCEAAAhDwtEJIJ+n+grTX/4kABJi5AXafTcRqs9WdAAAAAElFTkSuQmCC",
    "assets/cobblemonworld/textures/entity/boss/toba.png" to "iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAYAAADDPmHLAAAD80lEQVR42u2dv27UQBCH585OEH8ihUNJY2goQEoBT0Cbgg4klI6CB6OgQ0jQUdDyBNBSUACKlFNOnO4k0MWXUAVOwt61vV7vrP19khvbe2fv/GZmx17bo8mDgwvRzmIu4/09tYe3Op6WbksTc9t8Zd5ubW/Znlp+fywwaBAAAgAEAAgAEAAgABgaadUdn356V7j+7cMnXtuKiOSSyLbqTkwMx+5mAN/tiQCkAEAAgABgmIxiuBmUL5ayvT9Re3znx7PyY3e8WWNt73iziQhACgCuA6g/yER3ijIcX+g6PyUCAAIABAAIAGIdBIqInJ/MG9W5vidV5gkRABAAkAK81tmraOts5gOA8ghwOncbJAV+8kX2dr2GuBu3wz+RtPz8hTEAIABAAIAAAAFAi1XA2fUrTj9wZtthy7E9eH3uIIoIkB0dooKuU4Ct07Ojw072udyGCDoUgMZO//H6A9bqSgBVOvtyHxeRXLYt+z+8XkEVEMoIm/+L9wcQwGanh/REjB8wAthE0EYaIPQrTwG+PNCW//H+zVo/KV1Sy5JblrGrZ7YdBfB+ZRGgy/EAAz+lAmjbILbwj/EVCsAUptu8JgCKBeArFRD6I4oArgYyhX+MH0kKMHlu3chA6I9QAFVTQR1R4P3lpLIqXXLLklqWxhGgSAR1owDeH3kKqOu1m0Jh4KclurQ4km9iSO3G//19SgSokwqqpAFCf48EUNWLTfcRIHIBNPFujN8zARQZlFA/sAhQ1avx/nqomQ8ARABn78b7ey4Ak5Ex/kAEgLERAIJAAP+MjvH1Mtq5d/ci5hPYuna1/2XgbFG6zfklXWm6FfTkzie7Tu3XBesuvp5U7yDln3RJE94PAH0YBIJO1L8q9tnHV/+te/PoOZYbQgQoMr5pPfRIADYjI4KWysCbB/eDloFFVUAd4xalg80qQLKd8Hl2unCqAnxWMePYPJ9IMLBBYLOTWhXWyXdePBYRkW8v35e29bGP5u8dUAYyCAQEAAhAC3Uv8nBRqIcRoKpRMX6PU4DNuBh/AGOAMiNj/AFdB2hi7FwSZZ3s734+8wGAMhAQACAAiHIQOJ79lPXyV/kBOk6qBCKAiIS7E4gAgBSguU7uAtN1CeYDACkAEAAgAEAAgAAAAcDQrgP4nDevo5NXwep85gMAAgAEAAgACscI6/nSvEPglywBEQAQAHhLAallDn3oOpX5AH7PnwhACgAEAAgAEAAgAEAAMKzrALZn6anzW+hk3g8QFh4LIwUAAgAEAAgAEAAgAPhbJprmrMdQ58cxH4DvBQApABAAIABAAIAAQAujSZYZPxzZxefRnf7f9uRRdit8L09Pmx+/5/7/A3sRuW2jybH+AAAAAElFTkSuQmCC"
)

tasks.processResources {
    doLast {
        generatedRuntimePngs.forEach { (relativePath, encoded) ->
            val target = destinationDir.resolve(relativePath)
            target.parentFile.mkdirs()
            target.writeBytes(java.util.Base64.getDecoder().decode(encoded))
        }
    }
}
