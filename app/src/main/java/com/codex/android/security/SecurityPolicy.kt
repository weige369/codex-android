package com.codex.android.security

import android.content.Context
import java.io.File

/**
 * 安全等级策略。
 *
 * 用户可在设置里选择 AI 工具的权限等级，控制高权限操作（任意 Shell 执行、文件系统访问）。
 * 等级存储在 SharedPreferences("codex_prefs") 的 "security_level" 键。
 *
 * 等级按能力从严到宽递增（保证每个等级的安全承诺真实可信）：
 * - SAFE  (安全)  : 禁用任意 Shell 工具，文件读写仅限应用工作目录（沙箱内）。最严格。
 * - STANDARD (标准): 禁用任意 Shell 工具，但允许全盘文件读写。默认等级。
 * - FULL  (完全)  : 无限制，AI 可执行任意 Shell、访问全盘文件。面向开发者/高级用户。
 *
 * 说明：文件沙箱仅在「安全」等级生效。一旦放开 Shell（完全等级），AI 可通过
 * `cat`/`cp` 等命令绕过文件沙箱，因此 Shell 与文件沙箱不会在同一等级同时启用，
 * 避免给用户「文件受保护」的虚假承诺。
 */
object SecurityPolicy {

    const val PREFS_NAME = "codex_prefs"
    const val KEY_SECURITY_LEVEL = "security_level"

    const val LEVEL_FULL = "full"
    const val LEVEL_STANDARD = "standard"
    const val LEVEL_SAFE = "safe"

    const val DEFAULT_LEVEL = LEVEL_STANDARD

    /** 读取当前安全等级。 */
    fun currentLevel(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SECURITY_LEVEL, DEFAULT_LEVEL) ?: DEFAULT_LEVEL
    }

    /** 是否允许执行任意 Shell 命令（仅 FULL 允许）。 */
    fun isShellAllowed(context: Context): Boolean {
        return currentLevel(context) == LEVEL_FULL
    }

    /** 是否允许访问全盘文件（STANDARD / FULL 允许；仅 SAFE 限制在沙箱目录）。 */
    fun isFullFileAccessAllowed(context: Context): Boolean {
        return currentLevel(context) != LEVEL_SAFE
    }

    /**
     * 沙箱根目录：应用私有外部目录（无则回退到内部 filesDir）。
     * SAFE 等级下，文件工具只能访问该目录子树。
     */
    fun sandboxRoot(context: Context): File {
        return context.getExternalFilesDir(null) ?: context.filesDir
    }

    /**
     * 校验文件访问是否被当前安全等级允许。
     *
     * @return 允许时返回 null；被拒绝时返回一段 JSON 错误字符串，调用方可直接返回给 AI。
     */
    fun checkFileAccess(context: Context, path: String): String? {
        if (isFullFileAccessAllowed(context)) return null

        val root = try {
            sandboxRoot(context).canonicalFile
        } catch (e: Exception) {
            sandboxRoot(context).absoluteFile
        }
        val target = try {
            File(path).canonicalFile
        } catch (e: Exception) {
            File(path).absoluteFile
        }

        val rootPath = root.path
        val targetPath = target.path
        val withinSandbox = targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)

        if (!withinSandbox) {
            return "{\"success\":false,\"error\":\"当前安全等级（${currentLevel(context)}）禁止访问沙箱外路径：$path。可在设置中调整安全等级，或将文件放入工作目录：$rootPath\"}"
        }
        return null
    }

    /** Shell 被拒绝时的标准错误响应。 */
    fun shellDeniedResponse(context: Context): String {
        return "{\"success\":false,\"error\":\"当前安全等级（${currentLevel(context)}）禁止执行任意 Shell 命令。如需执行，请在设置中将安全等级调整为「完全」。\"}"
    }
}
