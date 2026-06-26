package com.example

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object StorageHelper {
    /**
     * Kopiert eine Datei von einer Content-URI in den internen App-Speicher (VirtualDisks).
     * Dies ist notwendig, da QEMU/Crosvm keine direkten Content-URIs lesen können.
     */
    fun copyUriToInternalStorage(context: Context, uri: Uri): String? {
        try {
            val fileName = getFileName(context, uri) ?: "imported_disk_${System.currentTimeMillis()}.img"
            val targetDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "VirtualDisks")
            if (!targetDir.exists()) targetDir.mkdirs()
            
            val targetFile = File(targetDir, fileName)
            
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(targetFile)
            
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            
            return targetFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }
}
