plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "pl.ynfuien"
description = "Plugin that vanishes players, but this time for real."
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")

    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")

    maven("https://jitpack.io")

    maven("https://repo.essentialsx.net/releases/")

    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    //        Paper
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")

    //        PlaceholderAPI
    compileOnly("me.clip:placeholderapi:2.12.2")

    //        Database connection pool
    implementation("com.zaxxer:HikariCP:7.0.2")

    //        Luck Perms
    compileOnly("net.luckperms:api:5.5")

    //        Dev library
    implementation("com.github.Ynfuien:YDevLib:1.0.6")

    //        BStats
    implementation("org.bstats:bstats-bukkit:3.1.0")

    //        EssentialsX
    compileOnly("net.essentialsx:EssentialsX:2.21.2")

    //        PacketEvents
    compileOnly("com.github.retrooper:packetevents-spigot:2.11.2")
}

tasks {
    processResources {
        // Expand the Maven-style tokens in plugin.yml
        val props = mapOf(
            "project" to mapOf(
                "name" to rootProject.name,
                "version" to version.toString(),
                "description" to project.description.toString(),
                "url" to "https://modrinth.com/project/yvanish"
            )
        )

        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(*props.entries.map { it.key to it.value }.toTypedArray())
        }
    }

    jar {
        // Regular jar is unused, shadowJar produces the final plugin jar
        enabled = false
    }

    shadowJar {
        archiveClassifier = ""

        //        Disable paper remapping
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }

        exclude("module-info.class", "META-INF/*.MF")

        //        HikariCP
        relocate("com.zaxxer.hikari", "pl.ynfuien.yvanish.libs.hikari")
        //        Dev library
        relocate("pl.ynfuien.ydevlib", "pl.ynfuien.yvanish.libs.ydevlib")
        //        BStats
        relocate("org.bstats", "pl.ynfuien.yvanish.libs.bstats")
    }

    build {
        dependsOn(shadowJar)
    }
}
