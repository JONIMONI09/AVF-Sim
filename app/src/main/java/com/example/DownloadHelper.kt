package com.example

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL

object DownloadHelper {

    private val client = OkHttpClient()

    /**
     * Downloads a file from a URL to the internal files directory.
     */
    suspend fun downloadFile(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        // Sanitize filename
        val sanitizedFileName = fileName.replace("..", "").replace("/", "")
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) return@withContext null
            
            val body = response.body ?: return@withContext null
            val contentLength = body.contentLength()
            val targetFile = File(context.filesDir, sanitizedFileName)
            
            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(targetFile)
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead: Long = 0
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (contentLength > 0) {
                    onProgress(totalBytesRead.toFloat() / contentLength)
                }
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            // Set executable permission if it's a binary
            if (fileName == "qemu-system-aarch64" || fileName == "proot") {
                targetFile.setExecutable(true, false)
            }
            
            return@withContext targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Downloads an OS Image to the VirtualDisks directory.
     */
    suspend fun downloadOSImage(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        try {
            val targetDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "VirtualDisks")
            if (!targetDir.exists()) targetDir.mkdirs()
            
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) return@withContext null
            
            val body = response.body ?: return@withContext null
            val contentLength = body.contentLength()
            val targetFile = File(targetDir, fileName)
            
            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(targetFile)
            
            val buffer = ByteArray(64 * 1024) // 64KB for large images
            var bytesRead: Int
            var totalBytesRead: Long = 0
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (contentLength > 0) {
                    onProgress(totalBytesRead.toFloat() / contentLength)
                }
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            return@withContext targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
