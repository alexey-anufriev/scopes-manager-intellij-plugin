package com.alexey_anufriev.scopes_manager.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

private const val DEFAULT_PROJECT_PATH = "src/integrationTest/resources/test-projects/idea-project"
private const val DEFAULT_SAMPLE_FILES = "App,App.java"
private const val DEFAULT_SAMPLE_PATH = "src/main/java/sample/App"

/** Declarative registration API for legacy and product-specific UI integration tests. */
open class UiIntegrationTestsExtension internal constructor(
    private val project: Project,
    private val sourceSet: SourceSet,
    private val legacyMatrix: TaskProvider<*>,
    private val stableMatrix: TaskProvider<*>,
    private val eapMatrix: TaskProvider<*>,
    private val fullMatrix: TaskProvider<*>,
) {
    /** Registers a test task pinned to a specific IDE version. */
    fun legacy(
        taskName: String,
        displayName: String,
        productCode: String,
        ideVersion: String,
        toolWindowId: String = "Project",
        testProjectPath: String = DEFAULT_PROJECT_PATH,
        sampleFileNames: String = DEFAULT_SAMPLE_FILES,
        samplePath: String = DEFAULT_SAMPLE_PATH,
    ) {
        val task = registerTask(
            taskName,
            UiIntegrationTestConfig(
                displayName = displayName,
                productCode = productCode,
                ideVersion = ideVersion,
                toolWindowId = toolWindowId,
                testProjectPath = testProjectPath,
                sampleFileNames = sampleFileNames,
                samplePath = samplePath,
            ),
        )
        legacyMatrix.configure { dependsOn(task) }
        fullMatrix.configure { dependsOn(task) }
    }

    /** Registers the latest release and EAP tasks for one IDE product. */
    fun product(
        displayName: String,
        productCode: String,
        releaseTaskName: String,
        eapTaskName: String,
        toolWindowId: String = "Project",
        testProjectPath: String = DEFAULT_PROJECT_PATH,
        sampleFileNames: String = DEFAULT_SAMPLE_FILES,
        samplePath: String = DEFAULT_SAMPLE_PATH,
    ) {
        val releaseTask = registerTask(
            releaseTaskName,
            UiIntegrationTestConfig(
                displayName = "latest $displayName release",
                productCode = productCode,
                toolWindowId = toolWindowId,
                testProjectPath = testProjectPath,
                sampleFileNames = sampleFileNames,
                samplePath = samplePath,
            ),
        )
        val eapTask = registerTask(
            eapTaskName,
            UiIntegrationTestConfig(
                displayName = "latest $displayName EAP",
                productCode = productCode,
                ideChannel = "eap",
                toolWindowId = toolWindowId,
                testProjectPath = testProjectPath,
                sampleFileNames = sampleFileNames,
                samplePath = samplePath,
            ),
        )

        stableMatrix.configure { dependsOn(releaseTask) }
        eapMatrix.configure { dependsOn(eapTask) }
        fullMatrix.configure { dependsOn(releaseTask, eapTask) }
    }

    private fun registerTask(taskName: String, config: UiIntegrationTestConfig): TaskProvider<Test> =
        project.tasks.register(taskName, Test::class.java) {
            description = "Runs UI integration tests with IntelliJ Starter/Driver against ${config.displayName}."
            group = "verification"
            testClassesDirs = sourceSet.output.classesDirs
            classpath = sourceSet.runtimeClasspath

            val builtPluginDir = project.providers.gradleProperty("builtPluginDir").orNull
            val prepareSandbox = project.tasks.named("prepareSandbox", PrepareSandboxTask::class.java)
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
}

private data class UiIntegrationTestConfig(
    val displayName: String,
    val productCode: String,
    val ideVersion: String? = null,
    val ideChannel: String = "release",
    val toolWindowId: String,
    val testProjectPath: String,
    val sampleFileNames: String,
    val samplePath: String,
)
