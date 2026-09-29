package com.example.domain.ai

/**
 * Backend Entitlement & Usage System for Nexa AI
 * Handles plans: Free, Plus, Pro with backend-configurable limits.
 */
enum class PlanTier(
    val tierId: String,
    val displayName: String,
    val monthlyMessageLimit: Int,
    val documentLimit: Int,
    val imageLimit: Int,
    val rateLimitPerMinute: Int
) {
    FREE(
        tierId = "free",
        displayName = "Free Tier",
        monthlyMessageLimit = 50,
        documentLimit = 5,
        imageLimit = 5,
        rateLimitPerMinute = 10
    ),
    PLUS(
        tierId = "plus",
        displayName = "Plus Brain",
        monthlyMessageLimit = 500,
        documentLimit = 50,
        imageLimit = 50,
        rateLimitPerMinute = 30
    ),
    PRO(
        tierId = "pro",
        displayName = "Pro Brain",
        monthlyMessageLimit = 2500,
        documentLimit = 250,
        imageLimit = 250,
        rateLimitPerMinute = 60
    );

    companion object {
        fun fromPlanName(name: String?): PlanTier {
            if (name == null) return FREE
            return entries.firstOrNull {
                it.tierId.equals(name, ignoreCase = true) ||
                it.displayName.equals(name, ignoreCase = true) ||
                it.name.equals(name, ignoreCase = true)
            } ?: FREE
        }
    }
}

data class UserEntitlementStatus(
    val planTier: PlanTier,
    val monthlyMessageLimit: Int,
    val usedThisPeriod: Int,
    val remainingThisPeriod: Int,
    val isLimitReached: Boolean,
    val resetDateEpochMs: Long
)

sealed class EntitlementCheckResult {
    data class Allowed(val status: UserEntitlementStatus) : EntitlementCheckResult()
    data class LimitReached(val message: String, val status: UserEntitlementStatus) : EntitlementCheckResult()
}

class EntitlementManager(
    // Backend configurable overrides if needed
    private val customPlanLimits: Map<PlanTier, Int> = emptyMap()
) {
    companion object {
        const val LIMIT_REACHED_MESSAGE =
            "You've reached your current AI usage limit. Upgrade your plan or try again when your usage resets."
    }

    fun getEffectiveMessageLimit(tier: PlanTier): Int {
        return customPlanLimits[tier] ?: tier.monthlyMessageLimit
    }

    fun evaluateEntitlement(
        planName: String?,
        usedMessagesThisPeriod: Int,
        renewalDateEpochMs: Long
    ): EntitlementCheckResult {
        val tier = PlanTier.fromPlanName(planName)
        val limit = getEffectiveMessageLimit(tier)
        val remaining = (limit - usedMessagesThisPeriod).coerceAtLeast(0)
        val isLimitReached = usedMessagesThisPeriod >= limit

        val status = UserEntitlementStatus(
            planTier = tier,
            monthlyMessageLimit = limit,
            usedThisPeriod = usedMessagesThisPeriod,
            remainingThisPeriod = remaining,
            isLimitReached = isLimitReached,
            resetDateEpochMs = renewalDateEpochMs
        )

        return if (isLimitReached) {
            EntitlementCheckResult.LimitReached(LIMIT_REACHED_MESSAGE, status)
        } else {
            EntitlementCheckResult.Allowed(status)
        }
    }
}
