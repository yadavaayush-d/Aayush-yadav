package com.example.domain.ai

enum class AIModule(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconName: String,
    val isComingSoon: Boolean,
    val systemPromptRole: String
) {
    GENERAL_AI(
        id = "general",
        title = "General AI",
        subtitle = "Central unified intelligence for everyday workflows and general queries.",
        iconName = "AutoAwesome",
        isComingSoon = false,
        systemPromptRole = "You are Nexa AI, a unified AI brain assisting users across multifaceted knowledge domains."
    ),
    STUDY_AI(
        id = "study",
        title = "Study AI",
        subtitle = "Learn, solve, practice questions, MCQs, and generate revision plans.",
        iconName = "School",
        isComingSoon = false,
        systemPromptRole = "You are Nexa Study AI, specialized in academic tutoring, problem-solving, and structured curriculum revision."
    ),
    CREATOR_AI(
        id = "creator",
        title = "Creator AI",
        subtitle = "Create video scripts, engaging stories, social captions, and content plans.",
        iconName = "Brush",
        isComingSoon = false,
        systemPromptRole = "You are Nexa Creator AI, specialized in creative copy, storytelling, viral scripting, and content strategizing."
    ),
    DOCUMENT_AI(
        id = "documents",
        title = "Document AI",
        subtitle = "Understand PDFs and complex reports, extract summaries and Q&A.",
        iconName = "Description",
        isComingSoon = false,
        systemPromptRole = "You are Nexa Document AI, specialized in semantic document parsing, table extraction, and dense report summarization."
    ),
    LANGUAGE_AI(
        id = "language",
        title = "Language AI",
        subtitle = "Translate with cultural nuances, practice conversation, and learn languages.",
        iconName = "Translate",
        isComingSoon = false,
        systemPromptRole = "You are Nexa Language AI, a multilingual linguist assisting in idiomatic translation and conversational grammar."
    ),
    BUSINESS_AI(
        id = "business",
        title = "Business AI",
        subtitle = "Enterprise workflows, market intelligence, customer support, and financial reports.",
        iconName = "BusinessCenter",
        isComingSoon = true,
        systemPromptRole = "You are Nexa Business AI, assisting enterprise operations and commercial workflows."
    ),
    SKILL_PATH_AI(
        id = "skills",
        title = "SkillPath AI",
        subtitle = "Personalized career roadmaps, step-by-step competencies, and progress checks.",
        iconName = "TrendingUp",
        isComingSoon = true,
        systemPromptRole = "You are Nexa SkillPath AI, mapping structured career trajectories and skill milestones."
    ),
    KISAN_AI(
        id = "farming",
        title = "Kisan AI",
        subtitle = "Agricultural records, weather impact, crop health, and market price insights.",
        iconName = "Agriculture",
        isComingSoon = true,
        systemPromptRole = "You are Nexa Kisan AI, serving farmers with agricultural best practices and agronomy telemetry."
    ),
    GHAR_MANAGER_AI(
        id = "family",
        title = "GharManager AI",
        subtitle = "Household planning, grocery coordination, pantry tracking, and shared tasks.",
        iconName = "Home",
        isComingSoon = true,
        systemPromptRole = "You are Nexa GharManager AI, assisting families in household organization and task coordination."
    ),
    LOCAL_BIZ_AI(
        id = "local_biz",
        title = "LocalBiz AI",
        subtitle = "Local-business marketing, review management, and customer messaging workflows.",
        iconName = "Storefront",
        isComingSoon = true,
        systemPromptRole = "You are Nexa LocalBiz AI, empowering neighbourhood merchants with local SEO and customer CRM."
    );

    companion object {
        fun fromId(id: String?): AIModule {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.title.equals(id, ignoreCase = true) }
                ?: GENERAL_AI
        }
    }
}
