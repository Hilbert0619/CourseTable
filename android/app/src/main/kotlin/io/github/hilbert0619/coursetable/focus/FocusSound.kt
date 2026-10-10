package io.github.hilbert0619.coursetable.focus

import android.content.Context
import androidx.annotation.StringRes
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.hilbert0619.coursetable.R

/**
 * A background sound for focusing: a recorded loop shipped with the app (`sound-<id>.m4a` from CourseTable/Sounds, see
 * content/sounds/SOURCES.md). The ids are the iOS ones, so a saved choice reads the same.
 */
enum class FocusSound(val id: String, @param:StringRes val title: Int) {
    NONE("none", R.string.sound_none),
    RAIN("rain", R.string.sound_rain),
    ROOF_RAIN("roofRain", R.string.sound_roof_rain),
    CAMPFIRE("campfire", R.string.sound_campfire),
    AIRPLANE("airplane", R.string.sound_airplane),
    OCEAN("ocean", R.string.sound_ocean),
    STREAM("stream", R.string.sound_stream),
    NIGHT("night", R.string.sound_night),
    TRAIN("train", R.string.sound_train),
    CAFE("cafe", R.string.sound_cafe),
    ;

    /** The loop in the app's assets. */
    val uri: String get() = "asset:///sound-$id.m4a"

    companion object {
        fun of(id: String?): FocusSound? = entries.firstOrNull { it.id == id }
    }
}

/** Plays a sound in a loop; a stand-in in tests. */
interface SoundOutput {
    /** Plays [sound] at [volume] (0–1), or stops for [FocusSound.NONE]. */
    fun play(sound: FocusSound, volume: Float)

    fun setVolume(volume: Float)

    fun stop()
}

/**
 * Plays one sound in a seamless loop with Media3 ExoPlayer. It doesn't take audio focus, so it mixes with music (the
 * iOS `.mixWithOthers`), and holds a wake lock so it keeps playing with the screen off. Main thread only.
 */
class SoundPlayer(private val context: Context) : SoundOutput {
    private var player: ExoPlayer? = null
    private var playing = FocusSound.NONE

    override fun play(sound: FocusSound, volume: Float) {
        if (sound == FocusSound.NONE) return stop()
        val player = player ?: ExoPlayer.Builder(context).build().also { created ->
            val attributes = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
            created.setAudioAttributes(attributes, /* handleAudioFocus = */ false)
            created.setWakeMode(C.WAKE_MODE_LOCAL)
            created.repeatMode = Player.REPEAT_MODE_ONE
            player = created
        }
        player.volume = volume
        if (sound == playing && player.playWhenReady) return
        player.setMediaItem(MediaItem.fromUri(sound.uri))
        player.prepare()
        player.playWhenReady = true
        playing = sound
    }

    override fun setVolume(volume: Float) {
        player?.volume = volume
    }

    override fun stop() {
        player?.release()
        player = null
        playing = FocusSound.NONE
    }
}
