package com.alexey_anufriev.scopes_manager.utils

import com.intellij.openapi.actionSystem.AnActionEvent

object ActionUtils {

    fun hideAction(event: AnActionEvent) {
        event.presentation.isVisible = false
    }

}
