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
    /** Set when the helper stopped itself after a long silence (shown once on Home). */
    var stoppedIdle: Boolean
        get() = p.getBoolean("stopped_idle", false)
        set(v) { p.edit().putBoolean("stopped_idle", v).apply() }
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
        "skip" to Triple("Skip", "छोड़ें", "Skip kijiye"),
        "done" to Triple("Done", "हो गया", "Ho gaya"),
        "how_title" to Triple("How it works", "यह कैसे काम करता है", "Yeh kaise kaam karta hai"),
        "how_1" to Triple("Tell me what you want to do, like \"video call my son on WhatsApp\".", "बताइए आपको क्या करना है, जैसे \"बेटे को व्हाट्सऐप पर वीडियो कॉल\"।", "Bataiye kya karna hai, jaise \"bete ko WhatsApp par video call\"."),
        "how_2" to Triple("I draw a yellow circle around the right button and tell you what to do.", "मैं सही बटन के चारों ओर पीला घेरा बनाता हूँ और बताता हूँ क्या करना है।", "Main sahi button ke around peela gola banata hoon aur batata hoon kya karna hai."),
        "how_3" to Triple("I only show you. I never tap, type or change anything for you.", "मैं सिर्फ़ दिखाता हूँ। मैं खुद कभी कुछ नहीं दबाता या बदलता।", "Main sirf dikhata hoon. Main khud kabhi tap ya type nahi karta."),
        "privacy" to Triple("When the helper is on, your voice and the words on your screen go to Google Gemini so it can answer. Passwords, PINs and OTPs are hidden first.", "सहायक चालू होने पर आपकी आवाज़ और स्क्रीन के शब्द Google Gemini को भेजे जाते हैं। पासवर्ड, PIN और OTP पहले छिपा दिए जाते हैं।", "Helper on hone par aapki awaaz aur screen ke words Google Gemini ko jaate hain. Password, PIN aur OTP pehle chhupa diye jaate hain."),
        "agree" to Triple("I understand", "ठीक है, समझ आ गया", "Theek hai, samajh aa gaya"),
        "mic_title" to Triple("Allow the microphone", "माइक की अनुमति दें", "Mic ki permission dijiye"),
        "mic_body" to Triple("So I can hear you. I only listen while the helper is on.", "ताकि मैं आपकी बात सुन सकूँ। मैं सिर्फ़ सहायक चालू होने पर सुनता हूँ।", "Taaki main aapki baat sun sakoon. Sirf helper on hone par sunta hoon."),
        "allow" to Triple("Allow", "अनुमति दें", "Allow kijiye"),
        "granted" to Triple("Allowed", "अनुमति मिल गई", "Allow ho gaya"),
        "notif_title" to Triple("Allow notifications", "नोटिफ़िकेशन की अनुमति दें", "Notifications allow kijiye"),
        "notif_body" to Triple("A small notice shows while I am listening, with a Stop button.", "जब मैं सुन रहा होता हूँ, ऊपर एक छोटा नोटिफ़िकेशन दिखता है। उसमें बंद करने का बटन होता है।", "Jab main sun raha hoon, ek chhota notice dikhta hai jisme Stop button hota hai."),
        "a11y_title" to Triple("Turn on screen helper", "स्क्रीन सहायक चालू करें", "Screen helper on kijiye"),
        "a11y_body" to Triple("Tap the button, find \"R17E Guide\" under Installed apps or Downloaded apps, and switch it on.", "बटन दबाएँ, \"Installed apps\" या \"Downloaded apps\" में \"R17E Guide\" खोजें और चालू करें।", "Button dabaiye, \"Installed apps\" ya \"Downloaded apps\" mein \"R17E Guide\" dhoondhiye aur on kijiye."),
        "a11y_restricted" to Triple("If it is greyed out: open App info for R17E, tap ⋮ (top right), choose \"Allow restricted settings\", then try again.", "अगर यह धुंधला है: R17E की App info खोलें, ऊपर दाईं ओर ⋮ दबाएँ, \"Allow restricted settings\" चुनें, फिर दोबारा करें।", "Agar yeh grey hai: R17E ki App info kholiye, upar ⋮ dabaiye, \"Allow restricted settings\" chuniye, phir dobara try kijiye."),
        "open_settings" to Triple("Open settings", "सेटिंग्स खोलें", "Settings kholiye"),
        "open_app_info" to Triple("Open App info", "App info खोलें", "App info kholiye"),
        "ready_title" to Triple("All set!", "सब तैयार है!", "Sab ready hai!"),
        "ready_body" to Triple("Tap the big button, or the round helper button on any screen, and tell me what you want to do.", "बड़ा बटन दबाएँ, या किसी भी स्क्रीन पर गोल सहायक बटन दबाएँ, और बताइए क्या करना है।", "Bada button dabaiye, ya kisi bhi screen par gol helper button dabaiye, aur bataiye kya karna hai."),
        "start" to Triple("Start helper", "सहायक शुरू करें", "Helper shuru kijiye"),
        "stop" to Triple("Stop helper", "सहायक बंद करें", "Helper band kijiye"),
        "st_stopped" to Triple("Helper is off", "सहायक बंद है", "Helper band hai"),
        "st_connecting" to Triple("Getting ready…", "तैयार हो रहा है…", "Ready ho raha hai…"),
        "st_listening" to Triple("I am listening. Tell me what you want to do.", "मैं सुन रहा हूँ। बताइए क्या करना है।", "Main sun raha hoon. Bataiye kya karna hai."),
        "st_speaking" to Triple("Speaking…", "बोल रहा हूँ…", "Bol raha hoon…"),
        "st_reconnecting" to Triple("Connection dropped. Reconnecting…", "इंटरनेट रुक गया। फिर से जोड़ रहा हूँ…", "Internet ruk gaya. Phir se jod raha hoon…"),
        "st_offline" to Triple("No internet helper right now. Using simple offline help.", "इंटरनेट नहीं मिल रहा। अभी आसान ऑफ़लाइन मदद चल रही है।", "Internet nahi mil raha. Abhi simple offline help chal rahi hai."),
        "screen_off" to Triple("Screen helper is off. I can talk but cannot point.", "स्क्रीन सहायक बंद है। मैं बात कर सकता हूँ पर दिखा नहीं सकता।", "Screen helper band hai. Main baat kar sakta hoon par dikha nahi sakta."),
        "turn_on" to Triple("Turn on", "चालू करें", "On kijiye"),
        "mic_needed" to Triple("Microphone permission is needed.", "माइक की अनुमति चाहिए।", "Mic ki permission chahiye."),
        "try_saying" to Triple("Try saying", "ऐसे बोलकर देखें", "Aise bol ke dekhiye"),
        "ex_1" to Triple("\"How do I video call on WhatsApp?\"", "\"व्हाट्सऐप पर वीडियो कॉल कैसे करूँ?\"", "\"WhatsApp par video call kaise karun?\""),
        "ex_2" to Triple("\"Make the sound louder\"", "\"आवाज़ बढ़ानी है\"", "\"Awaaz badhani hai\""),
        "ex_3" to Triple("\"Is this message a scam?\"", "\"क्या यह मैसेज धोखा है?\"", "\"Kya yeh message fraud hai?\""),
        "language" to Triple("Language", "भाषा", "Bhasha"),
        "notif_listening" to Triple("R17E helper is listening", "R17E सहायक सुन रहा है", "R17E helper sun raha hai"),
        "notif_text" to Triple("Talk to me. I will show you where to tap.", "बोलिए। मैं दिखाऊँगा कहाँ दबाना है।", "Boliye. Main dikhaunga kahan tap karna hai."),
        "offline_hello" to Triple("The online helper is not available. Tell me what you want to do, like video call.", "इंटरनेट वाला सहायक अभी नहीं है। बताइए, क्या करना है? जैसे वीडियो कॉल।", "Online helper abhi nahi hai. Bataiye kya karna hai, jaise video call."),
        "offline_sorry" to Triple("Sorry, I did not get that. Please say it again.", "माफ़ कीजिए, समझ नहीं आया। फिर से बोलिए।", "Sorry, samajh nahi aaya. Phir se boliye."),
        "offline_done" to Triple("Well done! That was the last step.", "बहुत बढ़िया! यह आखिरी कदम था।", "Bahut badhiya! Yeh aakhri step tha."),
        "family_title" to Triple("Connect your family", "अपने परिवार से जोड़ें", "Family se judiye"),
        "family_body" to Triple("Ask your son, daughter or carer for the code from the R17E family dashboard and type it here.", "अपने बेटे, बेटी या देखभाल करने वाले से R17E परिवार डैशबोर्ड का कोड लें और यहाँ लिखें।", "Apne bete, beti ya carer se R17E family dashboard ka code lijiye aur yahan likhiye."),
        "family_alerts" to Triple("If I see a scam message, a PIN screen or a payment screen, your family gets a short alert. What is on your screen is never sent to them.", "अगर मुझे धोखे वाला मैसेज, PIN स्क्रीन या पेमेंट स्क्रीन दिखे, तो परिवार को छोटा सा अलर्ट जाता है। आपकी स्क्रीन की बातें उन्हें कभी नहीं भेजी जातीं।", "Agar mujhe fraud message, PIN screen ya payment screen dikhe, to family ko chhota alert jaata hai. Aapki screen ki baatein unhe kabhi nahi bheji jaati."),
        "family_code" to Triple("Family code", "परिवार कोड", "Family code"),
        "family_connect" to Triple("Connect", "जोड़ें", "Connect kijiye"),
        "family_linked" to Triple("Connected to", "इनसे जुड़ा है", "Connected hai"),
        "family_bad_code" to Triple("That code did not work. Check it or ask for a new one.", "यह कोड नहीं चला। एक बार जाँच लें, या नया कोड माँग लें।", "Yeh code kaam nahi kiya. Check kijiye ya naya code maangiye."),
        "family_net" to Triple("No internet. Please try again.", "इंटरनेट नहीं मिल रहा। थोड़ी देर में फिर से दबाएँ।", "Internet nahi mil raha. Thodi der mein phir se dabaiye."),
        "settings" to Triple("Settings", "सेटिंग्स", "Settings"),
        "helper_section" to Triple("Helper", "सहायक", "Helper"),
        "screen_helper" to Triple("Screen helper", "स्क्रीन सहायक", "Screen helper"),
        "on" to Triple("On", "चालू", "On"),
        "a11y_starting" to Triple("Screen helper is starting…", "स्क्रीन सहायक शुरू हो रहा है…", "Screen helper shuru ho raha hai…"),
        "a11y_stuck" to Triple("Screen helper is switched on but not responding. Turn it off and on again in Settings.", "स्क्रीन सहायक चालू है, पर जवाब नहीं दे रहा। सेटिंग्स में इसे एक बार बंद करके फिर चालू करें।", "Screen helper on hai, par jawab nahi de raha. Settings mein ise ek baar band karke phir on kijiye."),
        "family_wait" to Triple("Connecting to your family…", "परिवार से जोड़ रहा हूँ…", "Family se jod raha hoon…"),
        "unused_title" to Triple("Keep the helper ready", "सहायक को तैयार रखें", "Helper ko ready rakhiye"),
        "unused_body" to Triple("Android may switch the helper off if you do not use it for a few months. Open App info and turn off \"Pause app activity if unused\".", "कुछ महीने इस्तेमाल न करने पर Android सहायक को बंद कर सकता है। App info खोलें और \"Pause app activity if unused\" बंद करें।", "Kuch mahine use na karne par Android helper ko band kar sakta hai. App info kholiye aur \"Pause app activity if unused\" band kijiye."),
        "battery_title" to Triple("Let the helper run in the background", "सहायक को पीछे चलने दें", "Helper ko background mein chalne dijiye"),
        "battery_body" to Triple("So the phone does not stop me while we talk. Choose \"Unrestricted\" or \"Don't optimise\" for R17E.", "ताकि बात करते समय फ़ोन मुझे बंद न करे। R17E के लिए \"Unrestricted\" या \"Don't optimise\" चुनें।", "Taaki baat karte waqt phone mujhe band na kare. R17E ke liye \"Unrestricted\" ya \"Don't optimise\" chuniye."),
        "fix_now" to Triple("Fix now", "अभी ठीक करें", "Abhi theek kijiye"),
        "about" to Triple("About", "जानकारी", "Jaankari"),
        "version" to Triple("Version", "वर्शन", "Version"),
        "server" to Triple("Server", "सर्वर", "Server"),
        "scam_warn" to Triple("Careful! This screen looks like a fraud. Do not share any OTP or PIN, and do not send money. Ask your family first.", "सावधान! यह स्क्रीन धोखे वाली लगती है। कोई OTP या PIN न बताएँ, और पैसे न भेजें। पहले परिवार से पूछ लें।", "Savdhaan! Yeh screen fraud jaisi lagti hai. Koi OTP ya PIN mat bataiye, aur paise mat bhejiye. Pehle family se pooch lijiye."),
        "stopped_idle" to Triple("I stopped listening because it was quiet for a while. Tap to start again.", "काफ़ी देर से कोई बात नहीं हुई, इसलिए मैंने सुनना बंद कर दिया। फिर से शुरू करने के लिए दबाएँ।", "Kaafi der se koi baat nahi hui, isliye maine sunna band kar diya. Phir se shuru karne ke liye dabaiye."),
        "off" to Triple("Off", "बंद", "Band"),
        "step_of" to Triple("Step {n} of {m}", "कदम {n} / {m}", "Step {n} / {m}"),
        "continue" to Triple("Continue", "आगे बढ़ें", "Aage badhiye"),
        "st_stopped_sub" to Triple("Tap Start, then tell me what you want to do.", "शुरू करें दबाएँ, फिर बताइए क्या करना है।", "Shuru dabaiye, phir bataiye kya karna hai."),
        "st_listening_t" to Triple("Listening", "सुन रहा हूँ", "Sun raha hoon"),
        "st_listening_sub" to Triple("Tell me what you want to do.", "बताइए, क्या करना है?", "Bataiye, kya karna hai?"),
        "st_speaking_t" to Triple("Speaking", "बोल रहा हूँ", "Bol raha hoon"),
        "st_speaking_sub" to Triple("You can talk any time to stop me.", "बीच में बोलकर आप मुझे कभी भी रोक सकते हैं।", "Beech mein bolkar aap mujhe kabhi bhi rok sakte hain."),
        "st_connecting_t" to Triple("Getting ready", "तैयार हो रहा है", "Ready ho raha hai"),
        "st_connecting_sub" to Triple("This takes a few seconds.", "बस कुछ सेकंड।", "Bas kuch second."),
        "st_reconnecting_t" to Triple("Reconnecting", "फिर से जोड़ रहा हूँ", "Phir se jod raha hoon"),
        "st_reconnecting_sub" to Triple("The internet dropped. I will keep trying.", "इंटरनेट रुक गया था। मैं कोशिश कर रहा हूँ।", "Internet ruk gaya tha. Main koshish kar raha hoon."),
        "st_offline_t" to Triple("Offline help", "ऑफ़लाइन मदद", "Offline help"),
        "st_offline_sub" to Triple("No internet. Simple help still works, and I will reconnect by myself.", "इंटरनेट नहीं है। आसान मदद चल रही है, और इंटरनेट आते ही मैं खुद जुड़ जाऊँगा।", "Internet nahi hai. Simple help chal rahi hai, internet aate hi main khud jud jaunga."),
        "next_step" to Triple("Next step", "अगला कदम", "Agla step"),
        "scam_title" to Triple("Possible scam", "धोखे का खतरा", "Fraud ka khatra"),
        "got_it" to Triple("OK", "ठीक है", "Theek hai"),
        "privacy_title" to Triple("Your privacy", "आपकी निजता", "Aapki privacy"),
        "family_alerts_on" to Triple("Family alerts are on", "परिवार को अलर्ट चालू हैं", "Family alerts on hain"),
        "device_id" to Triple("Device ID", "डिवाइस ID", "Device ID"),
        "theme_note" to Triple("Follows your phone's light or dark setting.", "फ़ोन की लाइट या डार्क सेटिंग के हिसाब से बदलता है।", "Phone ki light ya dark setting ke hisaab se badalta hai."),
        "appearance" to Triple("Appearance", "रूप", "Look"),
        "help_running" to Triple("Helper is running", "सहायक चल रहा है", "Helper chal raha hai"),
        "bubble_hint" to Triple("Tap to talk", "बात करने के लिए दबाएँ", "Baat karne ke liye dabaiye"),
    )

    fun get(lang: Lang, key: String): String = t[key]?.let { when (lang) { Lang.EN -> it.first; Lang.HI -> it.second; Lang.HINGLISH -> it.third } } ?: key
    val keys: Set<String> get() = t.keys
}
