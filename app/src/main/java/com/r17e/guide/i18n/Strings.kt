package com.r17e.guide.i18n

import android.content.Context
import java.util.Locale

enum class Lang(val label: String, val ttsTag: String, val promptName: String) {
    EN("English", "en-IN", "English"),
    HI("हिन्दी", "hi-IN", "Hindi (Devanagari)"),
    HINGLISH("Hinglish", "hi-IN", "Hinglish (Hindi in Latin script mixed with English)"),
}

/** User preferences (language, onboarding done). */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("r17e_prefs", Context.MODE_PRIVATE)
    var lang: Lang
        get() = p.getString("lang", null)?.let { runCatching { Lang.valueOf(it) }.getOrNull() }
            ?: if (Locale.getDefault().language == "hi") Lang.HI else Lang.EN
        set(v) { p.edit().putString("lang", v.name).apply() }
    /** Name of the linked family group, or null. */
    var familyGroup: String?
        get() = p.getString("family_group", null)
        set(v) { p.edit().putString("family_group", v).apply() }
    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) { p.edit().putBoolean("onboarded", v).apply() }
}

/** All user-facing text in English, Hindi and Hinglish. Kept in code so Hinglish is a first-class choice. */
object S {
    private val t: Map<String, Triple<String, String, String>> = mapOf(
        "app" to Triple("R17E Helper", "R17E सहायक", "R17E Helper"),
        "tagline" to Triple("I show you where to tap, step by step.", "मैं आपको कदम-कदम पर बताता हूँ कि कहाँ दबाना है।", "Main aapko step by step dikhata hoon ki kahan tap karna hai."),
        "choose_lang" to Triple("Choose your language", "अपनी भाषा चुनें", "Apni bhasha chuniye"),
        "next" to Triple("Next", "आगे", "Aage"),
        "back" to Triple("Back", "पीछे", "Peeche"),
        "skip" to Triple("Skip", "छोड़ें", "Skip karein"),
        "done" to Triple("Done", "हो गया", "Ho gaya"),
        "how_title" to Triple("How it works", "यह कैसे काम करता है", "Yeh kaise kaam karta hai"),
        "how_1" to Triple("Tell me what you want to do, like \"video call my son on WhatsApp\".", "बताइए आपको क्या करना है, जैसे \"बेटे को व्हाट्सऐप पर वीडियो कॉल\"।", "Bataiye kya karna hai, jaise \"bete ko WhatsApp pe video call\"."),
        "how_2" to Triple("I draw a yellow circle around the right button and tell you what to do.", "मैं सही बटन के चारों ओर पीला घेरा बनाता हूँ और बताता हूँ क्या करना है।", "Main sahi button ke around peela gola banata hoon aur batata hoon kya karna hai."),
        "how_3" to Triple("I only show you. I never tap, type or change anything for you.", "मैं सिर्फ़ दिखाता हूँ। मैं खुद कभी कुछ नहीं दबाता या बदलता।", "Main sirf dikhata hoon. Main khud kabhi tap ya type nahi karta."),
        "privacy" to Triple("When the helper is on, your voice and the words on your screen go to Google Gemini so it can answer. Passwords, PINs and OTPs are hidden first.", "सहायक चालू होने पर आपकी आवाज़ और स्क्रीन के शब्द Google Gemini को भेजे जाते हैं। पासवर्ड, PIN और OTP पहले छिपा दिए जाते हैं।", "Helper on hone par aapki awaaz aur screen ke words Google Gemini ko jaate hain. Password, PIN aur OTP pehle chhupa diye jaate hain."),
        "agree" to Triple("I understand", "मैं समझ गया", "Samajh gaya"),
        "mic_title" to Triple("Allow the microphone", "माइक की अनुमति दें", "Mic ki permission dijiye"),
        "mic_body" to Triple("So I can hear you. I only listen while the helper is on.", "ताकि मैं आपकी बात सुन सकूँ। मैं सिर्फ़ सहायक चालू होने पर सुनता हूँ।", "Taaki main aapki baat sun sakoon. Sirf helper on hone par sunta hoon."),
        "allow" to Triple("Allow", "अनुमति दें", "Allow karein"),
        "granted" to Triple("Done ✓", "हो गया ✓", "Ho gaya ✓"),
        "notif_title" to Triple("Allow notifications", "सूचनाओं की अनुमति दें", "Notifications allow karein"),
        "notif_body" to Triple("A small notice shows while I am listening, with a Stop button.", "जब मैं सुन रहा होता हूँ, एक छोटी सूचना दिखती है जिसमें रोकने का बटन होता है।", "Jab main sun raha hoon, ek chhota notice dikhta hai jisme Stop button hota hai."),
        "a11y_title" to Triple("Turn on screen helper", "स्क्रीन सहायक चालू करें", "Screen helper on karein"),
        "a11y_body" to Triple("Tap the button, find \"R17E Guide\" under Installed apps or Downloaded apps, and switch it on.", "बटन दबाएँ, \"Installed apps\" या \"Downloaded apps\" में \"R17E Guide\" खोजें और चालू करें।", "Button dabaiye, \"Installed apps\" ya \"Downloaded apps\" mein \"R17E Guide\" dhoondhiye aur on kijiye."),
        "a11y_restricted" to Triple("If it is greyed out: open App info for R17E, tap ⋮ (top right), choose \"Allow restricted settings\", then try again.", "अगर यह धुंधला है: R17E की App info खोलें, ऊपर दाईं ओर ⋮ दबाएँ, \"Allow restricted settings\" चुनें, फिर दोबारा करें।", "Agar yeh grey hai: R17E ki App info kholiye, upar ⋮ dabaiye, \"Allow restricted settings\" chuniye, phir dobara try kijiye."),
        "open_settings" to Triple("Open settings", "सेटिंग्स खोलें", "Settings kholiye"),
        "open_app_info" to Triple("Open App info", "App info खोलें", "App info kholiye"),
        "ready_title" to Triple("All set!", "सब तैयार है!", "Sab ready hai!"),
        "ready_body" to Triple("Tap the big button, or the round helper button on any screen, and tell me what you want to do.", "बड़ा बटन दबाएँ, या किसी भी स्क्रीन पर गोल सहायक बटन दबाएँ, और बताइए क्या करना है।", "Bada button dabaiye, ya kisi bhi screen pe gol helper button dabaiye, aur bataiye kya karna hai."),
        "start" to Triple("Start helper", "सहायक शुरू करें", "Helper shuru karein"),
        "stop" to Triple("Stop helper", "सहायक बंद करें", "Helper band karein"),
        "st_stopped" to Triple("Helper is off", "सहायक बंद है", "Helper band hai"),
        "st_connecting" to Triple("Getting ready…", "तैयार हो रहा है…", "Ready ho raha hai…"),
        "st_listening" to Triple("I am listening. Tell me what you want to do.", "मैं सुन रहा हूँ। बताइए क्या करना है।", "Main sun raha hoon. Bataiye kya karna hai."),
        "st_speaking" to Triple("Speaking…", "बोल रहा हूँ…", "Bol raha hoon…"),
        "st_reconnecting" to Triple("Connection dropped. Reconnecting…", "कनेक्शन टूटा। फिर से जोड़ रहा हूँ…", "Connection toota. Phir se jod raha hoon…"),
        "st_offline" to Triple("No internet helper right now. Using simple offline help.", "अभी इंटरनेट वाला सहायक नहीं है। सरल ऑफ़लाइन मदद चालू है।", "Abhi internet wala helper nahi hai. Simple offline help chal rahi hai."),
        "screen_off" to Triple("Screen helper is off. I can talk but cannot point.", "स्क्रीन सहायक बंद है। मैं बात कर सकता हूँ पर दिखा नहीं सकता।", "Screen helper band hai. Main baat kar sakta hoon par dikha nahi sakta."),
        "turn_on" to Triple("Turn on", "चालू करें", "On karein"),
        "mic_needed" to Triple("Microphone permission is needed.", "माइक की अनुमति चाहिए।", "Mic ki permission chahiye."),
        "try_saying" to Triple("Try saying", "ऐसे बोलकर देखें", "Aise bol ke dekhiye"),
        "ex_1" to Triple("\"How do I video call on WhatsApp?\"", "\"व्हाट्सऐप पर वीडियो कॉल कैसे करूँ?\"", "\"WhatsApp pe video call kaise karun?\""),
        "ex_2" to Triple("\"Make the sound louder\"", "\"आवाज़ बढ़ानी है\"", "\"Awaaz badhani hai\""),
        "ex_3" to Triple("\"Is this message a scam?\"", "\"क्या यह मैसेज धोखा है?\"", "\"Kya yeh message fraud hai?\""),
        "language" to Triple("Language", "भाषा", "Bhasha"),
        "notif_listening" to Triple("R17E helper is listening", "R17E सहायक सुन रहा है", "R17E helper sun raha hai"),
        "notif_text" to Triple("Talk to me. I will show you where to tap.", "बोलिए। मैं दिखाऊँगा कहाँ दबाना है।", "Boliye. Main dikhaunga kahan tap karna hai."),
        "offline_hello" to Triple("The online helper is not available. Tell me what you want to do, like video call.", "इंटरनेट वाला सहायक अभी नहीं है। बताइए, क्या करना है? जैसे वीडियो कॉल।", "Online helper abhi nahi hai. Bataiye kya karna hai, jaise video call."),
        "offline_sorry" to Triple("Sorry, I did not get that. Please say it again.", "माफ़ कीजिए, समझ नहीं आया। फिर से बोलिए।", "Sorry, samajh nahi aaya. Phir se boliye."),
        "offline_done" to Triple("Well done! That was the last step.", "बहुत बढ़िया! यह आखिरी कदम था।", "Bahut badhiya! Yeh aakhri step tha."),
        "family_title" to Triple("Connect your family", "अपने परिवार से जोड़ें", "Family se connect karein"),
        "family_body" to Triple("Ask your son, daughter or carer for the code from the R17E family dashboard and type it here.", "अपने बेटे, बेटी या देखभाल करने वाले से R17E परिवार डैशबोर्ड का कोड लें और यहाँ लिखें।", "Apne bete, beti ya carer se R17E family dashboard ka code lijiye aur yahan likhiye."),
        "family_alerts" to Triple("If I see a scam message, a PIN screen or a payment screen, your family gets a short alert. What is on your screen is never sent to them.", "अगर मुझे धोखे वाला मैसेज, PIN स्क्रीन या पेमेंट स्क्रीन दिखे, तो परिवार को छोटा सा अलर्ट जाता है। आपकी स्क्रीन की बातें उन्हें कभी नहीं भेजी जातीं।", "Agar mujhe fraud message, PIN screen ya payment screen dikhe, to family ko chhota alert jaata hai. Aapki screen ki baatein unhe kabhi nahi bheji jaati."),
        "family_code" to Triple("Family code", "परिवार कोड", "Family code"),
        "family_connect" to Triple("Connect", "जोड़ें", "Connect karein"),
        "family_linked" to Triple("Connected to", "इनसे जुड़ा है", "Connected hai"),
        "family_bad_code" to Triple("That code did not work. Check it or ask for a new one.", "यह कोड काम नहीं किया। जाँचें या नया कोड माँगें।", "Yeh code kaam nahi kiya. Check kijiye ya naya code maangiye."),
        "family_net" to Triple("No internet. Please try again.", "इंटरनेट नहीं है। फिर से कोशिश करें।", "Internet nahi hai. Phir se try kijiye."),
        "bubble_hint" to Triple("Tap to talk", "बात करने के लिए दबाएँ", "Baat karne ke liye dabaiye"),
    )

    fun get(lang: Lang, key: String): String = t[key]?.let { when (lang) { Lang.EN -> it.first; Lang.HI -> it.second; Lang.HINGLISH -> it.third } } ?: key
    val keys: Set<String> get() = t.keys
}
