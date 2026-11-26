package io.github.jaswantk917.ghanti.ui.incall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import io.github.jaswantk917.ghanti.GhantiApp
import io.github.jaswantk917.ghanti.data.CallStyle
import io.github.jaswantk917.ghanti.incall.CallUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A contact's own photo, sharp enough to fill the call screen, and the colour the page takes from it. */
class CallPhoto(val image: ImageBitmap, val seed: PhotoColors.Seed)

/** Where the call screen stands on a photo: still reading it, none worth showing, or one to show. */
sealed interface PhotoState {
    data object Pending : PhotoState
    data object None : PhotoState
    class Ready(val photo: CallPhoto) : PhotoState
}

/**
 * The photo page's colours, the way Material You takes them from a wallpaper: the hue the photo's
 * colourful pixels share becomes a palette, and every role on the page is a tone of it.
 */
object PhotoColors {
    data class Seed(val hue: Float, val chroma: Float)

    /**
     * The hue most colourful pixels share, weighted by how colourful, from (hue, chroma, tone)
     * triples. Near-black and near-white pixels don't count; a grey photo gives a nearly grey page.
     */
    fun seed(hct: List<FloatArray>): Seed {
        val weight = FloatArray(BINS)
        val x = FloatArray(BINS)
        val y = FloatArray(BINS)
        val chromaSquared = FloatArray(BINS)
        for (pixel in hct) {
            val (hue, chroma, tone) = pixel
            if (chroma < MIN_CHROMA || tone < 12f || tone > 94f) continue
            val bin = (hue / 360f * BINS).toInt().mod(BINS)
            val angle = Math.toRadians(hue.toDouble())
            weight[bin] += chroma
            x[bin] += (cos(angle) * chroma).toFloat()
            y[bin] += (sin(angle) * chroma).toFloat()
            chromaSquared[bin] += chroma * chroma
        }
        fun near(bin: Int) = listOf((bin - 1).mod(BINS), bin, (bin + 1).mod(BINS))
        // a hue and its neighbours together, so a colour split across two bins still wins
        val best = (0 until BINS).maxBy { bin -> near(bin).sumOf { weight[it].toDouble() } }
        val total = near(best).sumOf { weight[it].toDouble() }.toFloat()
        if (total < hct.size * MIN_CHROMA * 0.04f) return GREY
        // the pixels' own hues averaged round the circle, so 355 and 5 meet at 0, not 180
        val hue = Math.toDegrees(atan2(near(best).sumOf { y[it].toDouble() }, near(best).sumOf { x[it].toDouble() })).toFloat().mod(360f)
        return Seed(hue, near(best).sumOf { chromaSquared[it].toDouble() }.toFloat() / total)
    }

    /**
     * Every role is a tone of the photo's hue, so text and buttons keep their contrast whatever the
     * photo: tone is lightness, and the pairs sit far apart (12 on 91, 98 on 34; 92 on 8, 18 on 82).
     */
    fun scheme(seed: Seed, dark: Boolean): ColorScheme {
        val h = seed.hue
        val c = seed.chroma
        val grey = c < MIN_CHROMA
        fun of(chroma: Float, tone: Int, hue: Float = h) = hct(hue, chroma, tone)
        val accent = if (grey) 6f else c.coerceIn(28f, 52f)
        // the page itself carries the photo's colour, as on the player this page follows
        val page = if (grey) 3f else (c * 0.5f).coerceIn(10f, 32f)
        val pill = if (grey) 4f else (c * 0.6f).coerceIn(12f, 38f)
        val ink = if (grey) 2f else (c * 0.3f).coerceIn(6f, 18f)
        val third = if (grey) 6f else (c * 0.5f).coerceIn(16f, 32f)
        val turn = h + 60f
        return if (dark) {
            val night = page * 0.6f
            val nightPill = pill * 0.6f
            darkColorScheme(
                primary = of(accent, 82), onPrimary = of(accent, 18), primaryContainer = of(accent, 30), onPrimaryContainer = of(accent, 90),
                inversePrimary = of(accent, 40),
                secondary = of(pill, 80), onSecondary = of(pill, 20), secondaryContainer = of(pill, 30), onSecondaryContainer = of(pill, 90),
                tertiary = of(third, 80, turn), onTertiary = of(third, 20, turn), tertiaryContainer = of(third, 30, turn), onTertiaryContainer = of(third, 90, turn),
                background = of(night, 8), onBackground = of(ink, 92),
                surface = of(night, 8), onSurface = of(ink, 92),
                surfaceVariant = of(nightPill, 28), onSurfaceVariant = of(ink, 78),
                surfaceTint = of(accent, 82), inverseSurface = of(ink, 90), inverseOnSurface = of(ink, 16),
                outline = of(nightPill, 58), outlineVariant = of(nightPill, 30),
                surfaceBright = of(night, 22), surfaceDim = of(night, 6),
                surfaceContainerLowest = of(night, 4), surfaceContainerLow = of(night, 11), surfaceContainer = of(night, 13),
                surfaceContainerHigh = of(nightPill, 20), surfaceContainerHighest = of(nightPill, 25),
            )
        } else {
            lightColorScheme(
                primary = of(accent, 34), onPrimary = of(accent * 0.3f, 98), primaryContainer = of(accent, 82), onPrimaryContainer = of(accent, 14),
                inversePrimary = of(accent, 80),
                secondary = of(pill, 40), onSecondary = of(pill, 100), secondaryContainer = of(pill, 84), onSecondaryContainer = of(pill, 14),
                tertiary = of(third, 40, turn), onTertiary = of(third, 100, turn), tertiaryContainer = of(third, 90, turn), onTertiaryContainer = of(third, 10, turn),
                background = of(page, 91), onBackground = of(ink, 12),
                surface = of(page, 91), onSurface = of(ink, 12),
                surfaceVariant = of(pill, 84), onSurfaceVariant = of(ink, 32),
                surfaceTint = of(accent, 34), inverseSurface = of(ink, 20), inverseOnSurface = of(ink, 94),
                outline = of(pill, 48), outlineVariant = of(pill, 76),
                surfaceBright = of(page, 96), surfaceDim = of(page, 84),
                surfaceContainerLowest = of(page, 98), surfaceContainerLow = of(page, 93), surfaceContainer = of(page, 89),
                surfaceContainerHigh = of(pill, 84), surfaceContainerHighest = of(pill, 80),
            )
        }
    }

    private fun hct(hue: Float, chroma: Float, tone: Int) = Color(ColorUtils.M3HCTToColor(hue.mod(360f), chroma, tone.toFloat()))

    /**
     * the smallest full photo the page takes. Contacts keep full photos at up to 720 px, but Google's
     * synced ones mostly come down at 512 (480 for some): stretched across the screen they're a little
     * soft, which the user chose over leaving them classic. Smaller ones and the 96 px thumbnails stay classic
     */
    const val MIN_SIDE = 480
    private const val BINS = 36
    internal const val MIN_CHROMA = 12f
    internal val GREY = Seed(60f, 4f)
}

/** Full photos, read once per call and kept for the next few, so the call screen never waits on one. */
object CallPhotos {
    private val cache = LruCache<Uri, PhotoState>(4)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** as a call arrives, before its screen opens */
    fun preload(context: Context, uri: Uri?) {
        if (uri == null || cache.get(uri) != null || GhantiApp.instance.prefs.settings.value.callStyle != CallStyle.Photo) return
        val app = context.applicationContext
        scope.launch { cache.put(uri, read(app, uri)) }
    }

    fun cached(uri: Uri): PhotoState? = cache.get(uri)

    suspend fun load(context: Context, uri: Uri): PhotoState = cache.get(uri) ?: withContext(Dispatchers.IO) { read(context, uri) }.also { cache.put(uri, it) }

    private fun read(context: Context, uri: Uri): PhotoState = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (min(bounds.outWidth, bounds.outHeight) < PhotoColors.MIN_SIDE) return@runCatching PhotoState.None
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= LONGEST) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return@runCatching PhotoState.None
        PhotoState.Ready(CallPhoto(bitmap.asImageBitmap(), seedOf(bitmap)))
    }.getOrDefault(PhotoState.None)

    fun seedOf(bitmap: Bitmap): PhotoColors.Seed {
        val small = Bitmap.createScaledBitmap(bitmap, SAMPLE, SAMPLE, true)
        val pixels = IntArray(SAMPLE * SAMPLE).also { small.getPixels(it, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE) }
        return PhotoColors.seed(pixels.map { FloatArray(3).also { out -> ColorUtils.colorToM3HCT(it, out) } })
    }

    private const val LONGEST = 1440
    private const val SAMPLE = 48
}

/** the photo for this call's screen, when the photo page is chosen and the contact has one sharp enough */
@Composable
fun rememberCallPhoto(call: CallUi, style: CallStyle): PhotoState {
    val uri = call.person?.fullPhoto?.takeIf { style == CallStyle.Photo && call.participants.isEmpty() } ?: return PhotoState.None
    val context = LocalContext.current
    val state by produceState(CallPhotos.cached(uri) ?: PhotoState.Pending, uri) { value = CallPhotos.load(context, uri) }
    return state
}

/** the photo page's colours over the app's own type, shapes and motion; with AMOLED on, the page is black at night */
@Composable
fun PhotoTheme(seed: PhotoColors.Seed, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val amoled = GhantiApp.instance.prefs.settings.collectAsState().value.amoled
    val scheme = remember(seed, dark, amoled) {
        PhotoColors.scheme(seed, dark).let { if (dark && amoled) it.copy(surface = Color.Black, background = Color.Black) else it }
    }
    MaterialTheme(colorScheme = scheme, motionScheme = MaterialTheme.motionScheme, shapes = MaterialTheme.shapes, typography = MaterialTheme.typography) {
        // as GhantiTheme does: bare text takes the page's own ink, not the app's
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
    }
}
