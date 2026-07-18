import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    java
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.kotlinJvm)
    id("scopes-manager.intellij-workflows")
    id("scopes-manager.ui-integration-tests")
}

group = "com.alexey-anufriev"
version = "2.0.0"

val ide = providers.gradleProperty("ide").getOrElse("IU")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        create(ide, libs.versions.platform.get())
        bundledPlugin("com.intellij.tasks")
        bundledPlugin("com.intellij.mcpServer")
        testFramework(TestFrameworkType.Starter)
        pluginVerifier()
        zipSigner()
    }

    testImplementation(libs.assertjCore)
    testImplementation(libs.mockitoKotlin)
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)

    // Required by the IntelliJ JUnit 5 test framework until JetBrains fixes IJPL-159134.
    // Remove once plugin tests pass without org/junit/rules/TestRule on the runtime classpath.
    testRuntimeOnly(libs.junit4)
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "253"
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

uiIntegrationTests {
    legacy(
        taskName = "integrationTest2025_3",
        displayName = "IntelliJ IDEA 2025.3",
        productCode = "IU",
        ideVersion = "2025.3",
    )

    product(
        displayName = "IntelliJ IDEA",
        productCode = "IU",
        releaseTaskName = "integrationTestIdeaLatestRelease",
        eapTaskName = "integrationTestIdeaLatestEap",
    )
    product(
        displayName = "CLion",
        productCode = "CL",
        releaseTaskName = "integrationTestCLionLatest",
        eapTaskName = "integrationTestCLionLatestEap",
        testProjectPath = "src/integrationTest/resources/test-projects/clion-project",
        sampleFileNames = "CMakeLists.txt",
        samplePath = "CMakeLists.txt",
    )
    product(
        displayName = "GoLand",
        productCode = "GO",
        releaseTaskName = "integrationTestGoLandLatest",
        eapTaskName = "integrationTestGoLandLatestEap",
        testProjectPath = "src/integrationTest/resources/test-projects/goland-project",
        sampleFileNames = "main.go",
        samplePath = "main.go",
    )
    product(
        displayName = "PyCharm",
        productCode = "PY",
        releaseTaskName = "integrationTestPyCharmLatest",
        eapTaskName = "integrationTestPyCharmLatestEap",
        testProjectPath = "src/integrationTest/resources/test-projects/pycharm-project",
        sampleFileNames = "app.py",
        samplePath = "src/app.py",
    )
    product(
        displayName = "Rider",
        productCode = "RD",
        releaseTaskName = "integrationTestRiderLatest",
        eapTaskName = "integrationTestRiderLatestEap",
        testProjectPath = "src/integrationTest/resources/test-projects/rider-project",
        sampleFileNames = "Program.cs",
        samplePath = "App/Program.cs",
    )
    product(
        displayName = "RubyMine",
        productCode = "RM",
        releaseTaskName = "integrationTestRubyMineLatest",
        eapTaskName = "integrationTestRubyMineLatestEap",
        testProjectPath = "src/integrationTest/resources/test-projects/rubymine-project",
        sampleFileNames = "app.rb",
        samplePath = "src/app.rb",
    )
    product(
        displayName = "RustRover",
        productCode = "RR",
        releaseTaskName = "integrationTestRustRoverLatest",
        eapTaskName = "integrationTestRustRoverLatestEap",
        testProjectPath = "src/integrationTest/resources/test-projects/rustrover-project",
        sampleFileNames = "main.rs",
        samplePath = "src/main.rs",
    )
    product(
        displayName = "WebStorm",
        productCode = "WS",
        releaseTaskName = "integrationTestWebStormLatest",
        eapTaskName = "integrationTestWebStormLatestEap",
        testProjectPath = "src/integrationTest/resources/test-projects/webstorm-project",
        sampleFileNames = "index.js",
        samplePath = "src/index.js",
    )
}
