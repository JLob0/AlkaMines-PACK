import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
    `maven-publish`
}

group = "com.alkacode"
version = "1.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    // mcMMO nao publica no Maven Central/jitpack - so no proprio Nexus deles.
    maven("https://nexus.neetgames.com/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    // depend hard no plugin.yml - o AlkaMinesPack usa o AlkaCore de verdade: AlkaPlugin
    // (classe base + AlkaAPI), MessageProvider (mensagens) e BaseGui (menus).
    compileOnly("com.alkacode:AlkaCore:1.0.3")
    // AlkaEconomy e hard dep, mas o hook fala 100% via reflexao (evita
    // NoClassDefFoundError sem o plugin instalado).
    // ProtocolLib 5.4.0 - entidades/pacotes client-side (mina virtual, dig). compileOnly.
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    // PlaceholderAPI - placeholders %alkamines_*% nas scoreboards/tabs.
    compileOnly("me.clip:placeholderapi:2.11.6")
    // ItemsAdder - itens custom nas skins da picareta e no GUI.
    compileOnly("com.github.LoneDev6:API-ItemsAdder:3.6.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    // sem isso, o Gradle nao percebe que so `version` mudou e reusa o plugin.yml
    // antigo do cache (processResources fica UP-TO-DATE incorretamente).
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {

        expand("version" to project.version)

    }
}
