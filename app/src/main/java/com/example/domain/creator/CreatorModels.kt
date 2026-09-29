package com.example.domain.creator

/**
 * Creator AI Domain Models & Constants (Step 4)
 */
enum class CreatorRequestType(val id: String, val displayLabel: String) {
    CONTENT_GENERATE("content_generate", "Content Generator"),
    YOUTUBE_SCRIPT("youtube_script", "YouTube Script"),
    SHORTS_SCRIPT("shorts_script", "YouTube Shorts Script"),
    REELS_SCRIPT("reels_script", "Instagram Reels Script"),
    TITLE_GENERATE("title_generate", "Title Generator"),
    CAPTION_GENERATE("caption_generate", "Caption Generator"),
    HASHTAG_SUGGEST("hashtag_suggest", "Hashtag Assistant"),
    CONTENT_IDEAS("content_ideas", "Content Ideas"),
    BLOG_GENERATE("blog_generate", "Blog Writer"),
    CONTENT_REWRITE("content_rewrite", "Content Rewriter");

    companion object {
        fun fromId(id: String): CreatorRequestType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CONTENT_GENERATE
        }
    }
}

object CreatorOptions {
    val PLATFORMS = listOf(
        "YouTube",
        "YouTube Shorts",
        "Instagram",
        "Instagram Reels",
        "Blog",
        "Facebook",
        "General Social Media"
    )

    val SHORT_VIDEO_PLATFORMS = listOf(
        "YouTube Shorts",
        "Instagram Reels"
    )

    val LANGUAGES = listOf(
        "English",
        "Hindi",
        "Hinglish"
    )

    val TONES = listOf(
        "Educational",
        "Professional",
        "Friendly",
        "Entertaining",
        "Motivational",
        "Storytelling",
        "Simple"
    )

    val CONTENT_TYPES = listOf(
        "Social Post",
        "Video Outline",
        "Carousel / Thread",
        "Article / Essay",
        "Community Update",
        "Story Sequence"
    )

    val LENGTHS = listOf(
        "Short",
        "Medium",
        "Long"
    )

    val YOUTUBE_DURATIONS = listOf(
        "30 seconds (Shorts)",
        "60 seconds (Shorts)",
        "3-5 minutes",
        "8-10 minutes",
        "15+ minutes"
    )

    val SHORT_VIDEO_DURATIONS = listOf(
        "15 seconds",
        "30 seconds",
        "45 seconds",
        "60 seconds",
        "90 seconds"
    )

    val SCRIPT_STYLES = listOf(
        "Educational",
        "Storytelling",
        "Tutorial / Step-by-Step",
        "Entertaining",
        "Motivational",
        "Commentary / Review"
    )

    val BLOG_LENGTHS = listOf(
        "Short (~400 words)",
        "Medium (~800 words)",
        "Long (~1200+ words)"
    )

    val REWRITE_ACTIONS = listOf(
        "Simplify",
        "Improve clarity",
        "Make professional",
        "Make friendly",
        "Make concise",
        "Translate",
        "Create social caption",
        "Create short summary"
    )
}

data class CreatorHomeCardItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconName: String,
    val targetTab: String,
    val presetPlatform: String? = null
)

data class GeneratedContentResult(
    val projectId: String,
    val title: String,
    val platform: String,
    val contentType: String,
    val language: String,
    val tone: String,
    val content: String
)

data class YouTubeScriptResult(
    val projectId: String,
    val topic: String,
    val duration: String,
    val language: String,
    val titleIdeas: List<String>,
    val hook: String,
    val introduction: String,
    val mainContent: String,
    val callToAction: String,
    val closing: String,
    val rawOutput: String
)

data class ShortVideoScriptResult(
    val projectId: String,
    val topic: String,
    val duration: String,
    val platform: String,
    val language: String,
    val hook: String,
    val sceneBeatOutline: String,
    val voiceoverScript: String,
    val onScreenTextSuggestions: String,
    val caption: String,
    val cta: String,
    val rawOutput: String
)

data class TitleSuggestionsResult(
    val projectId: String,
    val topic: String,
    val platform: String,
    val language: String,
    val titles: List<String>,
    val rawOutput: String
)

data class CaptionResult(
    val projectId: String,
    val topic: String,
    val platform: String,
    val language: String,
    val caption: String,
    val cta: String,
    val rawOutput: String
)

data class HashtagResult(
    val projectId: String,
    val topic: String,
    val platform: String,
    val language: String,
    val hashtags: List<String>,
    val disclaimerNote: String = "Suggested relevant hashtags for discovery — not guaranteed trending hashtags.",
    val rawOutput: String
)

data class ContentIdeaItem(
    val title: String,
    val shortDescription: String,
    val suggestedFormat: String,
    val suggestedHook: String
)

data class ContentIdeasResult(
    val projectId: String,
    val nicheOrTopic: String,
    val platform: String,
    val audience: String,
    val ideas: List<ContentIdeaItem>,
    val rawOutput: String
)

data class BlogArticleResult(
    val projectId: String,
    val topic: String,
    val language: String,
    val title: String,
    val introduction: String,
    val sectionOutline: String,
    val mainArticle: String,
    val conclusion: String,
    val rawOutput: String
)

data class RewriteResult(
    val projectId: String,
    val action: String,
    val language: String,
    val originalText: String,
    val transformedText: String
)
