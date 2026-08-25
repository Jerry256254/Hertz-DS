package com.hertzds.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream

sealed interface DownloadProgress {
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : DownloadProgress
    data object Extracting : DownloadProgress
    data class Done(val dir: File) : DownloadProgress
    data class Failed(val message: String) : DownloadProgress
}

/**
 * Fetches a .tar.bz2 model archive from an official sherpa-onnx GitHub release and
 * extracts it under filesDir/voice_models/<id>/. Nothing is bundled in the APK —
 * the user taps "Download" and this pulls straight from GitHub.
 */
class ModelDownloader(private val context: Context, private val http: OkHttpClient) {

    fun modelsRoot(): File = File(context.filesDir, "voice_models").apply { mkdirs() }

    fun isDownloaded(id: String, extractedDirName: String): Boolean =
        File(File(modelsRoot(), id), extractedDirName).let { it.exists() && it.list()?.isNotEmpty() == true }

    fun directoryFor(id: String, extractedDirName: String): File =
        File(File(modelsRoot(), id), extractedDirName)

    fun download(id: String, archiveUrl: String, extractedDirName: String): Flow<DownloadProgress> = flow {
        val targetDir = File(modelsRoot(), id).apply { mkdirs() }
        val archiveFile = File(targetDir, "archive.tar.bz2")

        try {
            val request = Request.Builder().url(archiveUrl).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadProgress.Failed("HTTP ${response.code} for $archiveUrl"))
                    return@flow
                }
                val body = response.body ?: run {
                    emit(DownloadProgress.Failed("empty response"))
                    return@flow
                }
                val total = body.contentLength()
                var read = 0L
                body.byteStream().use { input ->
                    FileOutputStream(archiveFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            emit(DownloadProgress.Downloading(read, total))
                        }
                    }
                }
            }

            emit(DownloadProgress.Extracting)
            extractTarBz2(archiveFile, targetDir)
            archiveFile.delete()

            val extracted = File(targetDir, extractedDirName)
            if (!extracted.exists()) {
                emit(DownloadProgress.Failed("archive did not contain expected folder $extractedDirName"))
                return@flow
            }
            emit(DownloadProgress.Done(extracted))
        } catch (e: Exception) {
            archiveFile.delete()
            emit(DownloadProgress.Failed(e.message ?: "download failed"))
        }
    }.flowOn(Dispatchers.IO)

    fun delete(id: String) {
        File(modelsRoot(), id).deleteRecursively()
    }

    private fun extractTarBz2(archive: File, destDir: File) {
        BZip2CompressorInputStream(archive.inputStream().buffered()).use { bz2 ->
            TarArchiveInputStream(bz2).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    val outFile = File(destDir, entry.name)
                    val canonicalDest = destDir.canonicalFile
                    if (!outFile.canonicalFile.path.startsWith(canonicalDest.path)) {
                        error("archive entry escapes destination: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> tar.copyTo(out) }
                    }
                    entry = tar.nextEntry
                }
            }
        }
    }
}
