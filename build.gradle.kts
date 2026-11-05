import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.fabric.loom)
    jacoco
}

// Registers `generateModulesDoc`, which writes docs/MODULES.md from the module sources.
apply(from = "scripts/generateModulesDoc.gradle.kts")

val archivesBaseName = providers.gradleProperty("archives_base_name").get()
val mavenGroup = providers.gradleProperty("maven_group").get()

base {
    archivesName = archivesBaseName
    version = libs.versions.mod.version.get()
    group = mavenGroup
}

repositories {
    mavenCentral()
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }

    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.meteor.client)

    // Runtime classpath scanning for module auto-registration. `include` bundles the library
    // (and its transitive dependencies) into the mod jar so users do not need to install it.
    implementation(libs.reflections)
    include(libs.reflections)
    // Reflections needs Javassist to read class files; Loom's include is not transitive.
    include(libs.javassist)

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation(libs.jqwik)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(
            JavaLanguageVersion.of(
                libs.versions.jdk.get().toInt()
            )
        )
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    named<JacocoReport>("jacocoTestReport") {
        dependsOn(test)
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "minecraft_version" to libs.versions.minecraft.get(),
            "jdk_version" to libs.versions.jdk.get()
        )

        inputs.properties(propertyMap)

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        // GPL-3.0 section 4 requires the licence to travel with the binary, so ship it in the jar.
        from(rootProject.file("LICENSE")) {
            rename { "LICENSE_venomous" }
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(libs.versions.jdk.get().toInt())
        // TODO(A3 follow-up): enable -Werror once the existing module warnings are fixed.
        // `-Werror` currently fails on ~100 pre-existing warnings in module sources (mostly
        // redundant casts), which are owned by Track B and must not be edited from here.
        options.compilerArgs.add("-Xlint:all")
    }
}
