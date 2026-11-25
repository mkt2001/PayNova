package com.paynova.upi.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.paynova.upi.data.SettingsStore
import java.util.Locale

/** Android TextToSpeech wrapper. Swap the engine here later for a licensed higher-quality voice. */
class Announcer(ctx: Context, private val s: SettingsStore) : TextToSpeech.OnInitListener {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext, this)

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
    }

    fun voices(lang: String): List<Voice> {
        if (!ready) return emptyList()
        return tts.voices.orEmpty()
            .filter { it.locale.language == lang && !it.isNetworkConnectionRequired }
            .sortedBy { it.name }
    }

    fun announceSuccess(amount: String, recipient: String) {
        val hi = s.ttsLang == "hi"
        val text = if (hi) "आपका भुगतान सफल रहा। आपने $amount रुपये $recipient को भेजे हैं।"
        else "Payment successful. You have paid $amount rupees to $recipient."
        speak(text)
    }

    fun test() {
        val hi = s.ttsLang == "hi"
        speak(if (hi) "आपका भुगतान सफल रहा। यह एक परीक्षण है।" else "Payment successful. This is a voice test.", force = true)
    }

    private fun speak(text: String, force: Boolean = false) {
        if (!ready || (!s.ttsEnabled && !force)) return
        val loc = if (s.ttsLang == "hi") Locale("hi", "IN") else Locale("en", "IN")
        tts.setLanguage(loc)
        val vs = voices(s.ttsLang)
        if (vs.isNotEmpty()) tts.setVoice(vs[s.ttsVoice.coerceIn(0, vs.size - 1)])
        tts.setSpeechRate(s.ttsRate)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, s.ttsVol) }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "pn-" + System.nanoTime())
    }
}
