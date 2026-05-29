package com.ai.assistance.operit.data.model
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
object MyObjectBox {
    fun builder() = BoxStoreBuilder()
}
class BoxStoreBuilder {
    private var context: Any? = null; private var directory: java.io.File? = null
    fun androidContext(context: Any): BoxStoreBuilder { this.context = context; return this }
    fun directory(directory: java.io.File): BoxStoreBuilder { this.directory = directory; return this }
    fun build(): BoxStore = BoxStore()
}

object Memory_ {
    val title: Memory_ get() = this; val content: Memory_ get() = this; val source: Memory_ get() = this
    val credibility: Memory_ get() = this; val importance: Memory_ get() = this
    val createdAt: Memory_ get() = this; val updatedAt: Memory_ get() = this; val id: Memory_ get() = this
    val uuid: Memory_ get() = this; val folderPath: Memory_ get() = this
    val isDocumentNode: Memory_ get() = this; val archived: Memory_ get() = this
    fun contains(text: String): Memory_ = this
    fun equal(text: String): Memory_ = this
    fun equal(value: Any?): Memory_ = this
    fun equal(value: Any?, order: QueryBuilder.StringOrder?): Memory_ = this
    fun equal(other: Memory_?): Memory_ = this
    fun isNull(): Memory_ = this; fun isNotNull(): Memory_ = this
    fun lessThan(value: Any?): Memory_ = this; fun greaterThan(value: Any?): Memory_ = this
    fun orderDesc(): Memory_ = this; fun orderAsc(): Memory_ = this
    fun or(other: Memory_?): Memory_ = this
    fun and(other: Memory_?): Memory_ = this
}
object MemoryAutoSaveCandidate_ {
    val content: MemoryAutoSaveCandidate_ get() = this; val type: MemoryAutoSaveCandidate_ get() = this
    val createdAt: MemoryAutoSaveCandidate_ get() = this; val status: MemoryAutoSaveCandidate_ get() = this
    val chatId: MemoryAutoSaveCandidate_ get() = this
    fun contains(text: String): MemoryAutoSaveCandidate_ = this
    fun equal(text: String): MemoryAutoSaveCandidate_ = this
    fun equal(value: Any?): MemoryAutoSaveCandidate_ = this
    fun or(other: MemoryAutoSaveCandidate_?): MemoryAutoSaveCandidate_ = this
    fun and(other: MemoryAutoSaveCandidate_?): MemoryAutoSaveCandidate_ = this
    fun isNull(): MemoryAutoSaveCandidate_ = this; fun isNotNull(): MemoryAutoSaveCandidate_ = this
}
object MemoryTag_ {
    val name: MemoryTag_ get() = this
    fun contains(text: String): MemoryTag_ = this
    fun equal(text: String): MemoryTag_ = this
    fun equal(value: Any?): MemoryTag_ = this
    fun equal(value: Any?, order: QueryBuilder.StringOrder?): MemoryTag_ = this
}
