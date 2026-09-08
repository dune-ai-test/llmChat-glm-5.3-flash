package com.mrrob.llmchat.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Small TextToSpeech wrapper: flushes any queued utterance before speaking
 * the newest reply and guarantees the [onDone] callback fires exactly once,
 * whether the utterance completes, errors, or TTS itself never initializes.
 */
class TtsPlayer(context: Context) {

    private val utteranceCounter = AtomicLong()

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null
    private var pendingDone: (() -> Unit)? = null
    private var activeDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        finishActive()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        finishActive()
                    }
                })
                val text = pendingText
                val done = pendingDone
                pendingText = null
                pendingDone = null
                if (text != null) speak(text, done ?: {})
            }
        }
    }

    fun speak(text: String, onDone: () -> Unit) {
        val engine = tts
        if (!ready || engine == null || text.isBlank()) {
            onDone()
            return
        }
        activeDone = onDone
        engine.language = Locale.getDefault()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply_${utteranceCounter.incrementAndGet()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        finishActive()
    }

    private fun finishActive() {
        val done = activeDone
        activeDone = null
        done?.invoke()
    }
}
