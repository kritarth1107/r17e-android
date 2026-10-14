package com.r17e.guide.offline

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.r17e.guide.a11y.GuideAccessibilityService
import com.r17e.guide.recipes.RecipeBook
import com.r17e.guide.recipes.RecipeRunner
import com.r17e.guide.safety.SafetyPolicy
import com.r17e.guide.session.Pointer
import java.util.Locale

/**
 * Offline fallback when Gemini Live is unreachable: on-device speech recognition picks a recipe,
 * then the runner follows the screen and points at each step while TTS speaks it. Main thread only.
 */
class OfflineGuide(private val ctx: Context, private val book: RecipeBook, private val pointer: Pointer) {
    private val main = Handler(Looper.getMainLooper())
    private val lang = if (Locale.getDefault().language == "hi") "hi" else "en"
    private var tts: TextToSpeech? = null
    private var stt: SpeechRecognizer? = null
    private var runner: RecipeRunner? = null
    private val poll = object : Runnable {
        override fun run() { tick(); main.postDelayed(this, 600) }
    }

    fun start() {
        tts = TextToSpeech(ctx) { ok ->
            if (ok == TextToSpeech.SUCCESS) {
                tts?.language = Locale.forLanguageTag(if (lang == "hi") "hi-IN" else "en-IN")
                say(if (lang == "hi") "इंटरनेट वाला सहायक अभी नहीं है। बताइए, क्या करना है? जैसे वीडियो कॉल।"
                    else "The online helper is not available. Tell me what you want to do, like video call.")
                main.postDelayed({ listen() }, 4500)
            }
        }
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return
        stt?.destroy()
        stt = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val recipe = heard.firstNotNullOfOrNull { book.match(it) }
                    if (recipe == null) { say(if (lang == "hi") "माफ़ कीजिए, समझ नहीं आया। फिर से बोलिए।" else "Sorry, I did not get that. Please say it again."); main.postDelayed({ listen() }, 3000); return }
                    runner = RecipeRunner(recipe)
                    say(recipe.title[lang] ?: recipe.title.values.first())
                    main.removeCallbacks(poll); main.post(poll)
                }
                override fun onError(error: Int) { main.postDelayed({ listen() }, 2000) }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == "hi") "hi-IN" else "en-IN")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
        }
    }

    private fun tick() {
        val r = runner ?: return
        val snap = GuideAccessibilityService.instance?.current?.let(SafetyPolicy::filter) ?: return
        val g = r.onSnapshot(snap) ?: return
        if (SafetyPolicy.vet(snap, g.node) is SafetyPolicy.Verdict.Deny) { pointer.clear(); return }
        val line = g.say[lang] ?: g.say.values.first()
        pointer.point(snap, g.node.id, line)
        say(line)
    }

    private fun say(t: String) { tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "r17e") }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        stt?.destroy(); stt = null
        tts?.shutdown(); tts = null
        runner = null; pointer.clear()
    }
}
