import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.jetbrains.intellij.platform.gradle.tasks.PublishPluginTask
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.16.0"
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
}

group = "com.alexey-anufriev"
version = "1.15.0"

val ide = (findProperty("ide") ?: "IC").toString()
val platformVersion = "2025.1"
val builtPluginDir = findProperty("builtPluginDir")?.toString()
val builtPluginArchive = findProperty("builtPluginArchive")?.toString()

val integrationTest = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets["main"].output
    runtimeClasspath += sourceSets["main"].output
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(ide, platformVersion)
        testFramework(TestFrameworkType.Starter)
        pluginVerifier()
        zipSigner()
    }

    add(integrationTest.implementationConfigurationName, sourceSets["main"].output)
    add(integrationTest.implementationConfigurationName, "org.junit.jupiter:junit-jupiter:6.0.3")
    add(integrationTest.implementationConfigurationName, "org.kodein.di:kodein-di-jvm:7.32.0")
    add(integrationTest.implementationConfigurationName, "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
    // Required to deserialize AssertJ Swing exceptions returned by the remote Driver on CI.
    add(integrationTest.runtimeOnlyConfigurationName, "org.assertj:assertj-swing:3.17.1")

    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")

    // Required by the IntelliJ JUnit 5 test framework until JetBrains fixes IJPL-159134.
    // Remove once plugin tests pass without org/junit/rules/TestRule on the runtime classpath.
    testRuntimeOnly("junit:junit:4.13.2")
}

configurations[integrationTest.implementationConfigurationName].extendsFrom(
    configurations["testImplementation"],
    configurations["intellijPlatformTestDependencies"],
)
configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations["testRuntimeOnly"])

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        failureLevel = listOf(VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS)

        ides {
            recommended()
        }
    }

    publishing {
        token = System.getenv("PUBLISH_TOKEN")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

data class UiIntegrationTestConfig(
    val displayName: String,
    val productCode: String,
    val ideVersion: String? = null,
    val ideChannel: String = "release",
    val toolWindowId: String = "Project",
    val testProjectPath: String = "src/integrationTest/resources/test-projects/idea-project",
    val sampleFileNames: String = "App,App.java",
    val samplePath: String = "src/main/java/sample/App",
)

val prepareSandbox = tasks.named<PrepareSandboxTask>("prepareSandbox")

fun Test.configureUiIntegrationTest(sourceSet: SourceSet, config: UiIntegrationTestConfig) {
    description = "Runs UI integration tests with IntelliJ Starter/Driver against ${config.displayName}."
    group = "verification"

    testClassesDirs = sourceSet.output.classesDirs
    classpath = sourceSet.runtimeClasspath

    val pluginPath = builtPluginDir ?: prepareSandbox.get().pluginDirectory.get().asFile.absolutePath
    systemProperty("path.to.build.plugin", pluginPath)
    systemProperty("uiTestProductCode", config.productCode)
    systemProperty("uiTestIdeChannel", config.ideChannel)
    systemProperty("uiTestToolWindowId", config.toolWindowId)
    systemProperty("uiTestProjectPath", config.testProjectPath)
    systemProperty("uiTestSampleFileNames", config.sampleFileNames)
    systemProperty("uiTestSamplePath", config.samplePath)
    config.ideVersion?.let { systemProperty("uiTestIdeVersion", it) }

    environment("GDK_BACKEND", "x11")
    environment("XDG_SESSION_TYPE", "x11")
    environment("WAYLAND_DISPLAY", "")

    useJUnitPlatform()
    if (builtPluginDir == null) {
        dependsOn(prepareSandbox)
    }
}

tasks.named<VerifyPluginTask>("verifyPlugin") {
    builtPluginArchive?.let {
        archiveFile.set(file(it))
    }
}

tasks.named<PublishPluginTask>("publishPlugin") {
    builtPluginArchive?.let {
        archiveFile.set(file(it))
        setDependsOn(emptyList<Any>()) // avoid rebuild
    }
}

tasks.register<Test>("integrationTest2025_1") {
    configureUiIntegrationTest(
        integrationTest,
        UiIntegrationTestConfig(
            displayName = "IntelliJ IDEA Community 2025.1",
            productCode = "IC",
            ideVersion = "2025.1",
        ),
    )
}

tasks.register<Test>("integrationTest2025_2") {
    configureUiIntegrationTest(
        integrationTest,
        UiIntegrationTestConfig(
            displayName = "IntelliJ IDEA Community 2025.2",
            productCode = "IC",
            ideVersion = "2025.2",
        ),
    )
}

tasks.register<Test>("integrationTest2025_3") {
    configureUiIntegrationTest(
        integrationTest,
        UiIntegrationTestConfig(
            displayName = "IntelliJ IDEA 2025.3",
            productCode = "IU",
            ideVersion = "2025.3",
        ),
    )
}

tasks.register<Test>("integrationTestIdeaLatestRelease") {
    configureUiIntegrationTest(
        integrationTest,
        UiIntegrationTestConfig(
            displayName = "latest IntelliJ IDEA release",
            productCode = "IU",
        ),
    )
}

tasks.register<Test>("integrationTestIdeaLatestEap") {
    configureUiIntegrationTest(
        integrationTest,
        UiIntegrationTestConfig(
            displayName = "latest IntelliJ IDEA EAP",
            productCode = "IU",
            ideChannel = "eap",
        ),
    )
}

tasks.register<Test>("integrationTestGoLandLatestEap") {
    configureUiIntegrationTest(
        integrationTest,
        UiIntegrationTestConfig(
            displayName = "latest GoLand EAP",
            productCode = "GO",
            ideChannel = "eap",
            testProjectPath = "src/integrationTest/resources/test-projects/goland-project",
            sampleFileNames = "main.go",
            samplePath = "main.go",
        ),
    )
}

tasks.register<Test>("integrationTestRiderLatestEap") {
    configureUiIntegrationTest(
        integrationTest,
        UiIntegrationTestConfig(
            displayName = "latest Rider EAP",
            productCode = "RD",
            ideChannel = "eap",
            testProjectPath = "src/integrationTest/resources/test-projects/rider-project",
            sampleFileNames = "Program.cs",
            samplePath = "App/Program.cs",
        ),
    )
}

tasks.register<Test>("integrationTestWebStormLatestEap") {
    configureUiIntegrationTest(
        integrationTest,
        UiIntegrationTestConfig(
            displayName = "latest WebStorm EAP",
            productCode = "WS",
            ideChannel = "eap",
            testProjectPath = "src/integrationTest/resources/test-projects/webstorm-project",
            sampleFileNames = "index.js",
            samplePath = "src/index.js",
        ),
    )
}

tasks.register<Test>("integrationTestGoLandLatest") {
    configureUiIntegrationTest(
        integrationTest,
        UiIntegrationTestConfig(
            displayName = "latest GoLand release",
            productCode = "GO",
            testProjectPath = "src/integrationTest/resources/test-projects/goland-project",
            sampleFileNames = "main.go",
            samplePath = "main.go",
        ),
    )
}

tasks.register<Test>("integrationTestRiderLatest") {
    configureUiIntegrationTest(
        integrationTest,
        UiIntegrationTestConfig(
            displayName = "latest Rider release",
            productCode = "RD",
            testProjectPath = "src/integrationTest/resources/test-projects/rider-project",
            sampleFileNames = "Program.cs",
            samplePath = "App/Program.cs",
        ),
    )
}

tasks.register<Test>("integrationTestWebStormLatest") {
    configureUiIntegrationTest(
        integrationTest,
        UiIntegrationTestConfig(
            displayName = "latest WebStorm release",
            productCode = "WS",
            testProjectPath = "src/integrationTest/resources/test-projects/webstorm-project",
            sampleFileNames = "index.js",
            samplePath = "src/index.js",
        ),
    )
}

val legacyUiIntegrationTests = listOf(
    "integrationTest2025_1",
    "integrationTest2025_2",
    "integrationTest2025_3",
)

val stableUiIntegrationTests = listOf(
    "integrationTestIdeaLatestRelease",
    "integrationTestGoLandLatest",
    "integrationTestRiderLatest",
    "integrationTestWebStormLatest",
)

val eapUiIntegrationTests = listOf(
    "integrationTestIdeaLatestEap",
    "integrationTestGoLandLatestEap",
    "integrationTestRiderLatestEap",
    "integrationTestWebStormLatestEap",
)

fun registerIntegrationTestMatrix(taskName: String, description: String, taskNames: List<String>) {
    tasks.register(taskName) {
        group = "verification"
        this.description = description
        dependsOn(taskNames.map { tasks.named(it) })
    }
}

registerIntegrationTestMatrix(
    taskName = "integrationTestLegacyMatrix",
    description = "Runs the legacy UI integration test matrix.",
    taskNames = legacyUiIntegrationTests,
)

registerIntegrationTestMatrix(
    taskName = "integrationTestStableMatrix",
    description = "Runs the stable UI integration test matrix.",
    taskNames = stableUiIntegrationTests,
)

registerIntegrationTestMatrix(
    taskName = "integrationTestEapMatrix",
    description = "Runs the EAP UI integration test matrix.",
    taskNames = eapUiIntegrationTests,
)

registerIntegrationTestMatrix(
    taskName = "integrationTestMatrix",
    description = "Runs the full UI integration test matrix.",
    taskNames = legacyUiIntegrationTests + stableUiIntegrationTests + eapUiIntegrationTests,
)

tasks.register<GradleBuild>("runGoland") {
    group = "intellij"
    tasks = listOf("runIde")
    startParameter.projectProperties = mapOf("ide" to "GO")
}

tasks.register<GradleBuild>("runRider") {
    group = "intellij"
    tasks = listOf("runIde")
    startParameter.projectProperties = mapOf("ide" to "RD")
}

tasks.register<GradleBuild>("runIdeaUltimate") {
    group = "intellij"
    tasks = listOf("runIde")
    startParameter.projectProperties = mapOf("ide" to "IU")
}
