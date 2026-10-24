// Paper（Java 25）と Velocity 上の Geyser（Java 21 以上）の両方に入れるので 21 向けに出力する
tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}
