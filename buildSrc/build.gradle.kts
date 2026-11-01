// Nocturne protection toolchain (build-time only, never shipped)
plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.8")
    implementation("org.ow2.asm:asm-tree:9.8")
    implementation("org.ow2.asm:asm-commons:9.8")
    implementation("org.ow2.asm:asm-analysis:9.8")
    implementation("org.ow2.asm:asm-util:9.8")
    implementation("com.google.code.gson:gson:2.11.0")
}

java {
    // compile with whatever JDK the daemon runs on; keep the language level conservative
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
