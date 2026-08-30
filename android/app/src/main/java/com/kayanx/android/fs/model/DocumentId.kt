package com.kayanx.android.fs.model

/**
 * Safe opaque identifier for any file/directory resource.
 * The Agent and LLM never see raw paths or URIs.
 * Only the FileBridge and Policy layers can resolve a DocumentId.
 */
@JvmInline
value class DocumentId(val value: String) {
    init {
        require(value.isNotBlank()) { "DocumentId cannot be blank" }
        require(!value.contains("..")) { "DocumentId must not contain path traversal" }
    }

    companion object {
        fun root(treeKey: String) = DocumentId("root:$treeKey")
        fun child(parent: DocumentId, name: String) = DocumentId("${parent.value}/$name")
    }
}

/**
 * Logical roots the agent is allowed to operate on.
 * Mapped to persisted Tree URIs under the hood.
 */
enum class LogicalRoot {
    DOWNLOADS,
    WORKSPACE,
    INTERNAL_CACHE,
    INTERNAL_FILES
}

/**
 * Metadata returned to the Agent (never raw filesystem paths).
 */
data class FileInfo(
    val id: DocumentId,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long,
    val mimeType: String?,
    val canRead: Boolean,
    val canWrite: Boolean
)

/**
 * Result of a file operation. Always deterministic.
 */
sealed class FileResult {
    data class Success(val info: FileInfo? = null, val content: String? = null, val message: String = "ok") : FileResult()
    data class Failure(val code: ErrorCode, val message: String) : FileResult()
    data class NeedsConfirmation(val operation: DangerousOp, val target: DocumentId, val details: String) : FileResult()
}

enum class ErrorCode {
    PERMISSION_DENIED,
    NOT_FOUND,
    ALREADY_EXISTS,
    NOT_A_DIRECTORY,
    NOT_A_FILE,
    IO_ERROR,
    POLICY_REJECTED,
    INVALID_ID,
    TREE_NOT_GRANTED,
    CONFIRMATION_REQUIRED,
    UNSUPPORTED
}

enum class DangerousOp {
    DELETE,
    OVERWRITE,
    MOVE_OUTSIDE,
    RECURSIVE_DELETE
}
