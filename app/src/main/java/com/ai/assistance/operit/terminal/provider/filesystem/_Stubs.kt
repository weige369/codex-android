package com.ai.assistance.operit.terminal.provider.filesystem

class FileInfo(
    val name: String = "",
    val isDirectory: Boolean = false,
    val size: Long = 0L,
    val permissions: String = "rwxr-xr-x",
    val lastModified: String = "0",
    val path: String = ""
)

class FileOperationResult(
    val success: Boolean = true,
    val message: String = ""
)

open class FileSystemProvider {
    constructor() {}
    fun exists(path: String): Boolean = false
    fun isDirectory(path: String): Boolean = false
    fun isFile(path: String): Boolean = false
    fun readFile(path: String): String = ""
    fun readFileBytes(path: String): ByteArray = ByteArray(0)
    fun readFileWithLimit(path: String, limit: Int): String = ""
    fun readFileSample(path: String, sampleSize: Int = 4096): String = ""
    fun getFileSize(path: String): Long = 0
    fun getLineCount(): Int = 0
    fun getLineCount(path: String): Int = 0
    fun readFileLines(path: String, startLine: Int, endLine: Int): String {
        val content = readFile(path)
        if (content.isEmpty()) return ""
        val lines = content.lines()
        if (startLine < 1 || endLine < startLine || startLine > lines.size) return ""
        val fromIndex = (startLine - 1).coerceIn(0, lines.size - 1)
        val toIndex = endLine.coerceIn(fromIndex, lines.size)
        return lines.subList(fromIndex, toIndex).joinToString("\n")
    }
    fun writeFile(path: String, content: String, append: Boolean = false): FileOperationResult = FileOperationResult()
    fun writeFileBytes(path: String, bytes: ByteArray): FileOperationResult = FileOperationResult()
    fun createDirectory(path: String, createParents: Boolean = true): FileOperationResult = FileOperationResult()
    fun delete(path: String, recursive: Boolean = false): FileOperationResult = FileOperationResult()
    fun move(from: String, to: String, overwrite: Boolean = false): FileOperationResult = FileOperationResult()
    fun copy(from: String, to: String, overwrite: Boolean = false): FileOperationResult = FileOperationResult()
    fun listDirectory(path: String): List<FileInfo> = emptyList()
    fun findFiles(basePath: String, pattern: String, maxDepth: Int = Int.MAX_VALUE, caseInsensitive: Boolean = false): List<String> = emptyList()
    fun getFileInfo(path: String): FileInfo = FileInfo(name = path.substringAfterLast('/'))
    fun permissions(path: String): String = ""
    fun lastModified(path: String): String = "0"
    fun isTextLike(path: String, sampleSize: Int = 4096): Boolean = true
}
