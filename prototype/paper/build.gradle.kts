dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.134-stable")
    implementation(project(":common"))
}

tasks.jar {
    archiveBaseName = "LuckyCasino-Paper"
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
}

tasks.assemble { dependsOn(javaPack) }
