package com.mrrob.llmchat.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Thin wrapper around [SpeechRecognizer] that keeps a single recognizer
 * alive for the lifetime of the voice screen and forwards events as
 * plain callbacks. Results are emitted through:
 *  - [onPartial] while the user is still talking,
 *  - [onFinal] once an utterance is finalized (empty string = nothing heard),
 *  - [onListeningStarted] when the mic session actually begins,
 *  - [onError] for unrecoverable recognition errors.
 */
class SpeechRecognitionManager(private val context: Context) : RecognitionListener {

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    private var onPartial: (String) -> Unit = {}
    private var onFinal: (String) -> Unit = {}
    private var onListeningStarted: () -> Unit = {}
    private var onError: (String) -> Unit = {}

    val isListening: Boolean get() = listening

    fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onListeningStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this device.")
            return
        }
        this.onPartial = onPartial
        this.onFinal = onFinal
        this.onListeningStarted = onListeningStarted
        this.onError = onError

        val engine = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        engine.setRecognitionListener(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        listening = true
        engine.startListening(intent)
        onListeningStarted()
    }

    /** Ends the current mic session; a final result (or empty result) still arrives. */
    fun stopListening() {
        if (!listening) return
        listening = false
        recognizer?.stopListening()
    }

    fun destroy() {
        listening = false
        recognizer?.destroy()
        recognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) {}

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {}

    override fun onError(error: Int) {
        listening = false
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> onFinal("")
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                onError("Microphone permission is required for voice chat.")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                onError("The recognizer is busy. Please try again.")
            else -> onError("Voice recognition failed (error $error).")
        }
    }

    override fun onResults(results: Bundle?) {
        listening = false
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        onFinal(text)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        // Partial results arrive under the same key as final results.
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (text.isNotBlank()) onPartial(text)
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
