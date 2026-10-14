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
class OfflineGuide(private val ctx: Context, private val book: RecipeBook, private val pointer: Pointer, private val appLang: com.r17e.guide.i18n.Lang) {
    private val main = Handler(Looper.getMainLooper())
    /** Recipe text exists in en/hi; Hinglish users hear the Hindi recipe lines. */
    private val lang = if (appLang == com.r17e.guide.i18n.Lang.EN) "en" else "hi"
    private fun s(k: String) = com.r17e.guide.i18n.S.get(appLang, k)
    private var tts: TextToSpeech? = null
    private var stt: SpeechRecognizer? = null
    private var runner: RecipeRunner? = null
    private val poll = object : Runnable {
        override fun run() { tick(); main.postDelayed(this, 600) }
    }

    fun start() {
        tts = TextToSpeech(ctx) { ok ->
            if (ok == TextToSpeech.SUCCESS) {
                tts?.language = Locale.forLanguageTag(appLang.ttsTag)
                say(s("offline_hello"))
                main.postDelayed({ listen() }, 4500)
            }
        }
    }

    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return
        stt?.destroy()
        stt = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) =
                    onHeard(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
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
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, appLang.ttsTag)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
        }
    }

    /** Speech results (or a typed debug turn): pick a recipe and start following the screen. */
    fun onHeard(heard: List<String>) {
        val recipe = heard.firstNotNullOfOrNull { book.match(it) }
        if (com.r17e.guide.BuildConfig.DEBUG) android.util.Log.i("R17E_OFFLINE", "heard=$heard recipe=${recipe?.id}")
        if (recipe == null) { say(s("offline_sorry")); main.postDelayed({ listen() }, 3000); return }
        stt?.cancel()
        runner = RecipeRunner(recipe)
        say(recipe.title[lang] ?: recipe.title.values.first())
        main.removeCallbacks(poll); main.post(poll)
    }

    private fun tick() {
        val r = runner ?: return
        val snap = GuideAccessibilityService.instance?.current?.let(SafetyPolicy::filter) ?: return
        val g = r.onSnapshot(snap) ?: return
        if (SafetyPolicy.vet(snap, g.node) is SafetyPolicy.Verdict.Deny) { pointer.clear(); return }
        val line = g.say[lang] ?: g.say.values.first()
        android.util.Log.i("R17E_OFFLINE", "step ${g.step} -> ${g.node.id} ${g.node.label}")
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
