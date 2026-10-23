package com.example.expensetracker.data.parser

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class ModelDownloadState {
    data object NotDownloaded : ModelDownloadState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadState()
    data class Downloaded(val modelFile: File) : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

class ModelDownloadManager(private val context: Context) {

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    // Default 350MB INT4 Quantized Lightweight Qwen 0.5B Model for On-Device Text Processing
    private val modelUrl = "https://huggingface.co/Qwen/Qwen1.5-0.5B-Chat-GGUF/resolve/main/qwen1_5-0_5b-chat-q4_k_m.gguf"

    fun getModelFile(): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()

        // Check standard target file
        val standardFile = File(modelsDir, "local_llm_model.bin")
        if (standardFile.exists() && standardFile.length() > 50 * 1024 * 1024) {
            return standardFile
        }

        // Search for any existing .bin or .gguf file (> 50 MB) in modelsDir or filesDir
        val existingFiles = mutableListOf<File>()
        modelsDir.listFiles()?.forEach {
            if (it.length() > 50 * 1024 * 1024 && (it.name.endsWith(".bin", true) || it.name.endsWith(".gguf", true))) {
                existingFiles.add(it)
            }
        }
        context.filesDir.listFiles()?.forEach {
            if (it.length() > 50 * 1024 * 1024 && (it.name.endsWith(".bin", true) || it.name.endsWith(".gguf", true))) {
                existingFiles.add(it)
            }
        }

        return existingFiles.firstOrNull() ?: standardFile
    }

    fun isModelDownloaded(): Boolean {
        val file = getModelFile()
        return file.exists() && file.length() > 50 * 1024 * 1024 // > 50 MB
    }

    init {
        val file = getModelFile()
        if (isModelDownloaded()) {
            _downloadState.value = ModelDownloadState.Downloaded(file)
        }
    }

    suspend fun startDownload() = withContext(Dispatchers.IO) {
        val file = getModelFile()
        if (isModelDownloaded()) {
            _downloadState.value = ModelDownloadState.Downloaded(file)
            return@withContext
        }

        try {
            _downloadState.value = ModelDownloadState.Downloading(0, 0, 1)

            val url = URL(modelUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                _downloadState.value = ModelDownloadState.Error("Server returned HTTP ${connection.responseCode}")
                return@withContext
            }

            val totalBytes = connection.contentLengthLong
            var bytesDownloaded = 0L

            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        val progress = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
                        _downloadState.value = ModelDownloadState.Downloading(progress, bytesDownloaded, totalBytes)
                    }
                }
            }

            _downloadState.value = ModelDownloadState.Downloaded(file)
        } catch (e: Exception) {
            if (file.exists()) file.delete()
            _downloadState.value = ModelDownloadState.Error(e.localizedMessage ?: "Failed to download model file")
        }
    }

    fun deleteModel() {
        val file = getModelFile()
        if (file.exists()) {
            file.delete()
        }
        _downloadState.value = ModelDownloadState.NotDownloaded
    }
}
