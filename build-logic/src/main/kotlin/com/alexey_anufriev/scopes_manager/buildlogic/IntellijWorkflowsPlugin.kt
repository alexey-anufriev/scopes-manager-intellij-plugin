package com.alexey_anufriev.scopes_manager.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.GradleBuild
import org.jetbrains.intellij.platform.gradle.tasks.PublishPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

/** Configures CI artifact reuse and convenience tasks for launching supported IDEs. */
class IntellijWorkflowsPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        val builtPluginArchive = providers.gradleProperty("builtPluginArchive")

        tasks.named("verifyPlugin", VerifyPluginTask::class.java) {
            builtPluginArchive.orNull?.let { archiveFile.set(file(it)) }
        }
        tasks.named("publishPlugin", PublishPluginTask::class.java) {
            builtPluginArchive.orNull?.let {
                archiveFile.set(file(it))
                setDependsOn(emptyList<Any>())
            }
        }

        mapOf(
            "runGoland" to "GO",
            "runRider" to "RD",
            "runIdeaUltimate" to "IU",
        ).forEach { (taskName, productCode) ->
            tasks.register(taskName, GradleBuild::class.java) {
                group = "intellij"
                tasks = listOf("runIde")
                startParameter.projectProperties = mapOf("ide" to productCode)
            }
        }
    }
}
