plugins {
    kotlin("jvm") version "1.9.25"
    application
    id("com.gradleup.shadow") version "8.3.3"
}

group = "com.rmp"
version = "0.1.0"

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.rmp.imitator.MainKt")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-jackson:2.3.12")
    implementation("ch.qos.logback:logback-classic:1.5.8")
}

kotlin {
    jvmToolchain(21)
}
