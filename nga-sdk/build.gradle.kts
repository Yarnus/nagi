plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("org.jsoup:jsoup:1.18.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
tasks.register<JavaExec>("smoke") {
    group = "verification"
    description = "Opt-in real NGA read-only probe. No credentials or mutations."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.nagi.sdk.ReadSmokeKt")
    dependsOn(tasks.testClasses)
}
