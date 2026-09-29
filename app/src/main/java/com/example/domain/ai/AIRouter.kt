package com.example.domain.ai

/**
 * AI Router Foundation (Section 6)
 *
 * Defines the classification architecture and data models for routing
 * queries into specialized AI workspaces.
 */
data class RoutingClassificationResult(
    val recommendedModule: AIModule,
    val confidence: Float,
    val detectedKeywords: List<String>,
    val routingExplanation: String,
    val suggestedPromptRefinement: String? = null,
    val requiresBackendOrchestrator: Boolean = true
)

interface AIRouterService {
    /**
     * Inspects input query and classifies the intent into the appropriate workspace.
     */
    fun classifyIntent(query: String): RoutingClassificationResult
}

class DefaultAIRouterService : AIRouterService {
    override fun classifyIntent(query: String): RoutingClassificationResult {
        val lower = query.lowercase().trim()

        val studyKeywords = listOf("math", "physics", "solve", "exam", "quiz", "homework", "formula", "study", "mcq", "algebra")
        val creatorKeywords = listOf(
            "script", "write", "youtube", "shorts", "reels", "instagram", "tiktok",
            "story", "caption", "poem", "blog", "content", "title", "hashtag", "hashtags",
            "hook", "creator", "rewrite"
        )
        val docKeywords = listOf("pdf", "document", "summarize", "contract", "invoice", "paper", "report", "extract", "table")
        val langKeywords = listOf(
            "translate", "translation", "hinglish", "hindi", "bengali", "marathi", "gujarati",
            "tamil", "telugu", "kannada", "malayalam", "punjabi", "urdu", "spanish", "french",
            "german", "japanese", "korean", "arabic", "grammar", "vocabulary", "language", "detect language"
        )
        val bizKeywords = listOf("business", "startup", "revenue", "pitch", "sales", "marketing", "roi", "client")
        val farmKeywords = listOf("crop", "farm", "soil", "fertilizer", "harvest", "weather", "kisan", "seed")
        val familyKeywords = listOf("grocery", "household", "chore", "meal plan", "pantry", "home", "budget")

        return when {
            studyKeywords.any { lower.contains(it) } -> {
                val matched = studyKeywords.filter { lower.contains(it) }
                RoutingClassificationResult(
                    recommendedModule = AIModule.STUDY_AI,
                    confidence = 0.88f,
                    detectedKeywords = matched,
                    routingExplanation = "Matched study & academic tutoring intent based on terms: $matched"
                )
            }
            creatorKeywords.any { lower.contains(it) } -> {
                val matched = creatorKeywords.filter { lower.contains(it) }
                RoutingClassificationResult(
                    recommendedModule = AIModule.CREATOR_AI,
                    confidence = 0.85f,
                    detectedKeywords = matched,
                    routingExplanation = "Matched creative media & scripting intent based on terms: $matched"
                )
            }
            docKeywords.any { lower.contains(it) } -> {
                val matched = docKeywords.filter { lower.contains(it) }
                RoutingClassificationResult(
                    recommendedModule = AIModule.DOCUMENT_AI,
                    confidence = 0.90f,
                    detectedKeywords = matched,
                    routingExplanation = "Matched document parsing & analytical synthesis intent based on terms: $matched"
                )
            }
            langKeywords.any { lower.contains(it) } -> {
                val matched = langKeywords.filter { lower.contains(it) }
                RoutingClassificationResult(
                    recommendedModule = AIModule.LANGUAGE_AI,
                    confidence = 0.92f,
                    detectedKeywords = matched,
                    routingExplanation = "Matched multilingual translation & linguistic learning intent based on terms: $matched"
                )
            }
            farmKeywords.any { lower.contains(it) } -> {
                val matched = farmKeywords.filter { lower.contains(it) }
                RoutingClassificationResult(
                    recommendedModule = AIModule.KISAN_AI,
                    confidence = 0.80f,
                    detectedKeywords = matched,
                    routingExplanation = "Matched agricultural intent (Kisan AI - Coming Soon)"
                )
            }
            familyKeywords.any { lower.contains(it) } -> {
                val matched = familyKeywords.filter { lower.contains(it) }
                RoutingClassificationResult(
                    recommendedModule = AIModule.GHAR_MANAGER_AI,
                    confidence = 0.80f,
                    detectedKeywords = matched,
                    routingExplanation = "Matched household planning intent (GharManager AI - Coming Soon)"
                )
            }
            bizKeywords.any { lower.contains(it) } -> {
                val matched = bizKeywords.filter { lower.contains(it) }
                RoutingClassificationResult(
                    recommendedModule = AIModule.BUSINESS_AI,
                    confidence = 0.82f,
                    detectedKeywords = matched,
                    routingExplanation = "Matched enterprise workflow intent (Business AI - Coming Soon)"
                )
            }
            else -> {
                RoutingClassificationResult(
                    recommendedModule = AIModule.GENERAL_AI,
                    confidence = 0.70f,
                    detectedKeywords = emptyList(),
                    routingExplanation = "Routing to Nexa AI Unified General Intelligence Workspace"
                )
            }
        }
    }
}
