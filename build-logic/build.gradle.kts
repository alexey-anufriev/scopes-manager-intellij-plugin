plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.intellijPlatformGradlePlugin)
}

gradlePlugin {
    plugins {
        register("intellijWorkflows") {
            id = "scopes-manager.intellij-workflows"
            implementationClass = "com.alexey_anufriev.scopes_manager.buildlogic.IntellijWorkflowsPlugin"
        }
        register("uiIntegrationTests") {
            id = "scopes-manager.ui-integration-tests"
            implementationClass = "com.alexey_anufriev.scopes_manager.buildlogic.UiIntegrationTestsPlugin"
        }
    }
}
