import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import cn.reverie.obf.ObfuscateTask

plugins {
    kotlin("jvm") version "2.4.10"
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    id("maven-publish")
}

val releaseMetadataSource = file("src/main/kotlin/cn/reverie/client/nocturneClient.kt").readText(Charsets.UTF_8)
fun releaseConstant(name: String): String = Regex(
    """const\s+val\s+${Regex.escape(name)}\s*=\s*[\"]([^\"]*)[\"]"""
).find(releaseMetadataSource)?.groupValues?.get(1)
    ?: throw GradleException("Missing const val $name in nocturneClient.kt")

val nocturneReleaseVersion = releaseConstant("RELEASE_VERSION")
val nocturneReleaseChangelog = releaseConstant("RELEASE_CHANGELOG")
if (!nocturneReleaseVersion.matches(Regex("b\\d+\\.\\d+\\.\\d+"))) {
    throw GradleException("RELEASE_VERSION must use bX.Y.Z format, for example b2.4.0: $nocturneReleaseVersion")
}
if (nocturneReleaseChangelog.isBlank()) {
    throw GradleException("RELEASE_CHANGELOG in nocturneClient.kt must not be blank")
}

version = nocturneReleaseVersion
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

loom {
    accessWidenerPath = file("src/main/resources/viafabricplus.accesswidener")
}

val targetJavaVersion = 25
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)

    withSourcesJar()
}

fabricApi {
    configureDataGeneration {
        client = true
    }
}

repositories {
    mavenCentral()
    maven("https://repo.viaversion.com")
    maven("https://maven.terraformersmc.com/releases")
}

val lwjglVersion = "3.4.1"
val lwjglNatives = listOf("natives-windows", "natives-windows-arm64", "natives-linux", "natives-linux-arm64", "natives-macos", "natives-macos-arm64")

dependencies {

    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")
    testImplementation(kotlin("test-junit"))

    // Interpreted, cancellable JavaScript runtime. Bundled for launcher installations.
    implementation("org.mozilla:rhino:1.9.1")
    include("org.mozilla:rhino:1.9.1")

    implementation("org.lwjgl:lwjgl-nanovg:$lwjglVersion")
    include("org.lwjgl:lwjgl-nanovg:$lwjglVersion")
    for (native in lwjglNatives) {
        runtimeOnly("org.lwjgl:lwjgl-nanovg:$lwjglVersion:$native")
        include("org.lwjgl:lwjgl-nanovg:$lwjglVersion:$native")
    }

    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")

    // CloudMusic dependencies: audio decoding & QR generation
    implementation("javazoom:jlayer:1.0.1")
    include("javazoom:jlayer:1.0.1")
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    include("com.googlecode.soundlibs:mp3spi:1.9.5.4")
    implementation("com.googlecode.soundlibs:tritonus-share:0.3.7.4")
    include("com.googlecode.soundlibs:tritonus-share:0.3.7.4")
    implementation("org.jflac:jflac-codec:1.5.2")
    include("org.jflac:jflac-codec:1.5.2")
    implementation("com.google.zxing:core:3.5.3")
    include("com.google.zxing:core:3.5.3")

    val fabricApiModules = listOf(
        "fabric-api-base",
        "fabric-resource-loader-v1",
        "fabric-resource-loader-v0",
        "fabric-networking-api-v1",
        "fabric-command-api-v2",
        "fabric-lifecycle-events-v1",
        "fabric-particles-v1",
        "fabric-registry-sync-v0"
    )
    for (module in fabricApiModules) {
        include(fabricApi.module(module, project.property("fabric_api_version") as String))
    }

    val viaLibraries = listOf(
        "com.viaversion:viaversion-common:${project.property("viaversion_version")}",
        "com.viaversion:viabackwards-common:${project.property("viabackwards_version")}",
        "com.viaversion:viaaprilfools-common:${project.property("viaaprilfools_version")}",
        "net.raphimc:ViaLegacy:${project.property("vialegacy_version")}",
        "de.florianreuth:classic4j:${project.property("classic4j_version")}",
        "net.lenni0451:Reflect:${project.property("reflect_version")}",
        "net.lenni0451.commons:unchecked:${project.property("unchecked_version")}",
        "com.seedfinding:mc_biome:1.171.1",
        "com.seedfinding:mc_noise:1.171.1",
        "com.seedfinding:mc_seed:1.171.2",
        "com.seedfinding:mc_math:1.171.0",
        "com.seedfinding:mc_core:1.210.0"
    )
    for (library in viaLibraries) {
        implementation(library)
        include(library)
    }

    compileOnly("com.terraformersmc:modmenu:${project.property("modmenu_version")}")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    inputs.property("kotlin_loader_version", project.property("kotlin_loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to (project.property("minecraft_version") as String),
            "loader_version" to (project.property("loader_version") as String),
            "kotlin_loader_version" to (project.property("kotlin_loader_version") as String)
        )
    }
}

tasks.withType<JavaCompile>().configureEach {

    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

// Seeded checks for the procedural Human Telly controller, also discovered by `test`.
tasks.register<Test>("humanTellyCheck") {
    group = "verification"
    description = "Checks Human Telly motion, input remapping and click timing without launching Minecraft"
    dependsOn(tasks.named("testClasses"))
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter { includeTestsMatching("cn.reverie.module.world.HumanTellyChecks") }
    testLogging.showStandardStreams = true
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(targetJavaVersion)) })
}

tasks.register("architectureCheck") {
    group = "verification"
    description = "Checks first-party dependency boundaries for mixins and utility code."
    val mixinSources = fileTree("src/main/java/cn/reverie/mixin") { include("**/*.java") }
    val utilitySources = fileTree("src/main/kotlin/cn/reverie/util") { include("**/*.kt") }
    inputs.files(mixinSources, utilitySources)
    doLast {
        val allowedMixinPrefixes = listOf(
            "cn.reverie.manager.",
            "cn.reverie.manager.event.player.MoveInputEvent",
            "cn.reverie.manager.event.packet.PacketSide",
            "cn.reverie.render.ui.NvgScreen",
            "cn.reverie.render.ChatComponentAccess",
            "cn.reverie.render.ChatScreenAccess",
            "cn.reverie.render.EditBoxAccess",
            "cn.reverie.mixin.",
            // Known legacy exceptions: read-only render helpers that predate the
            // boundary rule and are still referenced from mixins.
            "cn.reverie.module.render.NameProtect",
            "cn.reverie.module.render.MotionCamera",
            "cn.reverie.render.core.ResolutionHelper"
        )
        val forbiddenMixinPrefixes = listOf(
            "cn.reverie.module.", "cn.reverie.util.", "cn.reverie.app.config.",
            "cn.reverie.render.core.", "cn.reverie.render.hud.", "cn.reverie.render.video.",
            "cn.reverie.gui.", "cn.reverie.nocturne.", "cn.reverie.app.account.", "cn.reverie.app.command.",
            "cn.reverie.app.music."
        )
        val violations = mutableListOf<String>()
        mixinSources.forEach { source ->
            source.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                val imported = Regex("^import\\s+(cn\\.reverie\\.[A-Za-z0-9_.$]+)").find(trimmed)?.groupValues?.get(1)
                val qualified = Regex("(?<![A-Za-z0-9_])cn\\.reverie\\.(?:module|util|app\\.(?:config|account|command|music)|render\\.(?:core|hud|video)|gui|nocturne)\\.[A-Za-z0-9_.$]+")
                    .find(trimmed)?.value
                val candidate = imported ?: qualified ?: return@forEachIndexed
                if (forbiddenMixinPrefixes.any(candidate::startsWith) && allowedMixinPrefixes.none(candidate::startsWith)) {
                    violations += "${source.relativeTo(projectDir)}:${index + 1}: $candidate"
                }
            }
        }
        // Utility code must not pull in feature modules; these two are the
        // documented exceptions carried over from before the rule existed.
        val legacyUtilityImports = setOf(
            "cn.reverie.module.render.NameProtect",
            "cn.reverie.module.render.MotionCamera"
        )
        utilitySources.forEach { source ->
            source.readLines().forEachIndexed { index, line ->
                val candidate = Regex("^\\s*import\\s+cn\\.reverie\\.(?:module|app\\.music)\\.[A-Za-z0-9_.$]+")
                    .find(line)?.value?.substringAfter("import ")?.trim() ?: return@forEachIndexed
                if (candidate in legacyUtilityImports) return@forEachIndexed
                violations += "${source.relativeTo(projectDir)}:${index + 1}: utility imports feature module $candidate"
            }
        }
        check(violations.isEmpty()) {
            "Dependency direction violation(s):\\n" + violations.joinToString("\\n")
        }
        logger.lifecycle("Architecture boundaries passed: mixins route through managers; utility code does not import feature modules.")
    }
}

tasks.named("check") {
    dependsOn("architectureCheck")
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }

    from("LICENSE-ViaFabricPlus.txt")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    repositories {

    }
}

// ---------------- Nocturne protection pipeline ----------------
// buildNative   : compiles the native core + manual-mapping loader (MSVC x64)
// obfuscateJar  : runs the full Java protection pipeline on remapJar output,
//                 bundles the encrypted native payloads, verifies everything
// protectedBuild: full chain, produces build/libs/nocturne-*-protected.jar
//
// The secret surface is all-in-native: the obfuscation seed is baked into the
// core image and every string-pool / class-pack key is derived from it inside
// the DLL, so nothing in the jar can decrypt pool ciphertext or packed class
// bodies on its own. The client app id / app secret are baked the same way and
// no longer exist in Java.

val cmakeExe = "C:/Program Files/CMake/bin/cmake"
val nativeBuildDir = layout.buildDirectory.dir("native")
val jdkHome = providers.gradleProperty("org.gradle.java.home")
    .orElse(providers.systemProperty("java.home")).get()

/** obf.json is a JSON file we only need a few scalar values from. */
fun obfText(): String = file("obf.json").readText()

fun obfLong(key: String, fallback: Long): Long =
    Regex("\"" + key + "\"\\s*:\\s*(-?\\d+)").find(obfText())
        ?.groupValues?.get(1)?.toLong() ?: fallback

fun obfString(key: String, fallback: String): String =
    Regex("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").find(obfText())
        ?.groupValues?.get(1) ?: fallback

val vsWhereExe = file(
    "${System.getenv("ProgramFiles(x86)") ?: "C:/Program Files (x86)"}/Microsoft Visual Studio/Installer/vswhere.exe"
)

fun vswhereValue(property: String): String {
    if (!vsWhereExe.isFile) return ""
    return try {
        val process = ProcessBuilder(vsWhereExe.absolutePath, "-latest", "-products", "*", "-property", property)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""
    } catch (ignored: Exception) {
        ""
    }
}

val vsInstanceSpec = run {
    val path = vswhereValue("installationPath").replace('\\', '/')
    val version = vswhereValue("installationVersion")
    if (path.isNotEmpty() && version.isNotEmpty()) "$path,version=$version" else ""
}

val nativeCoreDll = nativeBuildDir.map { it.file("Release/nocturne_core.dll") }
val nativeLoaderDll = nativeBuildDir.map { it.file("Release/nc_loader.dll") }
val nativeSecretHeader = nativeBuildDir.map { it.file("nc_secret_config.h") }
val localAppSecretFile = layout.projectDirectory.file(".secrets/app_secret.txt")

val nativeConfigure by tasks.registering(Exec::class) {
    doFirst {
        file(nativeBuildDir).mkdirs()
        val environmentSecret = providers.environmentVariable("NOCTURNE_APP_SECRET").orNull?.trim()
        val localSecret = localAppSecretFile.asFile.takeIf { it.isFile }
            ?.readText(Charsets.UTF_8)?.trim()
        val appSecret = environmentSecret?.takeIf { it.isNotEmpty() }
            ?: localSecret?.takeIf { it.isNotEmpty() }
            ?: throw GradleException(
                "Set NOCTURNE_APP_SECRET or provide the ignored .secrets/app_secret.txt file"
            )
        if (!appSecret.matches(Regex("[A-Za-z0-9_-]+"))) {
            throw GradleException("NOCTURNE_APP_SECRET has an unsupported format")
        }
        environment("NOCTURNE_APP_SECRET", appSecret)
    }
    // Keep the secret out of command-line arguments and CMakeCache.txt.
    val seed = obfLong("seed", 0L)
    val appId = obfString("appId", "nc-1")
    val cmakeArgs = mutableListOf(
        cmakeExe, "-S", file("native").toString(), "-B", nativeBuildDir.get().asFile.toString(),
        "-U", "NC_APP_SECRET", "-A", "x64", "-DJDK_INCLUDE_DIR=$jdkHome/include",
        "-DNC_BUILD_SEED=$seed", "-DNC_APP_ID=$appId"
    )
    if (vsInstanceSpec.isNotEmpty()) {
        cmakeArgs += "-DCMAKE_GENERATOR_INSTANCE=$vsInstanceSpec"
    }
    commandLine(cmakeArgs)
    inputs.file("obf.json")
    inputs.file("native/CMakeLists.txt")
    outputs.file(nativeBuildDir.map { it.file("CMakeCache.txt") })
    outputs.file(nativeSecretHeader)
    outputs.upToDateWhen { false }
}

val buildNative by tasks.registering(Exec::class) {
    dependsOn(nativeConfigure)
    inputs.dir("native")
    outputs.file(nativeCoreDll)
    outputs.file(nativeLoaderDll)
    outputs.upToDateWhen { false }
    commandLine(cmakeExe, "--build", nativeBuildDir.get().asFile, "--config", "Release")
}

val protectedJarPath = layout.buildDirectory
    .file("libs/${project.property("archives_base_name")}-$nocturneReleaseVersion-protected.jar")

val obfuscateJar by tasks.registering(ObfuscateTask::class) {
    dependsOn(tasks.named("jar"), buildNative)
    outJar.set(protectedJarPath)
    libraries.from(configurations.getByName("runtimeClasspath"))
    config.set(layout.projectDirectory.file("obf.json"))
    mcVersion.set(project.property("minecraft_version") as String)
    nativeCore.set(nativeCoreDll)
    nativeLoader.set(nativeLoaderDll)
}

tasks.register("protectedBuild") {
    group = "nocturne"
    description = "Full protected release: remap -> obfuscate -> native bundle -> verify"
    dependsOn(obfuscateJar)
}

afterEvaluate {
    obfuscateJar {
        // loom 1.17 emits the final mojmap-named mod jar directly from `jar`
        inJar.set(tasks.named<org.gradle.jvm.tasks.Jar>("jar").flatMap { it.archiveFile })
    }
}
