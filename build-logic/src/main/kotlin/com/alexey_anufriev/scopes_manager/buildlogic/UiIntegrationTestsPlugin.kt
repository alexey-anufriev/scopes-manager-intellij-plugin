package com.alexey_anufriev.scopes_manager.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet

/** Configures the integration-test source set and exposes the UI test matrix DSL. */
class UiIntegrationTestsPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        val sourceSets = extensions.getByType(JavaPluginExtension::class.java).sourceSets
        val integrationTest = sourceSets.create("integrationTest") {
            compileClasspath += sourceSets.getByName("main").output
            runtimeClasspath += sourceSets.getByName("main").output
        }

        configureDependencies(integrationTest)

        val legacyMatrix = registerMatrix(
            "integrationTestLegacyMatrix",
            "Runs the legacy UI integration test matrix.",
        )
        val stableMatrix = registerMatrix(
            "integrationTestStableMatrix",
            "Runs the stable UI integration test matrix.",
        )
        val eapMatrix = registerMatrix(
            "integrationTestEapMatrix",
            "Runs the EAP UI integration test matrix.",
        )
        val fullMatrix = registerMatrix(
            "integrationTestMatrix",
            "Runs the full UI integration test matrix.",
        )

        extensions.create(
            "uiIntegrationTests",
            UiIntegrationTestsExtension::class.java,
            project,
            integrationTest,
            legacyMatrix,
            stableMatrix,
            eapMatrix,
            fullMatrix,
        )
        Unit
    }

    private fun Project.configureDependencies(integrationTest: SourceSet) {
        val libraries = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
        val sourceSets = extensions.getByType(JavaPluginExtension::class.java).sourceSets

        dependencies.add(integrationTest.implementationConfigurationName, sourceSets.getByName("main").output)
        dependencies.add(integrationTest.implementationConfigurationName, libraries.findLibrary("junitJupiter").get())
        dependencies.add(integrationTest.implementationConfigurationName, libraries.findLibrary("kodein").get())
        dependencies.add(integrationTest.implementationConfigurationName, libraries.findLibrary("coroutinesCore").get())
        // Required to deserialize AssertJ Swing exceptions returned by the remote Driver on CI.
        dependencies.add(integrationTest.runtimeOnlyConfigurationName, libraries.findLibrary("assertjSwing").get())

        configurations.named(integrationTest.implementationConfigurationName) {
            extendsFrom(
                configurations.getByName("testImplementation"),
                configurations.getByName("intellijPlatformTestDependencies"),
            )
        }
        configurations.named(integrationTest.runtimeOnlyConfigurationName) {
            extendsFrom(configurations.getByName("testRuntimeOnly"))
        }
    }

    private fun Project.registerMatrix(taskName: String, description: String) = tasks.register(taskName) {
        group = "verification"
        this.description = description
    }
}
