package app.yomilens.reading

import app.yomilens.model.ReadingLine
import app.yomilens.model.ReadingToken
import app.yomilens.text.JapaneseScript
import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer

class JapaneseReadingEngine(
    tokenizerFactory: () -> Tokenizer = { Tokenizer() },
) {
    private val tokenizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED, tokenizerFactory)

    fun annotate(text: String): List<ReadingLine> = text
        .lineSequence()
        .map { line ->
            ReadingLine(
                tokens = if (line.isBlank()) {
                    emptyList()
                } else {
                    applyReadingOverrides(tokenizer.tokenize(line).map(::toReadingToken))
                },
            )
        }
        .toList()

    fun romanize(lines: List<ReadingLine>): String = lines.joinToString("\n") { line ->
        buildString {
            line.tokens.forEachIndexed { index, token ->
                val romanized = romanizeToken(token)
                if (index > 0 && shouldInsertSpace(line.tokens[index - 1], token)) {
                    append(' ')
                }
                append(romanized)
            }
        }
    }

    fun romanizeKanji(lines: List<ReadingLine>): String = lines.mapNotNull { line ->
        line.tokens.filter { token -> KanaScripts.containsKanji(token.surface) }
            .takeIf(List<ReadingToken>::isNotEmpty)
    }.joinToString("\n") { tokens ->
        tokens.joinToString(" ") { token ->
            val fullReading = KanaScripts.katakanaToHiragana(token.readingKatakana)
            val kanjiReading = JapaneseScript.kanjiReading(token.surface, fullReading)
            HepburnRomanizer.romanize(KanaScripts.hiraganaToKatakana(kanjiReading))
        }
    }

    private fun toReadingToken(token: Token): ReadingToken {
        val reading = token.reading.takeUnless { it == "*" }.orEmpty()
        val surface = token.surface
        return ReadingToken(
            surface = surface,
            furigana = reading
                .takeIf { it.isNotBlank() && KanaScripts.containsKanji(surface) }
                ?.let(KanaScripts::katakanaToHiragana),
            readingKatakana = reading.ifBlank { surface },
            isParticle = token.partOfSpeechLevel1 == "助詞",
        )
    }

    private fun romanizeToken(token: ReadingToken): String {
        if (token.isParticle) {
            when (token.surface) {
                "は" -> return "wa"
                "へ" -> return "e"
                "を" -> return "o"
            }
        }
        return HepburnRomanizer.romanize(token.readingKatakana)
    }

    private fun shouldInsertSpace(previous: ReadingToken, current: ReadingToken): Boolean {
        val closing = "。、！？!?.,:：）」』】]"
        val opening = "（「『【["
        val currentStartsWithClosing = current.surface.firstOrNull()?.let(closing::contains) ?: false
        val previousEndsWithOpening = previous.surface.lastOrNull()?.let(opening::contains) ?: false
        return !currentStartsWithClosing && !previousEndsWithOpening
    }

    private fun applyReadingOverrides(tokens: List<ReadingToken>): List<ReadingToken> = buildList {
        var index = 0
        while (index < tokens.size) {
            val override = READING_OVERRIDES.entries.firstOrNull { (surface, _) ->
                var combined = ""
                var candidateIndex = index
                while (candidateIndex < tokens.size && combined.length < surface.length) {
                    combined += tokens[candidateIndex].surface
                    candidateIndex += 1
                }
                combined == surface
            }
            if (override == null) {
                add(tokens[index])
                index += 1
                continue
            }

            val (surface, hiragana) = override
            add(
                ReadingToken(
                    surface = surface,
                    furigana = hiragana,
                    readingKatakana = KanaScripts.hiraganaToKatakana(hiragana),
                    isParticle = false,
                ),
            )
            var consumedSurface = ""
            while (index < tokens.size && consumedSurface.length < surface.length) {
                consumedSurface += tokens[index].surface
                index += 1
            }
        }
    }

    private companion object {
        /** Kuromoji splits this established harbor term and loses its compound reading. */
        val READING_OVERRIDES = linkedMapOf(
            "係船柱" to "けいせんちゅう",
        )
    }
}
