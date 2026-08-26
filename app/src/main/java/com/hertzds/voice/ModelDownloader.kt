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

    companion object {
        /** Below this, an .onnx file is a truncated/corrupt model, not a real one. */
        private const val MIN_ONNX_BYTES = 500_000L
    }

    fun modelsRoot(): File = File(context.filesDir, "voice_models").apply { mkdirs() }

    /**
     * True only if the folder holds a real, complete model — not merely non-empty.
     * A voice downloaded before atomic-extraction was added could be sitting here
     * truncated; treating "folder exists" as "downloaded" would keep silently
     * feeding that corrupt model to the native engine (and to speak() falling
     * back to system TTS) forever. Checking real .onnx size lets a corrupt
     * leftover heal itself the next time the user (re)downloads the voice.
     */
    fun isDownloaded(id: String, extractedDirName: String): Boolean {
        val dir = File(File(modelsRoot(), id), extractedDirName)
        if (!dir.exists()) return false
        val onnxFiles = dir.listFiles { f -> f.extension == "onnx" } ?: return false
        return onnxFiles.any { it.length() >= MIN_ONNX_BYTES }
    }

    fun directoryFor(id: String, extractedDirName: String): File =
        File(File(modelsRoot(), id), extractedDirName)

    /**
     * Downloads and extracts atomically: extraction lands in a `.partial` staging
     * folder and is only renamed to its final name once fully complete. A model
     * folder that a viewer/downloader can see under [extractedDirName] is
     * therefore guaranteed complete — [isDownloaded] can never see (and the
     * native TTS/STT engines can never try to load) a truncated model left
     * behind by an interrupted download, which otherwise loads as a corrupt
     * ONNX file and crashes the process at the native layer, uncatchable from
     * Kotlin.
     */
    fun download(id: String, archiveUrl: String, extractedDirName: String): Flow<DownloadProgress> = flow {
        val targetDir = File(modelsRoot(), id).apply { mkdirs() }
        val archiveFile = File(targetDir, "archive.tar.bz2")
        val stagingDir = File(targetDir, "$extractedDirName.partial")

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
                if (total > 0 && read != total) {
                    error("incomplete download: got $read of $total bytes")
                }
            }

            emit(DownloadProgress.Extracting)
            stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            extractTarBz2(archiveFile, stagingDir)
            archiveFile.delete()

            val stagedModel = File(stagingDir, extractedDirName)
            if (!stagedModel.exists()) {
                error("archive did not contain expected folder $extractedDirName")
            }
            val finalDir = File(targetDir, extractedDirName)
            finalDir.deleteRecursively()
            if (!stagedModel.renameTo(finalDir)) {
                error("could not finalize downloaded model")
            }
            stagingDir.deleteRecursively()

            emit(DownloadProgress.Done(finalDir))
        } catch (e: Exception) {
            archiveFile.delete()
            stagingDir.deleteRecursively()
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
