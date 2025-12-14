package com.alexey_anufriev.scopes_manager

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

private const val ADD_TO_SCOPE_ACTION = "com.alexey-anufriev.scopes-manager.AddToScopeActionGroup"
private const val ADD_TO_SCOPE_ACTION_RD = "com.alexey-anufriev.scopes-manager.AddToScopeActionGroup_RD"
private const val REMOVE_FROM_SCOPE_ACTION = "com.alexey-anufriev.scopes-manager.RemoveFromScopeActionGroup"
private const val REMOVE_FROM_SCOPE_ACTION_RD = "com.alexey-anufriev.scopes-manager.RemoveFromScopeActionGroup_RD"

class ScopesManagerInitializer : StartupActivity {

    override fun runActivity(project: Project) {
        val rider = isRider()

        val addActionId = if (rider) ADD_TO_SCOPE_ACTION_RD else ADD_TO_SCOPE_ACTION
        val removeActionId = if (rider) REMOVE_FROM_SCOPE_ACTION_RD else REMOVE_FROM_SCOPE_ACTION

        val keymap = KeymapManagerEx.getInstanceEx().activeKeymap
        val warnings = mutableListOf<String>()

        warnings += keymap.tryAddOrExplainConflict(
            actionId = addActionId,
            shortcut = altShortcut(KeyEvent.VK_S),
            readableName = "Alt+S"
        )

        warnings += keymap.tryAddOrExplainConflict(
            actionId = removeActionId,
            shortcut = altShortcut(KeyEvent.VK_D),
            readableName = "Alt+D"
        )

        val actualWarnings = warnings.filter { it.isNotBlank() }
        if (actualWarnings.isNotEmpty()) {
            val text = buildString {
                append(actualWarnings.joinToString("\n\n"))
                append("\n\nYou can change it in Settings | Keymap.")
            }

            NotificationGroupManager.getInstance()
                .getNotificationGroup("ScopesManager")
                .createNotification(text, NotificationType.WARNING)
                .notify(project)
        }
    }

    private fun Keymap.tryAddOrExplainConflict(
        actionId: String,
        shortcut: KeyboardShortcut,
        readableName: String
    ): String {
        if (getShortcuts(actionId).any { it == shortcut }) {
            return ""
        }

        val conflicts = getConflicts(actionId, shortcut)
        if (conflicts.isNotEmpty()) {
            val conflictIds = conflicts.keys.joinToString()
            return "$readableName is already in use.\nConflicts with: $conflictIds"
        }

        addShortcut(actionId, shortcut)
        return ""
    }

    private fun altShortcut(keyCode: Int): KeyboardShortcut =
        KeyboardShortcut(KeyStroke.getKeyStroke(keyCode, InputEvent.ALT_DOWN_MASK), null)

    private fun isRider(): Boolean = ApplicationInfo.getInstance().build.productCode == "RD"

}
