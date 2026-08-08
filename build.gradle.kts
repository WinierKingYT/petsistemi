plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.petsistemi"
version = "0.2.0-alpha.1"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.5")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")

    testImplementation("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    // Headless Bukkit server for integration tests: real command dispatch, listener
    // registration and inventory flows, none of which plain mocks can exercise.
    testImplementation("com.github.seeseemelk:MockBukkit-v1.20:3.87.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// The plain jar would otherwise be `petsistemi-<v>.jar`, which differs from the shaded
// `PetSistemi-<v>.jar` only by case: on Windows they are the same file and overwrite each
// other, on Linux they sit side by side and a release glob can pick the unshaded one
// (no bundled SQLite driver). A classifier keeps the two artifacts unambiguous everywhere.
tasks.jar {
    archiveClassifier.set("thin")
}

tasks.shadowJar {
    archiveBaseName.set("PetSistemi")
    archiveClassifier.set("")
    archiveVersion.set(version.toString())
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.test {
    useJUnitPlatform()
}
