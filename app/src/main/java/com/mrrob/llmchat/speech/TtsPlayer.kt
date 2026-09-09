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
    private var activeDone: (() -> Unit)? = null

    /** Speaking speed multiplier from Voice settings. */
    @Volatile
    var rate: Float = 1.1f

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(rate)
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
                pendingText?.let { (text, done) ->
                    pendingText = null
                    speak(text, done)
                }
            }
        }
    }

    private var pendingText: Pair<String, () -> Unit>? = null

    fun speak(text: String, onDone: () -> Unit = {}) {
        val engine = tts
        if (!ready || engine == null || text.isBlank()) {
            onDone()
            return
        }
        activeDone = onDone
        engine.setSpeechRate(rate)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply_${utteranceCounter.incrementAndGet()}")
    }

    fun stop() {
        tts?.stop()
        finishActive()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        activeDone = null
    }

    private fun finishActive() {
        val done = activeDone
        activeDone = null
        done?.invoke()
    }
}
