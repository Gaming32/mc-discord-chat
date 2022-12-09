plugins {
    id("fabric-loom") version "1.0.12"
    id("io.github.juuxel.loom-quiltflower") version "1.8.0"
    `maven-publish`
}

val minecraftVersion = "1.19.2"
val fabricApiVersion = "0.68.0+1.19.2"
val fabricLoaderVersion = "0.14.11"
val fabricKotlinVersion = "1.8.7+kotlin.1.7.22"

project.version = "1.0-SNAPSHOT"
project.group = "io.github.gaming32"

val archivesBaseName = project.name

repositories {
    mavenCentral()
    maven("https://maven.quiltmc.org/repository/release")
    maven("https://maven.quiltmc.org/repository/snapshot")
}

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:$minecraftVersion")
    @Suppress("UnstableApiUsage")
    mappings(loom.layered {
        officialMojangMappings()
        mappings(file("mappings/quilt-mappings-1.19.2+build.local-intermediary-v2.jar"))
//        mappings("org.quiltmc:quilt-mappings:${project.quilt_mappings}:intermediary-v2")
    })
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")

    // Fabric API. This is technically optional, but you probably want it anyway.
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    implementation("net.dv8tion:JDA:5.0.0-beta.1") {
        exclude(module = "opus-java")
    }

    implementation("club.minnced:discord-webhooks:0.8.2")

    implementation("org.quiltmc:quilt-json5:1.0.2")

    // NOTE: Make sure the versions here match those shown in a build scan
    include("club.minnced:discord-webhooks:0.8.2")
    include("com.squareup.okhttp3:okhttp:4.10.0")
    include("com.squareup.okio:okio:3.0.0")
    include("com.squareup.okio:okio-jvm:3.0.0")
    include("org.json:json:20210307")
    include("net.dv8tion:JDA:5.0.0-beta.1")
    include("com.fasterxml.jackson.core:jackson-core:2.13.2")
    include("com.fasterxml.jackson.core:jackson-databind:2.13.2.2")
    include("com.neovisionaries:nv-websocket-client:2.14")
    include("net.sf.trove4j:trove4j:3.0.3")
}

loom {
    accessWidenerPath.set(file("src/main/resources/mc-discord-chat.accessWidener"))
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to project.version))
        }
    }

    withType<JavaCompile> {
        // ensure that the encoding is set to UTF-8, no matter what the system default is
        // this fixes some edge cases with special characters not displaying correctly
        // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
        // If Javadoc is generated, this must be specified in that task too.
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    remapJar {
        archiveBaseName.set(archivesBaseName)
    }

    remapSourcesJar {
        archiveBaseName.set(archivesBaseName)
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${project.name}" }
        }
    }
}

java {
    withSourcesJar()
}
