package com.ai.assistance.llama

/**
 * Stub LlamaSession — native llama.cpp is not available on this build.
 * All creation methods return null / false so that LlamaProvider gracefully degrades.
 */
class LlamaSession private constructor() {

    data class Config(
        val nThreads: Int = 4,
        val nCtx: Int = 2048,
        val nBatch: Int = 512,
        val nUBatch: Int = 512,
        val nGpuLayers: Int = 0,
        val useMmap: Boolean = false,
        val flashAttention: Boolean = false,
        val kvUnified: Boolean = true,
        val offloadKqv: Boolean = false
    )

    companion object {
        fun isAvailable(): Boolean = false
        fun getUnavailableReason(): String = "llama.cpp native library not bundled in this build"
        fun create(pathModel: String, config: Config): LlamaSession? = null
    }

    fun countTokens(text: String): Int = 0
    fun generateStream(prompt: String, maxTokens: Int, onToken: (String) -> Boolean): Boolean = false
    fun applyStructuredChatTemplate(messagesJson: String, toolsJson: String?, addAssistant: Boolean): String? = null
    fun clearToolCallGrammar(): Boolean = false
    fun parseToolCallResponse(content: String): String? = null
    fun applyChatTemplate(roles: List<String>, contents: List<String>, addAssistant: Boolean): String? = null
    fun setSamplingParams(temperature: Float, topP: Float, topK: Int, repetitionPenalty: Float, frequencyPenalty: Float, presencePenalty: Float, penaltyLastN: Int = 64): Boolean = false
    fun cancel() {}
    fun release() {}
}
