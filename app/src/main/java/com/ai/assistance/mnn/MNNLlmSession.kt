package com.ai.assistance.mnn

import java.io.File

/**
 * Stub MNNLlmSession — native MNN LLM engine is not available on this build.
 * create() always returns null so that MNNProvider gracefully degrades.
 */
class MNNLlmSession private constructor() {

    companion object {
        @JvmStatic
        fun create(
            modelDir: String,
            backendType: String = "cpu",
            threadNum: Int = 4,
            precision: String = "low",
            memory: String = "low",
            tmpPath: String? = null
        ): MNNLlmSession? = null
    }

    fun tokenize(text: String): IntArray = IntArray(0)
    fun detokenize(token: Int): String = ""
    fun countTokens(text: String): Int = 0
    fun countTokensWithHistory(history: List<Pair<String, String>>): Int = 0
    fun generateStream(history: List<Pair<String, String>>, maxTokens: Int = -1, onToken: (String) -> Boolean): Boolean = false
    fun generateStreamStructured(messagesJson: String, toolsJson: String? = null, maxTokens: Int = -1, onToken: (String) -> Boolean): Boolean = false
    fun chat(userContent: String, maxTokens: Int = -1, onToken: (String) -> Boolean): Boolean = false
    fun reset() {}
    fun cancel() {}
    fun setConfig(configJson: String): Boolean = false
    fun setMaxNewTokens(maxNewTokens: Int): Boolean = false
    fun setSystemPrompt(systemPrompt: String): Boolean = false
    fun setAssistantPromptTemplate(template: String): Boolean = false
    fun setThinkingMode(enabled: Boolean): Boolean = false
    fun setAudioDataCallback(callback: Any?): Boolean = false
    fun generateWavform(): Boolean = false
    fun release() {}
    fun getModelPath(): String = ""
    fun isReleased(): Boolean = true
}
