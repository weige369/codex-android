package com.codex.android.diagnostics

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * 自检报告失败原因展示的回归测试。
 *
 * 覆盖 [DiagnosticsRunner.exceptionSummary] 的格式/截断逻辑，
 * 以及失败项（文件写入失败）会把异常摘要带进 detail/suggestion。
 */
class DiagnosticsRunnerTest {

    // ===== exceptionSummary 格式 =====

    @Test
    fun exceptionSummary_includesTypeAndMessage() {
        val summary = DiagnosticsRunner.exceptionSummary(IllegalStateException("boom"))
        assertEquals("IllegalStateException: boom", summary)
    }

    @Test
    fun exceptionSummary_trimsMessageWhitespace() {
        val summary = DiagnosticsRunner.exceptionSummary(IllegalStateException("  spaced  "))
        assertEquals("IllegalStateException: spaced", summary)
    }

    @Test
    fun exceptionSummary_blankMessage_showsTypeOnly() {
        assertEquals("RuntimeException", DiagnosticsRunner.exceptionSummary(RuntimeException()))
        assertEquals("RuntimeException", DiagnosticsRunner.exceptionSummary(RuntimeException("   ")))
    }

    @Test
    fun exceptionSummary_truncatesLongMessageTo200Chars() {
        val longMessage = "x".repeat(500)
        val summary = DiagnosticsRunner.exceptionSummary(IllegalArgumentException(longMessage))

        assertEquals("IllegalArgumentException: " + "x".repeat(200), summary)
        // 仅消息部分应被截断到 200 字
        assertEquals(200, summary.substringAfter(": ").length)
    }

    // ===== 失败项把异常摘要带进 detail/suggestion =====

    @Test
    fun fileWriteFailure_surfacesExceptionSummaryInResult() {
        // 把 filesDir 指向一个普通文件，使得其下无法创建目录/写入文件，
        // 从而触发真实的写入异常路径。
        val regularFile = File.createTempFile("diag-not-a-dir", ".tmp").apply { deleteOnExit() }

        val context = mock<Context>()
        whenever(context.filesDir).thenReturn(regularFile)

        val results = runBlocking { DiagnosticsRunner(context).runCategory("filesystem") }

        val writeResult = results.first { it.name == "文件写入权限" }
        assertFalse("写入失败时该项应判为不通过", writeResult.passed)

        val summaryShown = writeResult.detail.contains("Exception") ||
                writeResult.suggestion.contains("Exception")
        assertTrue(
            "异常摘要应出现在 detail 或 suggestion 中: detail=${writeResult.detail}, suggestion=${writeResult.suggestion}",
            summaryShown
        )
    }
}
