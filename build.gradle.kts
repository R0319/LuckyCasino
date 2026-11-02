// LuckyCasino: common（ゲーム処理と通信の取り決め）・paper（プラグイン）・geyser（統合版の拡張）
subprojects {
    apply(plugin = "java")

    group = "io.github.r0319"
    version = "0.3.4"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.opencollab.dev/main/")
        maven("https://repo.opencollab.dev/maven-snapshots/")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation"))
    }
}
