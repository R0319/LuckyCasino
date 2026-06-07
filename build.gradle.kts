plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Vault: ExcellentEconomy を含む全 Vault 互換経済プラグインへの橋渡し API
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    // ExcellentEconomy: Vault 非導入時の直接 API フォールバック用
    // リフレクション経由で呼び出すため compileOnly 不要だが、
    // IDE 補完を活かしたい場合は以下のコメントアウトを外して
    // JitPack 経由で追加可能（バージョンはサーバーに合わせること）:
    // compileOnly("com.github.nulli0n:ExcellentEconomy-spigot:VERSION")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-Xlint:deprecation"))
    }

    runServer {
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
