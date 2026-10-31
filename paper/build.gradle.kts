repositories {
    maven("https://jitpack.io") {
        content { includeGroup("com.github.MilkBowl") }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.134-stable")
    // VaultUnlocked も旧 Vault の API で使える
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    implementation(project(":common"))
    // ルールブックのページを JSON に書き出すテスト（tools/preview/rulebook.py の見本）で使う
    testImplementation("io.papermc.paper:paper-api:26.2.build.134-stable")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("rulebook.out", layout.buildDirectory.dir("rulebook").get().asFile.path)
}

tasks.jar {
    archiveBaseName = "LuckyCasino-Paper"
    from(rootProject.files("LICENSE", "THIRD_PARTY_NOTICES.md"))
    from(configurations.runtimeClasspath.map { cp -> cp.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

// Java 版リソースパック（サーバー配信用 zip）
val javaPack by tasks.registering(Zip::class) {
    archiveFileName = "LuckyCasino-java-pack.zip"
    destinationDirectory = layout.buildDirectory.dir("resourcepack")
    from(rootProject.layout.projectDirectory.dir("resourcepacks/java"))
    from(rootProject.files("LICENSE"))
}

tasks.assemble { dependsOn(javaPack) }
