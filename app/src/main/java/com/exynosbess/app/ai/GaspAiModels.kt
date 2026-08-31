package com.exynosbess.app.ai

enum class AiRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class AiAction {
    EXPLAIN,
    PERFORMANCE,
    EFFICIENCY,
    FIND_PROBLEMS,
    SUGGEST_GPU_TUNING,
    CUSTOM
}

data class GaspAiContext(
    val dtsFileName: String,
    val dtbIndex: Int,
    val nodeName: String,
    val nodeContent: String,
    val userQuestion: String? = null
)

data class AiSuggestion(
    val title: String,
    val explanation: String,
    val risk: AiRiskLevel,
    val originalText: String? = null,
    val proposedText: String? = null,
    val canApply: Boolean = false
)
