dependencies {
    compileOnly("org.geysermc.geyser:api:2.11.0-SNAPSHOT")
    implementation(project(":common"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

// 統合版リソースパックを .mcpack にして拡張機能の jar に同梱する
val bedrockPack by tasks.registering(Zip::class) {
    archiveFileName = "luckycasino.mcpack"
    destinationDirectory = layout.buildDirectory.dir("generated/pack")
    from(rootProject.layout.projectDirectory.dir("resourcepacks/bedrock"))
}

tasks.processResources {
    from(bedrockPack)
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("extension.yml") { expand(props) }
}

tasks.jar {
    archiveBaseName = "LuckyCasino-Geyser"
    from(rootProject.files("LICENSE", "THIRD_PARTY_NOTICES.md"))
    from(configurations.runtimeClasspath.map { cp -> cp.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
