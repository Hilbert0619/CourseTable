package io.github.hilbert0619.coursetable.words

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Reads words aloud with the voices on the phone (iOS `Pronunciation`): no network, no audio files. The engine starts on
 * first use. Some phones have no English voice installed; [speak] then returns `false` so the app can say so.
 */
class Pronunciation(private val context: Context) {
    enum class Accent(val locale: Locale) {
        AMERICAN(Locale.US),
        BRITISH(Locale.UK),
    }

    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, Accent>? = null

    /** Speaks [text]; `false` if the phone has no voice for the accent (or no speech engine at all). */
    fun speak(text: String, accent: Accent = Accent.AMERICAN): Boolean {
        val engine = engine ?: TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            val waiting = pending
            pending = null
            if (ready && waiting != null) say(waiting.first, waiting.second)
        }.also { engine = it }
        if (!ready) {
            pending = text to accent
            return true
        }
        return say(text, accent)
    }

    private fun say(text: String, accent: Accent): Boolean {
        val engine = engine ?: return false
        val available = engine.isLanguageAvailable(accent.locale)
        if (available < TextToSpeech.LANG_AVAILABLE) return false
        engine.language = accent.locale
        engine.setSpeechRate(0.9f)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word")
        return true
    }

    fun shutdown() {
        engine?.shutdown()
        engine = null
        ready = false
    }
}
