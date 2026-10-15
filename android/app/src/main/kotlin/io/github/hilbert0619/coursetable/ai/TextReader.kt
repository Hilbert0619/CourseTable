package io.github.hilbert0619.coursetable.ai

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import io.github.hilbert0619.aikit.TimetablePhoto
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * On-device text recognition (ML Kit with its Chinese and Latin model inside the app: no network, no Google Play
 * services). Built for 64-bit ARM phones only (`packaging` in `app/build.gradle.kts`); elsewhere it reports
 * [Unavailable].
 */
object TextReader {
    /** What a picture holds: its lines in reading order, and each line's box as fractions of the picture. */
    data class Reading(val lines: List<String>, val boxes: List<TimetablePhoto.TextBox>) {
        val text: String get() = lines.joinToString("\n")
    }

    /** The phone can't recognise text (the recogniser isn't built for its processor). */
    class Unavailable(cause: Throwable) : Exception(cause)

    suspend fun read(context: Context, picture: Uri): Reading = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, picture)
        val recognizer = try {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        } catch (error: Throwable) {
            Log.w(TAG, "No text recognizer", error)
            throw Unavailable(error)
        }
        try {
            val result = suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            val width = image.width.toDouble().coerceAtLeast(1.0)
            val height = image.height.toDouble().coerceAtLeast(1.0)
            val lines = result.textBlocks.flatMap { it.lines }
            Reading(
                lines = lines.map { it.text },
                boxes = lines.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    TimetablePhoto.TextBox(line.text, box.left / width, box.top / height, box.width() / width, box.height() / height)
                },
            )
        } catch (error: UnsatisfiedLinkError) {
            throw Unavailable(error)
        } finally {
            recognizer.close()
        }
    }

    private const val TAG = "TextReader"
}
