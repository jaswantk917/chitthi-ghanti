package io.github.jaswantk917.tring.recorder

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Whisper's model, fetched when asked for (574 MB, from whisper.cpp's own copies on Hugging Face)
 * and checked against its known digest before it's used; and the speech detector it runs with,
 * small enough to come in the app. A download cut short picks up where it stopped.
 */
object WhisperModel {
    private const val NAME = "ggml-large-v3-turbo-q5_0.bin"
    private const val ADDRESS = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$NAME"
    const val SIZE = 574_041_195L
    private const val SHA256 = "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2"
    private const val VAD = "ggml-silero-v5.1.2.bin"

    sealed interface State {
        data object Missing : State
        data class Downloading(val done: Long) : State
        data object Checking : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Missing)
    val state: StateFlow<State> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private fun dir(context: Context) = File(context.getExternalFilesDir(null) ?: context.filesDir, "whisper").apply { mkdirs() }

    fun file(context: Context) = File(dir(context), NAME)

    private fun part(context: Context) = File(dir(context), "$NAME.part")

    /** only a checked download is ever renamed into place */
    fun ready(context: Context) = file(context).length() == SIZE

    /** the speech detector, copied out of the app the first time: whisper.cpp reads it by path */
    fun vad(context: Context): File {
        val file = File(dir(context), VAD)
        if (!file.exists()) {
            val temp = File(dir(context), "$VAD.part")
            context.assets.open("whisper/$VAD").use { input -> temp.outputStream().use { input.copyTo(it) } }
            temp.renameTo(file)
        }
        return file
    }

    fun refresh(context: Context) {
        if (job?.isActive == true) return
        _state.value = if (ready(context)) State.Ready else State.Missing
    }

    fun download(context: Context) {
        if (job?.isActive == true || ready(context)) return
        val app = context.applicationContext
        job = scope.launch {
            try {
                fetch(app)
                _state.value = State.Checking
                val digest = sha256(part(app))
                if (digest != SHA256) {
                    part(app).delete()
                    error("The download came out damaged. Try again.")
                }
                part(app).renameTo(file(app))
                _state.value = State.Ready
            } catch (e: CancellationException) {
                _state.value = State.Missing
                throw e
            } catch (e: Exception) {
                _state.value = State.Failed(e.message ?: "The download failed. Try again.")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = State.Missing
    }

    fun delete(context: Context) {
        job?.cancel()
        file(context).delete()
        part(context).delete()
        _state.value = State.Missing
    }

    private suspend fun fetch(context: Context) {
        val part = part(context)
        var done = part.length().takeIf { it < SIZE } ?: 0L
        if (done == 0L) part.delete()
        if (dir(context).usableSpace < SIZE - done) error("Needs ${SIZE / 1_000_000} MB free on the phone.")
        _state.value = State.Downloading(done)
        val connection = (URL(ADDRESS).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            if (done > 0) setRequestProperty("Range", "bytes=$done-")
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> Unit
                // the server sent all of it, not the rest: start the file again
                HttpURLConnection.HTTP_OK -> done = 0
                else -> error("The download failed (${connection.responseCode}). Try again.")
            }
            connection.inputStream.use { input ->
                FileOutputStream(part, done > 0).use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var shown = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (done - shown >= 1_000_000) {
                            shown = done
                            _state.value = State.Downloading(done)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (done != SIZE) error("The download stopped early. Try again to pick it up.")
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
