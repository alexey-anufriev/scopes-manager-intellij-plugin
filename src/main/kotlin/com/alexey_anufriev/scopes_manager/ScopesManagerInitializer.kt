package com.alexey_anufriev.scopes_manager

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class ScopesManagerInitializer : StartupActivity {

    override fun runActivity(project: Project) {
        val activeKeymap = KeymapManagerEx.getInstanceEx().activeKeymap

        activeKeymap.addShortcut(
            "com.alexey-anufriev.scopes-manager.AddToScopeActionGroup",
            KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK), null)
        )

        activeKeymap.addShortcut(
            "com.alexey-anufriev.scopes-manager.AddToScopeActionGroup_RD",
            KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK), null)
        )

        activeKeymap.addShortcut(
            "com.alexey-anufriev.scopes-manager.RemoveFromScopeActionGroup",
            KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.ALT_DOWN_MASK), null)
        )

        activeKeymap.addShortcut(
            "com.alexey-anufriev.scopes-manager.RemoveFromScopeActionGroup_RD",
            KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.ALT_DOWN_MASK), null)
        )
    }

}
