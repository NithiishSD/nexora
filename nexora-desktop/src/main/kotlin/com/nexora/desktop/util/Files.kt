package com.nexora.desktop.util

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Native open/save dialogs (Windows and Linux file pickers) for CSV export and documents. */
object FileDialogs {

    /** Asks where to save; returns null when the user cancels. */
    fun chooseSaveFile(title: String, defaultName: String): Path? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true // blocks until the user closes the dialog
        val name = dialog.file ?: return null
        return File(dialog.directory, name).toPath()
    }

    fun chooseOpenFile(title: String): Path? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, name).toPath()
    }

    /** Saves [bytes] where the user chooses; returns the path or null when cancelled. */
    fun save(title: String, defaultName: String, bytes: ByteArray): Path? =
        chooseSaveFile(title, defaultName)?.also { Files.write(it, bytes) }
}
