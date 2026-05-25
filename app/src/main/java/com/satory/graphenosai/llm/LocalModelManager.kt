package com.satory.graphenosai.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages downloading and storage of local AI models (GGUF format)
 * Models are optimized for ARM64 Pixel devices running GrapheneOS
 */
class LocalModelManager(private val context: Context) {

    companion object {
        private const val TAG = "LocalModelManager"
        
        // Directory for storing models
        private const val MODELS_DIR = "local_models"
        
        // Buffer size for downloads (256KB for better performance)
        private const val DOWNLOAD_BUFFER_SIZE = 256 * 1024
        
        /**
         * Available models optimized for ARM64/Pixel devices
         * These are quantized models that balance quality and performance
         * 
         * Selection criteria:
         * - Q4_K_M quantization: Best quality/size ratio for mobile
         * - 1B-8B parameters: Optimal for Pixel 6/7/8 with 8-12GB RAM
         * - Instruction-tuned: Better at following prompts
         * - ChatML or Gemma format: Standard prompt format support
         */
        val AVAILABLE_MODELS = listOf(
            // Qwen3 4B - Latest generation with thinking/non-thinking modes
            LocalModelInfo(
                id = "qwen3-4b",
                name = "Qwen3 4B",
                description = "Latest Qwen, thinking mode, 32K context",
                sizeBytes = 2_500_000_000L, // ~2.5GB
                downloadUrl = "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf",
                filename = "Qwen3-4B-Q4_K_M.gguf",
                contextSize = 32768,
                recommended = true
            ),

            // Qwen3 1.7B - Fastest Qwen3 for low-memory devices
            LocalModelInfo(
                id = "qwen3-1.7b",
                name = "Qwen3 1.7B",
                description = "Fast Qwen3, thinking mode, multilingual",
                sizeBytes = 1_830_000_000L, // ~1.83GB (Q8_0)
                downloadUrl = "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q8_0.gguf",
                filename = "Qwen3-1.7B-Q8_0.gguf",
                contextSize = 32768,
                recommended = false
            ),

            // Gemma 4 E2B - Google's latest, optimized for phones
            LocalModelInfo(
                id = "gemma-4-e2b",
                name = "Gemma 4 E2B",
                description = "Google's latest, thinking mode, 128K context",
                sizeBytes = 3_110_000_000L, // ~3.11GB
                downloadUrl = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_K_M.gguf",
                filename = "gemma-4-E2B-it-Q4_K_M.gguf",
                contextSize = 8192,
                recommended = true,
                promptFormat = "gemma"
            ),

            // Gemma 4 E4B - Larger variant for laptops/tablets
            LocalModelInfo(
                id = "gemma-4-e4b",
                name = "Gemma 4 E4B",
                description = "Google's larger model, thinking mode, 128K context",
                sizeBytes = 4_980_000_000L, // ~4.98GB
                downloadUrl = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF/resolve/main/gemma-4-E4B-it-Q4_K_M.gguf",
                filename = "gemma-4-E4B-it-Q4_K_M.gguf",
                contextSize = 8192,
                recommended = false,
                promptFormat = "gemma"
            ),

            // DeepSeek-R1-Distill-Qwen-1.5B - Reasoning distilled from R1
            LocalModelInfo(
                id = "deepseek-r1-distill-qwen-1.5b",
                name = "DeepSeek R1 1.5B",
                description = "R1-distilled reasoning, fast inference",
                sizeBytes = 1_120_000_000L, // ~1.12GB
                downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                filename = "deepseek-r1-distill-qwen-1.5b-q4_k_m.gguf",
                contextSize = 8192,
                recommended = true
            ),

            // SmolLM3 3B - Latest generation, multilingual, 128K context
            LocalModelInfo(
                id = "smollm3-3b",
                name = "SmolLM3 3B",
                description = "Latest SmolLM, multilingual, 128K context",
                sizeBytes = 1_920_000_000L, // ~1.92GB
                downloadUrl = "https://huggingface.co/ggml-org/SmolLM3-3B-GGUF/resolve/main/SmolLM3-Q4_K_M.gguf",
                filename = "smollm3-3b-q4_k_m.gguf",
                contextSize = 8192,
                recommended = true
            ),

            // Phi-4-mini - Microsoft's latest efficient model, 128K context
            LocalModelInfo(
                id = "phi-4-mini-instruct",
                name = "Phi-4 Mini 3.8B",
                description = "Microsoft's latest, excellent reasoning, 128K",
                sizeBytes = 2_490_000_000L, // ~2.49GB
                downloadUrl = "https://huggingface.co/bartowski/microsoft_Phi-4-mini-instruct-GGUF/resolve/main/microsoft_Phi-4-mini-instruct-Q4_K_M.gguf",
                filename = "phi-4-mini-instruct-q4_k_m.gguf",
                contextSize = 8192,
                recommended = true
            ),

            // Gemma 3 4B IT - Google's latest, multilingual, vision-capable
            LocalModelInfo(
                id = "gemma-3-4b-it",
                name = "Gemma 3 4B",
                description = "Google's latest, multilingual, 128K context",
                sizeBytes = 2_490_000_000L, // ~2.49GB
                downloadUrl = "https://huggingface.co/bartowski/google_gemma-3-4b-it-GGUF/resolve/main/google_gemma-3-4b-it-Q4_K_M.gguf",
                filename = "google_gemma-3-4b-it-q4_k_m.gguf",
                contextSize = 8192,
                recommended = false,
                promptFormat = "gemma"
            )
        )
    }
    
    private val modelsDir: File by lazy {
        File(context.filesDir, MODELS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }
    
    /**
     * Get the models directory path
     */
    fun getModelsDirectory(): File = modelsDir
    
    /**
     * Get all downloaded models
     */
    fun getDownloadedModels(): List<LocalModelInfo> {
        return AVAILABLE_MODELS.filter { isModelDownloaded(it.id) }
    }
    
    /**
     * Check if a specific model is downloaded
     */
    fun isModelDownloaded(modelId: String): Boolean {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return false
        val modelFile = File(modelsDir, model.filename)
        return modelFile.exists() && modelFile.length() > 0
    }
    
    /**
     * Get the file path for a model
     */
    fun getModelPath(modelId: String): String? {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return null
        val modelFile = File(modelsDir, model.filename)
        return if (modelFile.exists()) modelFile.absolutePath else null
    }
    
    /**
     * Get model info by ID
     */
    fun getModelInfo(modelId: String): LocalModelInfo? {
        return AVAILABLE_MODELS.find { it.id == modelId }
    }
    
    /**
     * Download a model with progress updates
     * Returns a Flow with download progress (0-100) or error
     */
    fun downloadModel(modelId: String): Flow<DownloadProgress> = flow {
        val model = AVAILABLE_MODELS.find { it.id == modelId }
        if (model == null) {
            emit(DownloadProgress.Error("Model not found: $modelId"))
            return@flow
        }
        
        val modelFile = File(modelsDir, model.filename)
        val tempFile = File(modelsDir, "${model.filename}.tmp")
        
        // Check if already downloaded
        if (modelFile.exists() && modelFile.length() == model.sizeBytes) {
            Log.i(TAG, "Model already downloaded: ${model.name}")
            emit(DownloadProgress.Completed(modelFile.absolutePath))
            return@flow
        }
        
        Log.i(TAG, "Starting download: ${model.name} from ${model.downloadUrl}")
        emit(DownloadProgress.Started(model.name))
        
        try {
            val url = URL(model.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.setRequestProperty("User-Agent", "GrapheneOS-AI-Assistant/1.0")
            
            // Support resuming downloads
            var downloadedBytes = 0L
            if (tempFile.exists()) {
                downloadedBytes = tempFile.length()
                connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
                Log.i(TAG, "Resuming download from byte $downloadedBytes")
            }
            
            connection.connect()
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                emit(DownloadProgress.Error("Download failed: HTTP $responseCode"))
                return@flow
            }
            
            val totalBytes = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                model.sizeBytes
            } else {
                connection.contentLength.toLong().let { if (it > 0) it else model.sizeBytes }
            }
            
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(tempFile, downloadedBytes > 0)
            
            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
            var bytesRead: Int
            var totalDownloaded = downloadedBytes
            var lastProgressUpdate = 0
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalDownloaded += bytesRead
                
                val progress = ((totalDownloaded.toFloat() / totalBytes) * 100).toInt()
                if (progress > lastProgressUpdate) {
                    lastProgressUpdate = progress
                    emit(DownloadProgress.Downloading(progress, totalDownloaded, totalBytes))
                }
            }
            
            outputStream.close()
            inputStream.close()
            connection.disconnect()
            
            // Rename temp file to final name
            if (tempFile.renameTo(modelFile)) {
                Log.i(TAG, "Download completed: ${model.name}")
                emit(DownloadProgress.Completed(modelFile.absolutePath))
            } else {
                emit(DownloadProgress.Error("Failed to finalize download"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            emit(DownloadProgress.Error("Download failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Delete a downloaded model
     */
    suspend fun deleteModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val model = AVAILABLE_MODELS.find { it.id == modelId } ?: return@withContext false
        val modelFile = File(modelsDir, model.filename)
        val tempFile = File(modelsDir, "${model.filename}.tmp")
        
        var deleted = false
        if (modelFile.exists()) {
            deleted = modelFile.delete()
        }
        if (tempFile.exists()) {
            tempFile.delete()
        }
        
        Log.i(TAG, "Deleted model $modelId: $deleted")
        deleted
    }
    
    /**
     * Get total storage used by downloaded models
     */
    fun getTotalStorageUsed(): Long {
        return modelsDir.listFiles()
            ?.filter { it.isFile && it.extension == "gguf" }
            ?.sumOf { it.length() }
            ?: 0L
    }
    
    /**
     * Get available storage on device
     */
    fun getAvailableStorage(): Long {
        return modelsDir.freeSpace
    }
}

/**
 * Information about a downloadable local model
 */
data class LocalModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val filename: String,
    val contextSize: Int,
    val recommended: Boolean = false,
    val promptFormat: String = "chatml"
) {
    /**
     * Format size as human-readable string
     */
    fun formattedSize(): String {
        return when {
            sizeBytes >= 1_000_000_000 -> String.format("%.1f GB", sizeBytes / 1_000_000_000.0)
            sizeBytes >= 1_000_000 -> String.format("%.1f MB", sizeBytes / 1_000_000.0)
            else -> String.format("%.1f KB", sizeBytes / 1_000.0)
        }
    }
}

/**
 * Download progress state
 */
sealed class DownloadProgress {
    data class Started(val modelName: String) : DownloadProgress()
    data class Downloading(val percent: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloadProgress()
    data class Completed(val filePath: String) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
