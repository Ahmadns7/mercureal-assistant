package com.ahmadns7.mercureal.assistant.memory

/** What a transcript asks the assistant to do with notes. Anything else is [None] (normal chat). */
sealed interface VoiceIntent {
    data class Remember(val text: String) : VoiceIntent

    /** [topic] null means "all my notes". */
    data class Recall(val topic: String?) : VoiceIntent

    data object None : VoiceIntent
}

object VoiceIntents {

    private val remember = Regex(
        """^\s*(?:please\s+)?remember\s+(?:that\s+)?(.+?)[\s.!?]*$""",
        RegexOption.IGNORE_CASE,
    )
    private val recallAll = Regex(
        """^\s*(?:(?:show|read|list|tell)\s+me\s+|what\s+(?:are|is)\s+|what\s+)?""" +
            """(?:my\s+notes|my\s+memories|what\s+do\s+you\s+remember)[\s.!?]*$""",
        RegexOption.IGNORE_CASE,
    )
    private val recallAbout = Regex(
        """^\s*what\s+(?:do\s+you|did\s+i|have\s+i)\s+(?:remember|know|save|note|tell\s+you)\s+(?:about|on)\s+(.+?)[\s.!?]*$""",
        RegexOption.IGNORE_CASE,
    )
    private val filler = setOf("that", "it", "this", "something")

    fun parse(text: String): VoiceIntent {
        val t = text.trim()
        if (t.isEmpty()) return VoiceIntent.None
        remember.matchEntire(t)?.groupValues?.get(1)?.trim()?.let { body ->
            if (body.length >= 2 && body.lowercase() !in filler) return VoiceIntent.Remember(body)
        }
        if (recallAll.matches(t)) return VoiceIntent.Recall(null)
        recallAbout.matchEntire(t)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { return VoiceIntent.Recall(it) }
        return VoiceIntent.None
    }

    /**
     * Text placed into the LLM system prompt for a recall turn. Only the notes that matter for the
     * question are included: all of them (capped) for "my notes", otherwise notes sharing a word
     * with the topic.
     */
    fun memoryContext(notes: List<Note>, topic: String?): String {
        if (notes.isEmpty()) return "The user has no saved notes on this phone."
        if (topic == null) {
            return HEADER + notes.take(MAX_ALL).joinToString("\n") { "- ${it.text}" }
        }
        val words = topic.lowercase().split(Regex("\\W+")).filter { it.length >= 3 }
        val hits = notes.filter { n ->
            val lower = n.text.lowercase()
            words.any { lower.contains(it) }
        }
        if (hits.isEmpty()) return "No saved note mentions \"$topic\"."
        return HEADER + hits.take(MAX_ALL).joinToString("\n") { "- ${it.text}" }
    }

    private const val HEADER =
        "The user's private notes saved on this phone. Use them only to answer the question, " +
            "and say so if they do not contain the answer:\n"
    private const val MAX_ALL = 20
}
