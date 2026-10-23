package com.ahmadns7.mercureal.assistant.wake

import android.content.Context

/**
 * Wake phrase presets and keyword-line generation for the sherpa-onnx KWS model.
 *
 * A keyword line is "PHONEMES @DISPLAY", one phoneme sequence per line in the model's own token set
 * (tokens are the ARPAbet-style set in the kws tokens.txt). Presets are hand-checked. A custom phrase
 * is looked up word by word in the bundled pronunciation lexicon (CMU dict filtered to tokens the
 * model knows, assets/wake/lexicon.txt). Words it does not contain fall back to a crude letter
 * mapping, which is the least reliable path.
 */
object WakePhrase {

    const val DEFAULT_PHRASE = "ahmad"

    /** The built-in presets. [PRESET_PHRASES] also includes the custom option via the editor. */
    val PRESET_PHRASES = listOf("ahmad", "hey mercureal")

    /** Hand-checked phoneme spellings for the presets. */
    private val PRESET_PHONEMES: Map<String, List<String>> = mapOf(
        "ahmad" to listOf("AA1 M AA1 D", "AH0 M AA1 D", "AH0 M EH1 D", "AA1 M AE1 D"),
        // "mercureal" is not in the dictionary, so it is spelled from its sounds (M ER K Y UW R IY AH L).
        "hey mercureal" to listOf("HH EY1 M ER1 K Y UH1 R IY0 AH0 L", "HH EY1 M ER0 K Y UH1 R IY0 AH0 L"),
    )

    /** Built-in keyword lines for the default phrase, used until the user picks one. */
    val DEFAULT_KEYWORDS: String = keywordLines("ahmad", PRESET_PHONEMES.getValue("ahmad"))

    data class Built(
        val phrase: String,
        val keywords: String,
        val fromPreset: Boolean,
        /** Words that were not in the dictionary and used the crude letter fallback. */
        val oovWords: List<String>,
    )

    /**
     * Valid phrase: 1 to 4 words, letters only (a-z, spaces), at most 40 characters.
     * Keeps the lookup bounded and the detector input sane.
     */
    fun normalize(input: String): String {
        val cleaned = input.lowercase().trim().replace(Regex("\\s+"), " ")
        require(cleaned.isNotEmpty()) { "type a wake phrase" }
        require(cleaned.length <= 40) { "keep the phrase under 40 characters" }
        require(Regex("[a-z]+( [a-z]+){0,3}").matches(cleaned)) {
            "use letters only, up to 4 words (no digits or punctuation)"
        }
        return cleaned
    }

    /** Number of distinct words the lexicon must cover for a phrase (drives lazy loading). */
    fun wordsNeeded(input: String): Set<String> = normalize(input).split(" ").toSet()

    /** Builds keyword lines for a phrase: presets first, then the lexicon, then the letter fallback. */
    fun build(input: String, lexicon: Map<String, List<String>>): Built {
        val phrase = normalize(input)
        PRESET_PHONEMES[phrase]?.let {
            return Built(phrase, keywordLines(phrase, it), fromPreset = true, oovWords = emptyList())
        }
        val oov = mutableListOf<String>()
        // One phoneme sequence per word (first pronunciation variant). Unknown words use the crude fallback.
        val parts = phrase.split(" ").map { word ->
            val known = lexicon[word]?.firstOrNull()
            if (known != null) {
                known
            } else {
                oov += word
                fallbackPhonemes(word)
            }
        }
        val seq = parts.filter { it.isNotBlank() }.joinToString(" ")
        require(seq.isNotBlank()) { "could not build a pronunciation for \"$phrase\"" }
        return Built(phrase, keywordLines(phrase, listOf(seq)), fromPreset = false, oovWords = oov)
    }

    /** Renders "PHONEMES @PHRASE" lines, one per pronunciation variant. Uppercase display text. */
    fun keywordLines(phrase: String, phonemeLines: List<String>): String {
        val display = "@" + phrase.uppercase()
        return phonemeLines.joinToString("\n") { "$it $display" } + "\n"
    }

    /** Parses lexicon.txt lines "word PH1 PH2 ..." into word -> pronunciation variants. */
    fun parseLexicon(lines: Sequence<String>, words: Set<String>): Map<String, List<String>> {
        val out = HashMap<String, MutableList<String>>()
        for (line in lines) {
            val parts = line.trim().split(' ').filter { it.isNotEmpty() }
            if (parts.size < 2) continue
            val word = parts[0]
            if (word !in words) continue
            out.getOrPut(word) { mutableListOf() }.add(parts.drop(1).joinToString(" "))
        }
        return out
    }

    /** Loads only the lexicon entries the phrase needs. The asset is ~3 MB. */
    fun lexiconFor(context: Context, words: Set<String>): Map<String, List<String>> =
        context.assets.open("wake/lexicon.txt").bufferedReader().useLines { parseLexicon(it, words) }

    /** Crude letter-to-phoneme mapping, used only for words missing from the lexicon. */
    private fun fallbackPhonemes(word: String): String = word.map { c ->
        when (c) {
            'a' -> "AE1"; 'b' -> "B"; 'c' -> "K"; 'd' -> "D"; 'e' -> "EH1"; 'f' -> "F"; 'g' -> "G"
            'h' -> "HH"; 'i' -> "IH1"; 'j' -> "JH"; 'k' -> "K"; 'l' -> "L"; 'm' -> "M"; 'n' -> "N"
            'o' -> "AA1"; 'p' -> "P"; 'q' -> "K"; 'r' -> "R"; 's' -> "S"; 't' -> "T"; 'u' -> "AH1"
            'v' -> "V"; 'w' -> "W"; 'x' -> "K S"; 'y' -> "Y"; 'z' -> "Z"; else -> ""
        }
    }.filter { it.isNotEmpty() }.joinToString(" ")
}
