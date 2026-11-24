package io.github.jaswantk917.tring.recorder

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** 16 kHz mono PCM, the format of the call line. */
object Pcm {
    const val RATE = 16_000

    class Wav(val rate: Int, val samples: ShortArray)

    /** the first channel of a 16-bit WAV, wherever its chunks sit; null for anything else */
    fun readWav(bytes: ByteArray): Wav? {
        if (bytes.size < 44 || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var at = 12
        var rate = 0
        var channels = 0
        while (at + 8 <= bytes.size) {
            val name = String(bytes, at, 4, Charsets.US_ASCII)
            val size = data.getInt(at + 4)
            if (size < 0) return null
            val body = at + 8
            if (name == "fmt " && body + 16 <= bytes.size) {
                if (data.getShort(body + 14).toInt() != 16) return null
                channels = data.getShort(body + 2).toInt()
                rate = data.getInt(body + 4)
            } else if (name == "data") {
                if (rate <= 0 || channels <= 0) return null
                val end = min(body.toLong() + size, bytes.size.toLong()).toInt()
                val count = (end - body) / (2 * channels)
                return Wav(rate, ShortArray(count) { data.getShort(body + it * 2 * channels) })
            }
            at = body + size + (size and 1)
        }
        return null
    }

    fun resample(samples: ShortArray, from: Int, to: Int = RATE): ShortArray {
        if (from == to || samples.isEmpty()) return samples
        val out = ShortArray((samples.size.toLong() * to / from).toInt())
        for (i in out.indices) {
            val position = i.toDouble() * from / to
            val left = position.toInt().coerceAtMost(samples.lastIndex)
            val right = (left + 1).coerceAtMost(samples.lastIndex)
            val part = position - left
            out[i] = (samples[left] * (1 - part) + samples[right] * part).toInt().toShort()
        }
        return out
    }

    fun silence(ms: Int) = ShortArray(RATE * ms / 1_000)

    /** a soft-edged 1 kHz beep, so it doesn't click on the phone line */
    fun beep(ms: Int = 350): ShortArray {
        val count = RATE * ms / 1_000
        val edge = RATE / 100
        return ShortArray(count) { i ->
            val fade = minOf(1.0, i.toDouble() / edge, (count - i).toDouble() / edge)
            (BEEP_LEVEL * fade * sin(2 * PI * 1_000 * i / RATE)).toInt().toShort()
        }
    }

    fun bytes(samples: ShortArray): ByteArray {
        val out = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        out.asShortBuffer().put(samples)
        return out.array()
    }

    private const val BEEP_LEVEL = 8_000
}
