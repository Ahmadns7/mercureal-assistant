package com.ahmadns7.mercureal.assistant.translate

/** A language the translation and speech-language settings can name. [name] is what the LLM is told. */
data class Language(val code: String, val name: String)

object Languages {

    val targets: List<Language> = listOf(
        Language("fr", "French"),
        Language("es", "Spanish"),
        Language("de", "German"),
        Language("it", "Italian"),
        Language("pt", "Portuguese"),
        Language("tr", "Turkish"),
        Language("ar", "Arabic"),
        Language("ru", "Russian"),
        Language("zh", "Chinese"),
        Language("ja", "Japanese"),
        Language("ko", "Korean"),
        Language("hi", "Hindi"),
        Language("sw", "Swahili"),
        Language("ha", "Hausa"),
        Language("yo", "Yoruba"),
        Language("id", "Indonesian"),
        Language("en", "English"),
    )

    const val DEFAULT_TARGET = "fr"

    /** Speech-recognition language codes offered in Settings. "auto" is whisper's language detection. */
    val sttCodes: List<String> = listOf("auto", "en", "fr", "es", "de", "it", "pt", "tr", "ar", "ru", "zh", "ja", "ko", "hi", "sw", "ha", "yo", "id")

    fun byCode(code: String): Language? = targets.firstOrNull { it.code == code }

    fun byName(name: String): Language? = targets.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /**
     * The bundled offline voice (sherpa-onnx vits-ljs) is English-only, so only English can be spoken.
     * Every other target is shown on screen and the app says so aloud.
     */
    fun voiceCanSpeak(lang: Language): Boolean = lang.code == "en"
}

/** A "say X in <language>" / "translate X to <language>" request. [target] null means the Settings default. */
data class TranslateRequest(val text: String, val target: Language?)

object Translation {

    private val sayIn = Regex(
        """^\s*(?:please\s+)?(?:say|how\s+do\s+(?:i|you)\s+say)\s+(.+?)\s+in\s+([a-z]+)[\s.!?]*$""",
        RegexOption.IGNORE_CASE,
    )
    private val translateTo = Regex(
        """^\s*(?:please\s+)?translate\s+(.+?)(?:\s+(?:to|into)\s+([a-z]+))?[\s.!?]*$""",
        RegexOption.IGNORE_CASE,
    )

    /** Returns null when the text is not a translation request, or names a language we don't support. */
    fun parse(text: String): TranslateRequest? {
        val t = text.trim()
        if (t.isEmpty()) return null
        sayIn.matchEntire(t)?.let { m ->
            val lang = Languages.byName(m.groupValues[2]) ?: return null
            val body = m.groupValues[1].trim()
            return if (body.length >= 1) TranslateRequest(body, lang) else null
        }
        translateTo.matchEntire(t)?.let { m ->
            val body = m.groupValues[1].trim()
            val langWord = m.groupValues[2]
            if (langWord.isEmpty()) return if (body.isNotEmpty()) TranslateRequest(body, null) else null
            val lang = Languages.byName(langWord) ?: return null
            return if (body.isNotEmpty()) TranslateRequest(body, lang) else null
        }
        return null
    }
}
