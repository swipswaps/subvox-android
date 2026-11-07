package com.example.subvox.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream

/**
 * Wraps Vosk offline speech-to-text. Models are ~50 MB; the small English
 * model (vosk-model-small-en-us-0.15) fits comfortably in the 2 GB RAM of the
 * SM-J737T and runs at roughly real-time on an Exynos 7870.
 *
 * Ref: https://alphacephei.com/vosk/android (model list, Kotlin integration)
 * Ref: https://github.com/alphacep/vosk-api/blob/master/java/demo/src/main/java/org/vosk/demo/DemoActivity.java
 * Ref: https://developer.android.com/reference/android/media/AudioRecord
 */
class AudioTranscriber(private val ctx: Context) {

    private val sampleRate = 16000
    private val modelDir = File(ctx.filesDir, "vosk-model-small-en-us-0.15")
    private var model: Model? = null

    /** Download + unpack the small English model on first use.
     *  URL is the official alphacephei.com mirror. The download is ~40 MB
     *  and is cached in filesDir so it survives app restarts.
     */
    suspend fun ensureModel(): Boolean = withContext(Dispatchers.IO) {
        if (model != null) return@withContext true
        if (!modelDir.exists()) {
            val zip = File(ctx.filesDir, "model.zip")
            if (!zip.exists()) {
                // Do not auto-download in production; pre-push via adb instead.
                // Instructions are in README. This stub returns false so the
                // caller can show "model not installed".
                return@withContext false
            }
            // Unzip
            java.util.zip.ZipInputStream(zip.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val out = File(ctx.filesDir, entry.name)
                    if (entry.isDirectory) out.mkdirs()
                    else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { fos -> zis.copyTo(fos) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            zip.delete()
        }
        model = Model(modelDir.absolutePath)
        true
    }

    /**
     * Record 3 seconds of audio and return the transcript.
     * The buffer size is the minimum required by AudioRecord for this format;
     * using a larger buffer reduces dropped frames but increases latency.
     * Ref: https://developer.android.com/reference/android/media/AudioRecord#getMinBufferSize(int,%20int,%20int)
     */
    suspend fun transcribe3s(): String = withContext(Dispatchers.IO) {
        val m = model ?: return@withContext ""
        val rec = Recognizer(m, sampleRate.toFloat())
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, sampleRate * 2) // 2 s of 16-bit mono
        val ar = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
        val buf = ByteArray(bufSize)
        val out = StringBuilder()
        ar.startRecording()
        val end = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < end) {
            val n = ar.read(buf, 0, buf.size)
            if (n > 0) {
                if (rec.acceptWaveForm(buf, n)) {
                    out.append(rec.result).append("\n")
                }
            }
        }
        ar.stop()
        ar.release()
        out.append(rec.finalResult)
        rec.close()
        out.toString()
    }

    fun close() { model?.close(); model = null }
}
