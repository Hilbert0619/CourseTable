package io.github.hilbert0619.coursetable

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.hilbert0619.coursekit.FocusWallpaper
import io.github.hilbert0619.coursetable.focus.ShownWallpaper

/** The screenshots' wallpaper (iOS `DemoWallpaper`): a drawn dusk over hills, so they never depend on the network. */
object DemoWallpaper {
    val wallpaper = FocusWallpaper(
        id = "demo", url = "https://example.com/demo.jpg", width = 1290, height = 2796, color = "#3b4a6b", focusY = 0.5,
        author = "Demo Photographer", sourceUrl = "https://unsplash.com", licence = "Unsplash License",
    )

    val image: ImageBitmap by lazy { draw() }

    val shown: ShownWallpaper get() = ShownWallpaper(wallpaper, image)

    private fun rgb(red: Double, green: Double, blue: Double, alpha: Double = 1.0): Int =
        android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

    private fun draw(): ImageBitmap {
        val scale = 2f
        val width = 430 * scale
        val height = 932 * scale
        val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(rgb(0.13, 0.17, 0.36), rgb(0.55, 0.42, 0.62), rgb(0.98, 0.66, 0.48)), floatArrayOf(0f, 0.55f, 0.8f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
        listOf(0.32, 0.22, 0.12).forEachIndexed { index, shade ->
            val base = height * (0.62f + 0.08f * index)
            val hill = Path().apply {
                moveTo(0f, base)
                cubicTo(width * 0.3f, base - 120 * scale, width * 0.6f, base + 60 * scale, width, base - 40 * scale + index * 30 * scale)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            paint.color = rgb(shade * 0.6, shade * 0.7, shade)
            canvas.drawPath(hill, paint)
        }
        // The moon, low enough to show in the countdown card's crop.
        paint.color = rgb(1.0, 1.0, 1.0, 0.9)
        canvas.drawCircle(width * 0.7f + 15 * scale, height * 0.36f + 15 * scale, 15 * scale, paint)
        return bitmap.asImageBitmap()
    }
}
