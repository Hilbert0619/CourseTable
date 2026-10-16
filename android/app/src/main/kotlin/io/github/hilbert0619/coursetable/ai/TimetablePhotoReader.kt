package io.github.hilbert0619.coursetable.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.hilbert0619.aikit.AIAnswer
import io.github.hilbert0619.aikit.AIAnswerLanguage
import io.github.hilbert0619.aikit.AIImage
import io.github.hilbert0619.aikit.AIPrompts
import io.github.hilbert0619.aikit.TimetablePhoto
import io.github.hilbert0619.coursekit.Timetable
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reads a timetable from a picture (docs/ai-agent-spec.md §2, iOS `TimetablePhotoReader`): the picture goes to the
 * connection when it (or its photo model) reads pictures; otherwise the phone reads the text and where it sits, and the
 * text goes instead. Either way the answer is CSV for the import preview. Main thread only.
 */
class TimetablePhotoReader(private val scope: CoroutineScope) {
    enum class Phase {
        IDLE,

        /** Reading the text on this phone. */
        SCANNING,

        /** Waiting for the model. */
        ASKING,
        NO_TEXT,

        /** The phone can't read text (only 64-bit ARM phones have the recognizer). */
        NO_SCANNER,
        NO_TIMETABLE,
        DONE,
    }

    var phase by mutableStateOf(Phase.IDLE)
        private set
    val run = AIRun(scope)

    /** The last picture, for Try Again. */
    private var lastPhoto: Uri? = null

    val isWorking: Boolean get() = phase == Phase.SCANNING || phase == Phase.ASKING

    fun read(
        context: Context, photo: Uri, store: AIStore, timetable: Timetable, language: AIAnswerLanguage,
        onReading: (TimetablePhoto.Reading) -> Unit,
    ) {
        lastPhoto = photo
        run.reset()
        val connection = store.defaultConnection ?: return
        val key = store.key(connection)
        scope.launch {
            val pictures = connection.pictureConnection
            val jpeg = if (pictures != null) PhotoPrep.jpeg(context, photo) else null
            if (pictures != null && jpeg != null) {
                phase = Phase.ASKING
                val request = AIPrompts.timetableFromPhoto(AIImage(jpeg), null, timetable, language)
                run.start(request, store, pictures, key, onComplete = { finish(it, onReading) }, onEnd = {
                    // A model that turns out not to read pictures: read the text here instead.
                    if (run.state.error?.rejectsImages == true) readText(context, photo, store, connection, key, timetable, language, onReading)
                })
            } else {
                readText(context, photo, store, connection, key, timetable, language, onReading)
            }
        }
    }

    fun retry(context: Context, store: AIStore, timetable: Timetable, language: AIAnswerLanguage, onReading: (TimetablePhoto.Reading) -> Unit) {
        lastPhoto?.let { read(context, it, store, timetable, language, onReading) }
    }

    private fun readText(
        context: Context, photo: Uri, store: AIStore, connection: io.github.hilbert0619.aikit.AIConnection, key: String?,
        timetable: Timetable, language: AIAnswerLanguage, onReading: (TimetablePhoto.Reading) -> Unit,
    ) {
        run.reset()
        phase = Phase.SCANNING
        scope.launch {
            val boxes = try {
                TextReader.read(context, photo).boxes
            } catch (error: TextReader.Unavailable) {
                phase = Phase.NO_SCANNER
                return@launch
            } catch (error: Exception) {
                emptyList()
            }
            if (boxes.isEmpty()) {
                phase = Phase.NO_TEXT
                return@launch
            }
            phase = Phase.ASKING
            val request = AIPrompts.timetableFromPhoto(null, TimetablePhoto.ocrText(boxes), timetable, language)
            run.start(request, store, connection, key, onComplete = { finish(it, onReading) })
        }
    }

    private fun finish(answer: AIAnswer, onReading: (TimetablePhoto.Reading) -> Unit) {
        val reading = TimetablePhoto.reading(answer.text)
        if (reading == null) {
            phase = Phase.NO_TIMETABLE
            return
        }
        phase = Phase.DONE
        onReading(reading)
    }

    fun reset() {
        run.reset()
        phase = Phase.IDLE
    }
}

/** Pictures as they are sent: JPEG, at most 1600 pixels on the long side, turned upright. */
object PhotoPrep {
    const val MAX_PIXELS = 1600

    suspend fun jpeg(context: Context, photo: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decode(context, photo) ?: return@runCatching null
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
            bitmap.recycle()
            output.toByteArray()
        }.getOrNull()
    }

    private fun decode(context: Context, photo: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder applies the photo's orientation.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, photo)) { decoder, info, _ ->
                val scale = minOf(1.0, MAX_PIXELS.toDouble() / max(info.size.width, info.size.height).coerceAtLeast(1))
                decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(photo)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PIXELS) sample *= 2
        val bitmap = context.contentResolver.openInputStream(photo)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = minOf(1.0, MAX_PIXELS.toDouble() / max(bitmap.width, bitmap.height))
        if (scale >= 1.0) return bitmap
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
    }
}
