package com.example.domain.language

import java.util.UUID

object LanguagePromptHelper {

    fun validateLanguageSelection(language: String, allowAutoDetect: Boolean = false): Result<String> {
        val trimmed = language.trim()
        if (trimmed.isBlank()) {
            return Result.failure(LanguageConfigurationException("Language selection cannot be empty."))
        }
        if (allowAutoDetect && trimmed.equals(LanguageOptions.AUTO_DETECT, ignoreCase = true)) {
            return Result.success(LanguageOptions.AUTO_DETECT)
        }
        val matched = LanguageOptions.SUPPORTED_LANGUAGES.firstOrNull {
            it.equals(trimmed, ignoreCase = true)
        } ?: return Result.failure(
            LanguageConfigurationException("Unsupported language '$trimmed'. Please choose a supported language.")
        )
        return Result.success(matched)
    }

    // -------------------------------------------------------------
    // 2 & 3. TRANSLATOR & HINGLISH SUPPORT
    // -------------------------------------------------------------
    fun buildTranslationPrompt(
        sourceLanguage: String,
        targetLanguage: String,
        text: String
    ): String {
        val hinglishGuidance = if (
            sourceLanguage.equals("Hinglish", ignoreCase = true) ||
            targetLanguage.equals("Hinglish", ignoreCase = true) ||
            sourceLanguage.equals(LanguageOptions.AUTO_DETECT, ignoreCase = true)
        ) {
            "\nNote on Hinglish: Support Romanized Hindi/Hinglish (e.g., 'mujhe kal school jana hai') accurately. If the target is Hindi, output proper Devanagari Hindi script. If the target is Hinglish, output natural Romanized Hindi. If the source text is ambiguous across languages, note the ambiguity briefly without assuming blindly."
        } else {
            ""
        }

        return """
Translate the following user-provided text from $sourceLanguage to $targetLanguage.
Preserve the original meaning, names, numbers, dates, and tone. Do not invent facts.$hinglishGuidance

User Text:
\"\"\"
$text
\"\"\"

Provide your response in this format:
1. Translation: (The translated text in $targetLanguage)
2. Notes: (Optional brief note on nuances or ambiguity if applicable, or "None")
""".trimIndent()
    }

    fun parseTranslationOutput(
        projectId: String,
        sourceLanguage: String,
        targetLanguage: String,
        inputText: String,
        raw: String
    ): TranslationResult {
        val translationSection = extractSection(raw, listOf("1. Translation", "Translation", "1."))
        val notesSection = extractSection(raw, listOf("2. Notes", "Notes", "2."))

        val finalTranslated = if (translationSection.isNotBlank()) {
            translationSection
        } else {
            raw.lines()
                .filterNot { it.trim().startsWith("2. Notes", ignoreCase = true) }
                .joinToString("\n")
                .trim()
        }

        return TranslationResult(
            projectId = projectId,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            inputText = inputText,
            translatedText = finalTranslated.ifBlank { raw.trim() },
            notesOrNuances = if (notesSection.equals("None", ignoreCase = true)) "" else notesSection
        )
    }

    // -------------------------------------------------------------
    // 4. REWRITE
    // -------------------------------------------------------------
    fun buildRewritePrompt(
        text: String,
        styleOption: String,
        language: String
    ): String {
        return """
Rewrite the following user-provided text using the action: "$styleOption".
Output Language: $language
Rules:
- Preserve the original meaning, names, numbers, and facts unless explicitly asked otherwise.
- Only rewrite the text supplied below.
- Return only the rewritten text clearly.

User Text:
\"\"\"
$text
\"\"\"
""".trimIndent()
    }

    // -------------------------------------------------------------
    // 5. GRAMMAR CHECK
    // -------------------------------------------------------------
    fun buildGrammarCheckPrompt(
        text: String,
        language: String = "English"
    ): String {
        return """
Perform a careful grammar and spelling check on the following user-provided text ($language).
Do NOT unnecessarily change the user's personal writing style or voice.

User Text:
\"\"\"
$text
\"\"\"

Structure your response using these exact 4 numbered headers:
1. Corrected Version: (The corrected text with grammar and spelling fixed)
2. Grammar Issues: (Bullet points of grammar issues found, or "No grammar issues found")
3. Spelling Issues: (Bullet points of spelling issues found, or "No spelling issues found")
4. Explanation of Corrections: (Clear explanation of the important corrections made)
""".trimIndent()
    }

    fun parseGrammarCheckResult(projectId: String, inputText: String, raw: String): GrammarCheckResult {
        val corrected = extractSection(raw, listOf("1. Corrected Version", "Corrected Version", "1.")).ifBlank {
            raw.lines().firstOrNull { it.isNotBlank() } ?: raw.trim()
        }
        val grammarIssues = extractBulletList(extractSection(raw, listOf("2. Grammar Issues", "Grammar Issues", "2.")))
        val spellingIssues = extractBulletList(extractSection(raw, listOf("3. Spelling Issues", "Spelling Issues", "3.")))
        val explanations = extractBulletList(
            extractSection(raw, listOf("4. Explanation of Corrections", "Explanation of Important Corrections", "Explanation", "4."))
        )

        return GrammarCheckResult(
            projectId = projectId,
            inputText = inputText,
            correctedVersion = corrected,
            grammarIssues = grammarIssues.ifEmpty { listOf("Reviewed sentence structure and agreement.") },
            spellingIssues = spellingIssues.ifEmpty { listOf("No major spelling issues detected.") },
            explanations = explanations.ifEmpty { listOf("Corrected grammar while preserving your original writing style.") },
            rawOutput = raw
        )
    }

    // -------------------------------------------------------------
    // 6. LANGUAGE DETECTION
    // -------------------------------------------------------------
    fun buildLanguageDetectionPrompt(text: String): String {
        return """
Detect the language of the following user-provided text.
If the text is in Romanized Hindi (Hinglish) or is short/ambiguous across multiple languages, state that clearly and do NOT present uncertain detection as 100% guaranteed.

User Text:
\"\"\"
$text
\"\"\"

Structure your response using these exact 3 numbered headers:
1. Detected Language: (Name of the primary detected language, e.g., English, Hindi, Hinglish, Spanish, etc.)
2. Confidence & Explanation: (Explanation of linguistic markers, script, and any uncertainty or ambiguity)
3. Ambiguous: (Yes or No)
""".trimIndent()
    }

    fun parseLanguageDetectionResult(projectId: String, inputText: String, raw: String): LanguageDetectionResult {
        val detected = extractSection(raw, listOf("1. Detected Language", "Detected Language", "1."))
            .lines().firstOrNull { it.isNotBlank() }?.trim()
            ?: inferFallbackLanguageLabel(raw)

        val confidenceExp = extractSection(raw, listOf("2. Confidence & Explanation", "Confidence", "Explanation", "2."))
            .ifBlank { raw.trim() }

        val ambiguousRaw = extractSection(raw, listOf("3. Ambiguous", "Ambiguous", "3.")).lowercase()
        val isAmbiguous = ambiguousRaw.contains("yes") ||
            confidenceExp.contains("ambiguous", ignoreCase = true) ||
            confidenceExp.contains("uncertain", ignoreCase = true)

        return LanguageDetectionResult(
            projectId = projectId,
            inputText = inputText,
            detectedLanguage = detected,
            confidenceExplanation = confidenceExp,
            isAmbiguous = isAmbiguous,
            rawOutput = raw
        )
    }

    private fun inferFallbackLanguageLabel(raw: String): String {
        for (lang in LanguageOptions.SUPPORTED_LANGUAGES) {
            if (raw.contains(lang, ignoreCase = true)) return lang
        }
        return "Detected Language (See explanation)"
    }

    // -------------------------------------------------------------
    // 7. SUMMARIZE (Multilingual Summarization)
    // -------------------------------------------------------------
    fun buildSummarizePrompt(
        text: String,
        outputLanguage: String,
        summaryLength: String
    ): String {
        return """
Summarize the following user-provided text in $outputLanguage.
Requested Summary Length: $summaryLength

Rules:
1. Generate an original summary based ONLY on the supplied text below.
2. Do NOT reproduce large verbatim portions of the source text.
3. Do NOT invent any facts not present in the source text.
4. Write the summary entirely in $outputLanguage.

Source Text:
\"\"\"
$text
\"\"\"
""".trimIndent()
    }

    // -------------------------------------------------------------
    // 8. EXPLAIN TEXT ("Explain This Text")
    // -------------------------------------------------------------
    fun buildExplainTextPrompt(
        text: String,
        explanationLanguage: String,
        explanationLevel: String
    ): String {
        return """
Explain the following user-provided text clearly in $explanationLanguage.
Explanation Level: $explanationLevel (Simple / Student / Detailed)

User Text:
\"\"\"
$text
\"\"\"

Structure your response in $explanationLanguage using these 3 numbered headers:
1. Overall Meaning: (Clear explanation of what the text means at a $explanationLevel level)
2. Difficult Words & Concepts: (Bullet list explaining key vocabulary, idioms, or concepts from the text)
3. Step-by-Step Breakdown: (Detailed walkthrough of the ideas in the text)
""".trimIndent()
    }

    fun parseExplainTextResult(
        projectId: String,
        explanationLanguage: String,
        explanationLevel: String,
        inputText: String,
        raw: String
    ): ExplainTextResult {
        val meaning = extractSection(raw, listOf("1. Overall Meaning", "Overall Meaning", "1.")).ifBlank {
            raw.lines().firstOrNull { it.isNotBlank() } ?: raw.trim()
        }
        val words = extractBulletList(extractSection(raw, listOf("2. Difficult Words & Concepts", "Difficult Words", "2.")))
        val breakdown = extractSection(raw, listOf("3. Step-by-Step Breakdown", "Step-by-Step", "3.")).ifBlank {
            raw.trim()
        }

        return ExplainTextResult(
            projectId = projectId,
            explanationLanguage = explanationLanguage,
            explanationLevel = explanationLevel,
            inputText = inputText,
            overallMeaning = meaning,
            keyWordsAndConcepts = words.ifEmpty { listOf("Core vocabulary explained in context.") },
            stepByStepExplanation = breakdown,
            rawOutput = raw
        )
    }

    // -------------------------------------------------------------
    // 9. LANGUAGE PRACTICE
    // -------------------------------------------------------------
    fun buildLanguagePracticePrompt(
        languageToLearn: String,
        level: String,
        topic: String
    ): String {
        return """
Create an interactive language learning practice set for:
Language to Learn: $languageToLearn
Learner Level: $level
Topic: $topic

Do not claim that completing this practice guarantees mastery of $languageToLearn.

Please structure your response using these exact sections:
1. Vocabulary Practice:
- Term 1: meaning and example
- Term 2: meaning and example
- Term 3: meaning and example

2. Conversation Practice:
(A short, natural 2-4 line sample dialogue on '$topic' in $languageToLearn with English support)

3. Exercises:
Q1 [Simple Question]: <question text> | ANSWER: <expected answer> | HINT: <short explanation>
Q2 [Fill-in-the-blank]: <sentence with ___ blank> | ANSWER: <missing word> | HINT: <short explanation>
Q3 [Translation Exercise]: <phrase to translate> | ANSWER: <expected translation> | HINT: <short explanation>
""".trimIndent()
    }

    fun parseLanguagePracticeResult(
        projectId: String,
        sessionId: String,
        languageToLearn: String,
        level: String,
        topic: String,
        raw: String
    ): LanguagePracticeResult {
        val vocab = extractBulletList(extractSection(raw, listOf("1. Vocabulary Practice", "Vocabulary Practice", "1.")))
            .ifEmpty { listOf("$languageToLearn key vocabulary for $topic") }

        val convStarter = extractSection(raw, listOf("2. Conversation Practice", "Conversation Practice", "2."))
            .ifBlank { "Practice greeting and discussing $topic in $languageToLearn." }

        val exercisesBlock = extractSection(raw, listOf("3. Exercises", "Exercises", "3.")).ifBlank { raw }
        val parsedExercises = mutableListOf<PracticeExerciseItem>()

        val qLines = exercisesBlock.lines().map { it.trim() }.filter {
            it.contains("ANSWER:", ignoreCase = true) || it.startsWith("Q", ignoreCase = true)
        }

        for ((idx, line) in qLines.withIndex()) {
            if (line.contains("|")) {
                val parts = line.split("|").map { it.trim() }
                val firstPart = parts.getOrNull(0) ?: ""
                val category = Regex("\\[(.*?)\\]").find(firstPart)?.groupValues?.get(1) ?: "Practice Question"
                val promptText = firstPart
                    .replace(Regex("^Q\\d+\\s*(\\[.*?\\])?\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
                    .trim()
                val answerPart = parts.firstOrNull { it.startsWith("ANSWER:", ignoreCase = true) }
                    ?.substringAfter(":", "")?.trim() ?: ""
                val hintPart = parts.firstOrNull { it.startsWith("HINT:", ignoreCase = true) }
                    ?.substringAfter(":", "")?.trim() ?: "Check vocabulary and grammar rules for $languageToLearn."

                if (promptText.isNotBlank() && answerPart.isNotBlank()) {
                    parsedExercises.add(
                        PracticeExerciseItem(
                            id = UUID.randomUUID().toString(),
                            category = category,
                            prompt = promptText,
                            expectedAnswer = answerPart,
                            hintOrExplanation = hintPart
                        )
                    )
                }
            }
        }

        if (parsedExercises.isEmpty()) {
            parsedExercises.addAll(
                listOf(
                    PracticeExerciseItem(
                        id = UUID.randomUUID().toString(),
                        category = "Simple Question",
                        prompt = "How do you greet someone politely in $languageToLearn when discussing $topic?",
                        expectedAnswer = "Hello",
                        hintOrExplanation = "Use a standard polite greeting in $languageToLearn."
                    ),
                    PracticeExerciseItem(
                        id = UUID.randomUUID().toString(),
                        category = "Fill-in-the-blank",
                        prompt = "Complete the $languageToLearn practice phrase for $topic: ___",
                        expectedAnswer = topic.split(" ").firstOrNull() ?: "practice",
                        hintOrExplanation = "Refer to the vocabulary list above."
                    ),
                    PracticeExerciseItem(
                        id = UUID.randomUUID().toString(),
                        category = "Translation Exercise",
                        prompt = "Translate a key phrase about $topic into $languageToLearn.",
                        expectedAnswer = languageToLearn,
                        hintOrExplanation = "Focus on preserving the natural meaning."
                    )
                )
            )
        }

        return LanguagePracticeResult(
            projectId = projectId,
            sessionId = sessionId,
            languageToLearn = languageToLearn,
            level = level,
            topic = topic,
            vocabularyList = vocab,
            conversationStarter = convStarter,
            exercises = parsedExercises,
            rawOutput = raw
        )
    }

    // -------------------------------------------------------------
    // 10. CONVERSATION PRACTICE
    // -------------------------------------------------------------
    fun buildConversationPracticePrompt(
        languageToLearn: String,
        level: String,
        scenarioOrTopic: String,
        userMessage: String
    ): String {
        val isInitialTurn = userMessage.isBlank()
        return if (isInitialTurn) {
            """
Start a friendly, educational language-learning conversation in $languageToLearn (Learner Level: $level, Topic: $scenarioOrTopic).
Provide:
1. AI Message: (Your opening conversational line in $languageToLearn with a helpful translation if appropriate)
2. Correction: No correction needed — conversation started!
3. Suggested Natural Alternative: Try replying with a polite greeting in $languageToLearn.
4. Short Explanation: (Brief tip on how to respond naturally in $languageToLearn)
""".trimIndent()
        } else {
            """
Continue the educational language-learning conversation for "Learn $languageToLearn" (Level: $level, Topic: $scenarioOrTopic).
Learner's Response:
\"\"\"
$userMessage
\"\"\"

Evaluate the learner's response supportively and reply using these exact 4 numbered headers:
1. AI Message: (Your next natural conversational reply in $languageToLearn to keep the dialogue going)
2. Correction: (Any grammar/word-choice correction for the learner's response, or "Great job! Your sentence is grammatically correct.")
3. Suggested Natural Alternative: (A more native/natural way to phrase what the learner said in $languageToLearn)
4. Short Explanation: (A concise educational explanation of the correction or phrasing tip)
""".trimIndent()
        }
    }

    fun parseConversationPracticeResult(
        projectId: String,
        languageToLearn: String,
        userMessage: String,
        raw: String
    ): ConversationPracticeTurnResult {
        val aiMsg = extractSection(raw, listOf("1. AI Message", "AI Message", "1.")).ifBlank {
            raw.lines().firstOrNull { it.isNotBlank() } ?: raw.trim()
        }
        val correction = extractSection(raw, listOf("2. Correction", "Correction", "2.")).ifBlank {
            "Good effort! Keep practicing your sentence structure."
        }
        val alternative = extractSection(raw, listOf("3. Suggested Natural Alternative", "Suggested Natural Alternative", "Natural Alternative", "3.")).ifBlank {
            aiMsg
        }
        val explanation = extractSection(raw, listOf("4. Short Explanation", "Short Explanation", "Explanation", "4.")).ifBlank {
            "Practicing conversational turns helps build natural phrasing in $languageToLearn."
        }

        return ConversationPracticeTurnResult(
            projectId = projectId,
            languageToLearn = languageToLearn,
            userMessage = userMessage,
            aiReply = aiMsg,
            correction = correction,
            naturalAlternative = alternative,
            shortExplanation = explanation,
            rawOutput = raw
        )
    }

    // -------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------
    private fun extractBulletList(block: String): List<String> {
        if (block.isBlank()) return emptyList()
        return block.lines()
            .map {
                it.trim()
                    .removePrefix("-")
                    .removePrefix("*")
                    .removePrefix("•")
                    .replace(Regex("^\\d+[.)]\\s*"), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
    }

    private fun extractSection(text: String, headers: List<String>): String {
        val lines = text.lines()
        var capturing = false
        val buffer = StringBuilder()

        val allKnownHeaders = listOf(
            "1. Translation", "2. Notes",
            "1. Corrected Version", "2. Grammar Issues", "3. Spelling Issues", "4. Explanation of Corrections",
            "1. Detected Language", "2. Confidence & Explanation", "3. Ambiguous",
            "1. Overall Meaning", "2. Difficult Words & Concepts", "3. Step-by-Step Breakdown",
            "1. Vocabulary Practice", "2. Conversation Practice", "3. Exercises",
            "1. AI Message", "2. Correction", "3. Suggested Natural Alternative", "4. Short Explanation"
        )

        for (line in lines) {
            val cleaned = line.trim().removePrefix("#").removePrefix("##").removePrefix("###").replace("**", "").trim()
            val matchesTarget = headers.any { h ->
                cleaned.startsWith("$h:", ignoreCase = true) ||
                    cleaned.equals(h, ignoreCase = true)
            }

            if (matchesTarget) {
                capturing = true
                val afterColon = cleaned.substringAfter(":", "").trim()
                if (afterColon.isNotEmpty() && !headers.any { it.equals(afterColon, ignoreCase = true) }) {
                    buffer.appendLine(afterColon)
                }
                continue
            }

            if (capturing) {
                val hitsNextHeader = allKnownHeaders.any { other ->
                    !headers.any { h -> other.equals(h, ignoreCase = true) } &&
                        (cleaned.startsWith("$other:", ignoreCase = true) || cleaned.equals(other, ignoreCase = true))
                } || Regex("^[1-4]\\.\\s+[A-Za-z &/\\-]+:.*").matches(cleaned)

                if (hitsNextHeader) break
                buffer.appendLine(line)
            }
        }
        return buffer.toString().trim()
    }
}
