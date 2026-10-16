package com.example.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.sqrt

class WhisperSpeechRecognizerEngine(
    private val context: Context
) : SpeechRecognizerEngine {

    companion object {
        private const val TAG = "WhisperSpeechEngine"
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_COUNT = 1
        private const val BYTES_PER_SAMPLE = 2
        private const val MODEL_FILE_NAME = "ggml-base.bin"
        private const val MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin?download=true"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val modelLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    private val _partialResult = MutableStateFlow("")
    override val partialResult: StateFlow<String> = _partialResult.asStateFlow()
    private val _finalResult = MutableStateFlow("")
    override val finalResult: StateFlow<String> = _finalResult.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _rmsDb = MutableStateFlow(0f)
    override val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var wavFile: File? = null
    private var currentLanguageCode = "en"
    private var stopRequested = false
    @Volatile private var pauseRequested = false
    private var cancelRequested = false

    override fun isOfflineRecognitionAvailable(): Boolean = true

    override fun startListening(bcp47Tag: String, preferOfflineOnly: Boolean) {
        mainHandler.post {
            stopInternal()
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                _errorMessage.value = "Microphone permission is required. Please allow microphone access in App Settings and try again."
                _isListening.value = false
                return@post
            }
            _errorMessage.value = null
            _partialResult.value = ""
            _finalResult.value = ""
            _rmsDb.value = 0f
            currentLanguageCode = bcp47Tag.substringBefore("-").lowercase()
            stopRequested = false
            pauseRequested = false
            cancelRequested = false
            _isListening.value = true

            scope.launch {
                try {
                    val model = ensureModel()
                    if (stopRequested) return@launch
                    _partialResult.value = "Offline speech model ready. Speak now…"
                    startRecording(model)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to prepare offline speech recognition", e)
                    _isListening.value = false
                    _rmsDb.value = 0f
                    _errorMessage.value =
                        "Offline speech model could not be prepared. Connect to the internet once to download the approximately 142 MB multilingual base model. After that, speech recognition works offline for supported languages."
                }
            }
        }
    }

    private suspend fun ensureModel(): File = withContext(Dispatchers.IO) {
      synchronized(modelLock) {
        val dir = File(context.getExternalFilesDir("models"), "whisper")
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Unable to create model directory")

        val modelFile = File(dir, MODEL_FILE_NAME)
        if (modelFile.exists() && modelFile.length() > 50_000_000L) {
            File(dir, "ggml-tiny.bin").delete()
            return@synchronized modelFile
        }

        // Optional bundled model support for developer builds. The normal APK downloads the
        // smaller multilingual tiny model once, then reuses it for every selected language.
        val bundledModel = runCatching { context.assets.open(MODEL_FILE_NAME) }.getOrNull()
        if (bundledModel != null) {
            _partialResult.value = "Preparing built-in offline speech model…"
            val tempFile = File(dir, "$MODEL_FILE_NAME.part")
            bundledModel.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                    output.fd.sync()
                }
            }
            if (tempFile.length() < 50_000_000L) {
                tempFile.delete()
                throw IllegalStateException("Bundled speech model is incomplete")
            }
            if (modelFile.exists()) modelFile.delete()
            if (!tempFile.renameTo(modelFile)) {
                tempFile.copyTo(modelFile, overwrite = true)
                tempFile.delete()
            }
            return@withContext modelFile
        }

        // Normal first-run path: download the multilingual model once; keep it on-device for offline use.
        _partialResult.value = "Downloading multilingual offline speech model (one-time setup)…"
        val tempFile = File(dir, "$MODEL_FILE_NAME.part")
        val request = okhttp3.Request.Builder().url(MODEL_URL).build()
        okhttp3.OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Model download failed: HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("Model download was empty")
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                    output.fd.sync()
                }
            }
        }
        if (tempFile.length() < 50_000_000L) {
            tempFile.delete()
            throw IllegalStateException("Downloaded model is incomplete")
        }
        if (modelFile.exists()) modelFile.delete()
        if (!tempFile.renameTo(modelFile)) {
            tempFile.copyTo(modelFile, overwrite = true)
            tempFile.delete()
        }
        File(dir, "ggml-tiny.bin").delete()
        modelFile
      }
    }

    private suspend fun startRecording(modelFile: File) {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) throw IllegalStateException("Microphone is not supported")

        val bufferSize = maxOf(minBuffer * 2, SAMPLE_RATE / 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Microphone could not be initialized")
        }

        val output = File(
            context.getExternalFilesDir(null),
            "apna_translator_speech_${System.currentTimeMillis()}.wav"
        )
        wavFile = output
        audioRecord = recorder
        writeWavHeader(output, 0)
        recorder.startRecording()
        _partialResult.value = ""

        recordingThread = Thread {
            val buffer = ShortArray(bufferSize / 2)
            var dataBytes = 0L
            try {
                BufferedOutputStream(FileOutputStream(output, true)).use { wavOut ->
                    while (!stopRequested) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read <= 0) continue
                        if (pauseRequested) {
                            _rmsDb.value = 0f
                            continue
                        }
                        for (i in 0 until read) {
                            val sample = buffer[i].toInt()
                            wavOut.write(sample and 0xFF)
                            wavOut.write((sample shr 8) and 0xFF)
                        }
                        dataBytes += read * BYTES_PER_SAMPLE

                        var sum = 0.0
                        for (i in 0 until read) {
                            val v = buffer[i].toDouble()
                            sum += v * v
                        }
                        val rms = sqrt(sum / read.coerceAtLeast(1))
                        _rmsDb.value = if (rms > 0.0) {
                            (20.0 * kotlin.math.log10(rms / 32768.0)).toFloat().coerceIn(-60f, 0f)
                        } else -60f
                    }
                    wavOut.flush()
                }

                writeWavHeader(output, dataBytes)
                recorder.stop()
                recorder.release()
                audioRecord = null

                if (!cancelRequested && dataBytes >= SAMPLE_RATE * BYTES_PER_SAMPLE / 2) {
                    transcribe(modelFile, output)
                } else {
                    _isListening.value = false
                    if (!cancelRequested) _errorMessage.value = "No speech detected. Please try again."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording failed", e)
                try { recorder.release() } catch (_: Exception) {}
                audioRecord = null
                _isListening.value = false
                if (!cancelRequested) _errorMessage.value = "Microphone recording failed. Please try again."
            }
        }.also { it.start() }
    }

    private fun transcribe(modelFile: File, audioFile: File) {
        scope.launch(Dispatchers.Default) {
            try {
                _partialResult.value = "Recognizing offline…"
                val model = Whisper.loadModel(context, modelFile.absolutePath)
                try {
                    val result = Whisper.transcribe(
                        model,
                        audioFile.absolutePath,
                        WhisperConfig(language = currentLanguageCode)
                    )
                    val text = result.text.trim()
                    withContext(Dispatchers.Main) {
                        _isListening.value = false
                        _rmsDb.value = 0f
                        if (text.isNotBlank() && matchesSelectedLanguageScript(text, currentLanguageCode)) {
                            _finalResult.value = text
                            _partialResult.value = text
                            _errorMessage.value = null
                        } else if (text.isNotBlank()) {
                            _partialResult.value = ""
                            _errorMessage.value = "Speech recognition returned text in the wrong script for the selected language. Check the language and try again."
                        } else {
                            _partialResult.value = ""
                            _errorMessage.value = "Speech could not be recognized. Please try again."
                        }
                    }
                } finally {
                    Whisper.releaseModel(model)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Whisper transcription failed", e)
                withContext(Dispatchers.Main) {
                    _isListening.value = false
                    _rmsDb.value = 0f
                    _errorMessage.value =
                        "Offline speech recognition failed for this recording. Please try again."
                }
            } finally {
                audioFile.delete()
            }
        }
    }

    override fun pauseListening() {
        pauseRequested = true
        _partialResult.value = "Paused"
        _rmsDb.value = 0f
    }

    override fun resumeListening() {
        pauseRequested = false
        _partialResult.value = ""
    }

    override fun stopListening() {
        mainHandler.post {
            stopRequested = true
            cancelRequested = false
            _rmsDb.value = 0f
            try { audioRecord?.stop() } catch (_: Exception) {}
            _isListening.value = false
        }
    }

    override fun cancel() {
        mainHandler.post {
            stopRequested = true
            cancelRequested = true
            try { audioRecord?.stop() } catch (_: Exception) {}
            try { audioRecord?.release() } catch (_: Exception) {}
            audioRecord = null
            recordingThread = null
            wavFile?.delete()
            wavFile = null
            _isListening.value = false
            _rmsDb.value = 0f
            _partialResult.value = ""
        }
    }

    override fun release() {
        cancel()
        scope.cancel()
    }

    private fun matchesSelectedLanguageScript(text: String, languageCode: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        val expectedScript = when (languageCode.lowercase()) {
            "hi", "ne", "mr" -> Character.UnicodeScript.DEVANAGARI
            "ar", "ur" -> Character.UnicodeScript.ARABIC
            "bn" -> Character.UnicodeScript.BENGALI
            "pa" -> Character.UnicodeScript.GURMUKHI
            "ta" -> Character.UnicodeScript.TAMIL
            "te" -> Character.UnicodeScript.TELUGU
            "kn" -> Character.UnicodeScript.KANNADA
            "ml" -> Character.UnicodeScript.MALAYALAM
            "gu" -> Character.UnicodeScript.GUJARATI
            else -> null
        } ?: return true
        val matching = letters.count { Character.UnicodeScript.of(it.code) == expectedScript }
        return matching.toDouble() / letters.size >= 0.6
    }

    private fun writeWavHeader(file: File, dataBytes: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            // Rewrite only the 44-byte WAV header; keep the recorded PCM payload intact.
            raf.seek(0)
            raf.writeBytes("RIFF")
            raf.writeIntLE((36 + dataBytes).toInt())
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.writeIntLE(16)
            raf.writeShortLE(1)
            raf.writeShortLE(CHANNEL_COUNT)
            raf.writeIntLE(SAMPLE_RATE)
            raf.writeIntLE(SAMPLE_RATE * CHANNEL_COUNT * BYTES_PER_SAMPLE)
            raf.writeShortLE(CHANNEL_COUNT * BYTES_PER_SAMPLE)
            raf.writeShortLE(16)
            raf.writeBytes("data")
            raf.writeIntLE(dataBytes.toInt())
        }
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()))
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
    }

    private fun stopInternal() {
        stopRequested = true
        pauseRequested = false
        cancelRequested = true
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        recordingThread = null
        wavFile?.delete()
        wavFile = null
        _isListening.value = false
        _rmsDb.value = 0f
    }
}
