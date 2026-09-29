package com.example.domain.creator

object CreatorPromptHelper {

    fun buildContentGeneratorPrompt(
        platform: String,
        contentType: String,
        topic: String,
        targetAudience: String,
        language: String,
        tone: String,
        length: String,
        additionalInstructions: String
    ): String {
        return """
Create original creator content based on the following parameters:
- Platform: $platform
- Content Type: $contentType
- Topic: $topic
- Target Audience: ${targetAudience.ifBlank { "General audience" }}
- Language: $language
- Tone: $tone
- Length: $length
${if (additionalInstructions.isNotBlank()) "- Additional Instructions: $additionalInstructions\n" else ""}
Requirements:
- Write 100% original, engaging content tailored to $platform in $language.
- Do not claim guaranteed virality or reproduce copyrighted text.
- Organize with clear headings or sections for easy reading and copying.
""".trimIndent()
    }

    fun buildYouTubeScriptPrompt(
        topic: String,
        duration: String,
        language: String,
        audience: String,
        style: String
    ): String {
        val isShorts = duration.contains("second", ignoreCase = true) || duration.contains("short", ignoreCase = true)
        return """
Create an original YouTube ${if (isShorts) "Shorts (short-form)" else "video"} script with the following details:
- Topic: $topic
- Video Duration: $duration
- Language: $language
- Target Audience: ${audience.ifBlank { "General viewers" }}
- Style: $style

Please structure your output using these exact numbered section headers in $language:
1. Title Ideas: (Provide 3 to 5 original title suggestions; do not claim guaranteed views or virality)
2. Hook: (Compelling opening for the first 3-10 seconds)
3. Introduction: (Brief setup and what the viewer will learn/experience)
4. Main Content: (${if (isShorts) "Paced, punchy short-form points suitable for $duration" else "Structured talking points and visual cues suitable for $duration"})
5. Call to Action: (Natural viewer engagement prompt)
6. Closing: (Memorable sign-off / outro)
""".trimIndent()
    }

    fun parseYouTubeScript(
        projectId: String,
        topic: String,
        duration: String,
        language: String,
        raw: String
    ): YouTubeScriptResult {
        val titlesBlock = extractSection(raw, listOf("1. Title Ideas", "Title Ideas", "1."))
        val hook = extractSection(raw, listOf("2. Hook", "Hook", "2.")).ifBlank { raw.lines().firstOrNull().orEmpty() }
        val intro = extractSection(raw, listOf("3. Introduction", "Introduction", "3."))
        val main = extractSection(raw, listOf("4. Main Content", "Main Content", "Body", "4.")).ifBlank { raw }
        val cta = extractSection(raw, listOf("5. Call to Action", "Call to Action", "CTA", "5."))
        val closing = extractSection(raw, listOf("6. Closing", "Closing", "Ending", "Outro", "6."))

        val parsedTitles = titlesBlock.lines()
            .map { it.trim().removePrefix("-").removePrefix("*").replace(Regex("^\\d+[.)]\\s*"), "").trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("$topic - Complete Guide", "How to Master $topic", "$topic Explained Simply") }

        return YouTubeScriptResult(
            projectId = projectId,
            topic = topic,
            duration = duration,
            language = language,
            titleIdeas = parsedTitles,
            hook = hook.ifBlank { "Look closely at how $topic works in practice." },
            introduction = intro.ifBlank { "Welcome back! Today we are exploring $topic." },
            mainContent = main,
            callToAction = cta.ifBlank { "Let us know your thoughts in the comments and subscribe for more!" },
            closing = closing.ifBlank { "Thanks for watching, see you in the next video!" },
            rawOutput = raw
        )
    }

    fun buildShortsReelsPrompt(
        topic: String,
        duration: String,
        platform: String,
        language: String,
        style: String
    ): String {
        return """
Create an original short-form vertical video script for $platform:
- Topic: $topic
- Target Duration: $duration
- Platform: $platform
- Language: $language
- Style: $style

Important: Keep the output 100% original. Do NOT generate copyrighted song lyrics or long copyrighted text. Do NOT claim guaranteed virality.

Please structure your output using these exact numbered section headers in $language:
1. Hook: (Attention-grabbing opening in the first 3 seconds)
2. Scene/Beat Outline: (Visual scene-by-scene breakdown timed for $duration)
3. Voiceover Script: (Exact spoken lines for the creator)
4. On-Screen Text Suggestions: (Short text overlays for key moments)
5. Caption: (Engaging social caption to accompany the video)
6. CTA: (Clear call to action at the end)
""".trimIndent()
    }

    fun parseShortsReelsScript(
        projectId: String,
        topic: String,
        duration: String,
        platform: String,
        language: String,
        raw: String
    ): ShortVideoScriptResult {
        val hook = extractSection(raw, listOf("1. Hook", "Hook", "1."))
        val scenes = extractSection(raw, listOf("2. Scene/Beat Outline", "Scene/Beat Outline", "Scene Outline", "Beat Outline", "2."))
        val voiceover = extractSection(raw, listOf("3. Voiceover Script", "Voiceover Script", "Voiceover", "Body", "3."))
        val onScreen = extractSection(raw, listOf("4. On-Screen Text Suggestions", "On-Screen Text", "4."))
        val caption = extractSection(raw, listOf("5. Caption", "Caption", "5."))
        val cta = extractSection(raw, listOf("6. CTA", "CTA", "Call to Action", "Ending", "6."))

        return ShortVideoScriptResult(
            projectId = projectId,
            topic = topic,
            duration = duration,
            platform = platform,
            language = language,
            hook = hook.ifBlank { raw.lines().firstOrNull { it.isNotBlank() } ?: "Quick tip on $topic!" },
            sceneBeatOutline = scenes.ifBlank { "Scene 1 (0-5s): Visual hook\nScene 2 (5-45s): Core demonstration\nScene 3 (45-60s): Summary & CTA" },
            voiceoverScript = voiceover.ifBlank { raw },
            onScreenTextSuggestions = onScreen.ifBlank { "Key takeaway: $topic" },
            caption = caption.ifBlank { "Exploring $topic in $duration!" },
            cta = cta.ifBlank { "Follow and share your thoughts below!" },
            rawOutput = raw
        )
    }

    fun buildTitleGeneratorPrompt(
        topic: String,
        platform: String,
        language: String,
        count: Int
    ): String {
        return """
Generate $count original, compelling title ideas for the following topic:
- Topic: $topic
- Platform: $platform
- Language: $language
- Number of Titles: $count

Requirements:
- Return a numbered list (1. to $count.) of original title suggestions in $language.
- Present them strictly as creative title suggestions; do NOT claim that any title guarantees views or virality.
""".trimIndent()
    }

    fun parseTitleSuggestions(
        projectId: String,
        topic: String,
        platform: String,
        language: String,
        raw: String
    ): TitleSuggestionsResult {
        val titles = raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.lowercase().startsWith("here are") }
            .map {
                it.replace(Regex("^\\d+[.)]\\s*"), "")
                    .removePrefix("-")
                    .removePrefix("*")
                    .trim()
                    .removeSurrounding("\"")
            }
            .filter { it.length > 3 }
            .ifEmpty { listOf(raw.trim()) }

        return TitleSuggestionsResult(
            projectId = projectId,
            topic = topic,
            platform = platform,
            language = language,
            titles = titles,
            rawOutput = raw
        )
    }

    fun buildCaptionGeneratorPrompt(
        topic: String,
        platform: String,
        tone: String,
        language: String,
        length: String
    ): String {
        return """
Generate an original social media caption with these parameters:
- Topic: $topic
- Platform: $platform
- Tone: $tone
- Language: $language
- Length: $length

Structure your response with these two sections:
1. Caption: (The main engaging social media caption in $language)
2. Optional CTA: (A friendly call-to-action question or prompt for comments)
""".trimIndent()
    }

    fun parseCaptionResult(
        projectId: String,
        topic: String,
        platform: String,
        language: String,
        raw: String
    ): CaptionResult {
        val caption = extractSection(raw, listOf("1. Caption", "Caption", "1.")).ifBlank { raw }
        val cta = extractSection(raw, listOf("2. Optional CTA", "Optional CTA", "CTA", "Call to Action", "2."))
        return CaptionResult(
            projectId = projectId,
            topic = topic,
            platform = platform,
            language = language,
            caption = caption.trim(),
            cta = cta.trim(),
            rawOutput = raw
        )
    }

    fun buildHashtagPrompt(
        topic: String,
        platform: String,
        language: String
    ): String {
        return """
Generate relevant hashtag suggestions for:
- Topic: $topic
- Platform: $platform
- Language: $language

Requirements:
- Provide 12 to 20 relevant hashtag suggestions starting with '#'.
- Present them clearly as relevant suggestions for topic discovery, NOT guaranteed trending hashtags.
""".trimIndent()
    }

    fun parseHashtagResult(
        projectId: String,
        topic: String,
        platform: String,
        language: String,
        raw: String
    ): HashtagResult {
        val regex = Regex("#[\\p{L}\\p{N}_]+")
        val extracted = regex.findAll(raw).map { it.value }.distinct().toList()
        val finalTags = if (extracted.isNotEmpty()) {
            extracted
        } else {
            raw.split(Regex("[\\s,]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { if (it.startsWith("#")) it else "#${it.replace(Regex("[^\\p{L}\\p{N}_]"), "")}" }
                .filter { it.length > 1 }
        }
        return HashtagResult(
            projectId = projectId,
            topic = topic,
            platform = platform,
            language = language,
            hashtags = finalTags,
            rawOutput = raw
        )
    }

    fun buildContentIdeasPrompt(
        nicheOrTopic: String,
        platform: String,
        audience: String,
        count: Int,
        language: String
    ): String {
        return """
Generate $count original content ideas for:
- Niche / Topic: $nicheOrTopic
- Platform: $platform
- Target Audience: ${audience.ifBlank { "General audience" }}
- Language: $language

For each idea (Idea 1 to Idea $count), provide these exact 4 fields:
Idea Title: ...
Short Description: ...
Suggested Format: ...
Suggested Hook: ...
""".trimIndent()
    }

    fun parseContentIdeasResult(
        projectId: String,
        nicheOrTopic: String,
        platform: String,
        audience: String,
        raw: String
    ): ContentIdeasResult {
        val items = mutableListOf<ContentIdeaItem>()
        val blocks = raw.split(Regex("(?=Idea\\s*\\d+|(?=\\b1\\.\\s)|(?=Idea Title:))", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (block in blocks) {
            val title = extractLineValue(block, listOf("Idea Title", "Title"))
            val desc = extractLineValue(block, listOf("Short Description", "Description"))
            val format = extractLineValue(block, listOf("Suggested Format", "Format"))
            val hook = extractLineValue(block, listOf("Suggested Hook", "Hook"))
            if (title.isNotBlank() || desc.isNotBlank()) {
                items.add(
                    ContentIdeaItem(
                        title = title.ifBlank { "$nicheOrTopic Concept" },
                        shortDescription = desc.ifBlank { block.take(160) },
                        suggestedFormat = format.ifBlank { "$platform Video / Post" },
                        suggestedHook = hook.ifBlank { "Here is what most people miss about $nicheOrTopic..." }
                    )
                )
            }
        }

        if (items.isEmpty()) {
            items.add(
                ContentIdeaItem(
                    title = "$nicheOrTopic Deep Dive",
                    shortDescription = raw.take(240),
                    suggestedFormat = platform,
                    suggestedHook = "Everything you need to know about $nicheOrTopic"
                )
            )
        }

        return ContentIdeasResult(
            projectId = projectId,
            nicheOrTopic = nicheOrTopic,
            platform = platform,
            audience = audience,
            ideas = items,
            rawOutput = raw
        )
    }

    fun buildBlogWriterPrompt(
        topic: String,
        audience: String,
        language: String,
        tone: String,
        approxLength: String
    ): String {
        return """
Write an original blog article with the following details:
- Topic: $topic
- Target Audience: ${audience.ifBlank { "General readers" }}
- Language: $language
- Tone: $tone
- Approximate Length: $approxLength

Important: Generate 100% original wording. Do NOT reproduce copyrighted articles or textbook passages.

Please structure your output using these exact numbered section headers in $language:
1. Title: (Compelling original blog title)
2. Introduction: (Engaging opening paragraphs introducing the topic)
3. Section Outline: (Bullet-point outline of the article sections)
4. Main Article: (Well-structured body sections with clear subheadings)
5. Conclusion: (Key takeaways and closing summary)
""".trimIndent()
    }

    fun parseBlogArticleResult(
        projectId: String,
        topic: String,
        language: String,
        raw: String
    ): BlogArticleResult {
        val title = extractSection(raw, listOf("1. Title", "Title", "1.")).lines().firstOrNull { it.isNotBlank() } ?: topic
        val intro = extractSection(raw, listOf("2. Introduction", "Introduction", "2."))
        val outline = extractSection(raw, listOf("3. Section Outline", "Section Outline", "Outline", "3."))
        val main = extractSection(raw, listOf("4. Main Article", "Main Article", "Article Body", "4.")).ifBlank { raw }
        val conclusion = extractSection(raw, listOf("5. Conclusion", "Conclusion", "5."))

        return BlogArticleResult(
            projectId = projectId,
            topic = topic,
            language = language,
            title = title.removePrefix("#").trim(),
            introduction = intro.ifBlank { "An in-depth look at $topic." },
            sectionOutline = outline.ifBlank { "- Overview\n- Core Principles\n- Practical Applications" },
            mainArticle = main,
            conclusion = conclusion.ifBlank { "In summary, understanding $topic helps you apply these insights effectively." },
            rawOutput = raw
        )
    }

    fun buildRewritePrompt(
        userText: String,
        action: String,
        language: String
    ): String {
        return """
Transform the following user-provided text using the requested action.
- Action: $action
- Target Language: $language

Rules:
- Only transform the text supplied below by the user.
- Preserve the user's core factual meaning while applying "$action" in $language.
- Do not add unrelated or copyrighted material.

User Text:
\"\"\"
$userText
\"\"\"
""".trimIndent()
    }

    private fun extractSection(text: String, headers: List<String>): String {
        val lines = text.lines()
        var capturing = false
        val buffer = StringBuilder()

        val allKnownHeaders = listOf(
            "1. Title Ideas", "2. Hook", "3. Introduction", "4. Main Content", "5. Call to Action", "6. Closing",
            "1. Hook", "2. Scene/Beat Outline", "3. Voiceover Script", "4. On-Screen Text Suggestions", "5. Caption", "6. CTA",
            "1. Caption", "2. Optional CTA",
            "1. Title", "2. Introduction", "3. Section Outline", "4. Main Article", "5. Conclusion"
        )

        for (line in lines) {
            val cleaned = line.trim().removePrefix("#").removePrefix("##").removePrefix("###").replace("**", "").trim()

            val matchesTarget = headers.any { h ->
                cleaned.startsWith("$h:", ignoreCase = true) ||
                    cleaned.equals(h, ignoreCase = true) ||
                    cleaned.startsWith("$h -", ignoreCase = true)
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
                val hitsAnotherHeader = allKnownHeaders.any { other ->
                    !headers.any { h -> other.equals(h, ignoreCase = true) } &&
                        (cleaned.startsWith("$other:", ignoreCase = true) || cleaned.equals(other, ignoreCase = true))
                } || Regex("^[1-6]\\.\\s+[A-Za-z /\\-]+:.*").matches(cleaned)

                if (hitsAnotherHeader) {
                    break
                }
                buffer.appendLine(line)
            }
        }
        return buffer.toString().trim()
    }

    private fun extractLineValue(block: String, keys: List<String>): String {
        for (line in block.lines()) {
            val cleaned = line.trim().removePrefix("-").removePrefix("*").replace("**", "").trim()
            for (k in keys) {
                if (cleaned.startsWith("$k:", ignoreCase = true)) {
                    return cleaned.substringAfter(":").trim()
                }
            }
        }
        return ""
    }
}
