package com.alexey_anufriev.scopes_manager.utils

import com.intellij.openapi.editor.colors.EditorColorsManager
import java.awt.Color
import java.util.Random

object UiUtils {

    fun getRandomColor() : Color {
        val darkEditor = EditorColorsManager.getInstance().isDarkEditor
        val brightness = if (darkEditor) 0.4f else 1.0f
        return Color.getHSBColor(Random().nextFloat(), 0.2f, brightness)
    }

}
