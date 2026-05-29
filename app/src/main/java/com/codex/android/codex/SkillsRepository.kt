package com.codex.android.codex

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Codex Skills 数据仓库。
 *
 * 管理 Skills 的安装列表、可用市场、本地缓存。
 * 数据来源: Codex CLI exec-server API + 本地持久化缓存。
 */
class SkillsRepository(private val context: Context) {

    companion object {
        private const val TAG = "SkillsRepository"
        private const val PREFS_NAME = "codex_skills"
        private const val KEY_INSTALLED = "installed_skills"
        private const val KEY_MARKETS = "skill_markets"
        
        // 默认 Skill 市场
        private val defaultMarkets = listOf(
            SkillMarket("built-in", "内置 Skills", "Codex 内置的官方 Skills")
        )

        // 默认可用 Skills（从 Codex exec-server 获取之前的默认值）
        private val defaultAvailableSkills = listOf(
            SkillInfo("codex-sub-agents", "子代理系统", "让 Codex 创建和管理子代理", "1.0.0", "built-in"),
            SkillInfo("imagegen", "图像生成", "使用 DALL-E 等模型生成图像", "1.0.0", "built-in"),
            SkillInfo("openai-docs", "OpenAI 文档", "查询最新的 OpenAI API 文档", "1.0.0", "built-in"),
            SkillInfo("plugin-creator", "插件创建器", "创建和管理 Codex 插件", "1.0.0", "built-in"),
            SkillInfo("skill-creator", "Skill 创建器", "创建自定义 Skills", "1.0.0", "built-in"),
            SkillInfo("search-codex-chats", "聊天搜索", "搜索过去的 Codex 对话记录", "1.0.0", "built-in"),
            SkillInfo("composio-cli", "工具集成", "集成 200+ 外部工具", "1.0.0", "built-in"),
            SkillInfo("flightclaw", "航班查询", "搜索和跟踪航班价格", "1.0.0", "built-in"),
            SkillInfo("telegram-bridge-send", "Telegram", "通过 Telegram 发送通知", "1.0.0", "built-in"),
            SkillInfo("android-shell", "Android Shell", "在 Android 上执行 Shell 命令", "1.0.0", "built-in"),
            SkillInfo("android-file-mcp", "文件 MCP", "Android 文件系统 MCP 工具", "1.0.0", "built-in"),
        )
    }

    data class SkillInfo(
        val name: String,
        val title: String,
        val description: String,
        val version: String,
        val market: String,
        val installed: Boolean = false
    )

    data class SkillMarket(
        val id: String,
        val name: String,
        val description: String
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 获取已安装的 Skill 列表
     */
    fun getInstalledSkills(): List<SkillInfo> {
        val json = prefs.getString(KEY_INSTALLED, "[]") ?: "[]"
        val installedNames = try {
            JSONArray(json).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
        
        return defaultAvailableSkills.filter { it.name in installedNames }
            .map { it.copy(installed = true) }
    }

    /**
     * 获取所有可用的 Skill 列表
     */
    fun getAvailableSkills(): List<SkillInfo> {
        val installedNames = getInstalledNames()
        return defaultAvailableSkills.map { 
            it.copy(installed = it.name in installedNames) 
        }
    }

    /**
     * 获取 Skill 市场列表
     */
    fun getMarkets(): List<SkillMarket> = defaultMarkets

    /**
     * 安装 Skill
     */
    suspend fun installSkill(name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val installed = getInstalledNames().toMutableList()
            if (name !in installed) {
                installed.add(name)
                saveInstalledNames(installed)
                Log.i(TAG, "Skill 已安装: $name")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "安装 Skill 失败: $name", e)
            false
        }
    }

    /**
     * 卸载 Skill
     */
    suspend fun removeSkill(name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val installed = getInstalledNames().toMutableList()
            installed.remove(name)
            saveInstalledNames(installed)
            Log.i(TAG, "Skill 已卸载: $name")
            true
        } catch (e: Exception) {
            Log.e(TAG, "卸载 Skill 失败: $name", e)
            false
        }
    }

    /**
     * 添加自定义 Skill 市场
     */
    suspend fun addMarket(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val markets = getMarketsJson().toMutableList()
            val marketId = "custom_${markets.size}"
            markets.add(JSONObject().apply {
                put("id", marketId)
                put("url", url)
            })
            prefs.edit().putString(KEY_MARKETS, JSONArray(markets).toString()).apply()
            Log.i(TAG, "市场已添加: $url")
            true
        } catch (e: Exception) {
            Log.e(TAG, "添加市场失败", e)
            false
        }
    }

    private fun getInstalledNames(): List<String> {
        val json = prefs.getString(KEY_INSTALLED, "[]") ?: "[]"
        return try {
            JSONArray(json).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveInstalledNames(names: List<String>) {
        prefs.edit().putString(KEY_INSTALLED, JSONArray(names).toString()).apply()
    }

    private fun getMarketsJson(): List<Any> {
        val json = prefs.getString(KEY_MARKETS, "[]") ?: "[]"
        return try {
            JSONArray(json).let { arr ->
                (0 until arr.length()).map { arr.get(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
