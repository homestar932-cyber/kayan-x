package com.kayanx.android.fs.policy

import com.kayanx.android.fs.model.DangerousOp
import com.kayanx.android.fs.model.DocumentId
import com.kayanx.android.fs.model.ErrorCode
import com.kayanx.android.fs.model.FileResult
import com.kayanx.android.fs.model.LogicalRoot

/**
 * Android Security Layer owns the final decision.
 * The LLM proposes; this policy decides.
 *
 * Rules (non-negotiable):
 * - No absolute paths ever reach the agent.
 * - Delete / overwrite / move-outside require explicit confirmation.
 * - Recursive delete is always blocked unless explicitly enabled + confirmed.
 * - Max read size is enforced (prevents context explosion).
 * - Write size limits are enforced.
 */
class FilePolicy(
    private val maxReadBytes: Long = 512 * 1024,          // 512 KB
    private val maxWriteBytes: Long = 2 * 1024 * 1024,    // 2 MB
    private val allowRecursiveDelete: Boolean = false,
    private val confirmationRequired: Set<DangerousOp> = setOf(
        DangerousOp.DELETE,
        DangerousOp.OVERWRITE,
        DangerousOp.MOVE_OUTSIDE,
        DangerousOp.RECURSIVE_DELETE
    )
) {

    fun checkList(root: LogicalRoot, id: DocumentId): FileResult? = null // always allowed if tree granted

    fun checkRead(id: DocumentId, sizeHint: Long): FileResult? {
        if (sizeHint > maxReadBytes) {
            return FileResult.Failure(
                ErrorCode.POLICY_REJECTED,
                "File exceeds max read size ($maxReadBytes bytes). Requested: $sizeHint"
            )
        }
        return null
    }

    fun checkWrite(id: DocumentId, contentSize: Long, exists: Boolean): FileResult? {
        if (contentSize > maxWriteBytes) {
            return FileResult.Failure(
                ErrorCode.POLICY_REJECTED,
                "Write exceeds max size ($maxWriteBytes bytes)"
            )
        }
        if (exists && confirmationRequired.contains(DangerousOp.OVERWRITE)) {
            return FileResult.NeedsConfirmation(
                DangerousOp.OVERWRITE,
                id,
                "Overwrite existing file (size=$contentSize)"
            )
        }
        return null
    }

    fun checkDelete(id: DocumentId, isDirectory: Boolean, recursive: Boolean): FileResult? {
        if (recursive) {
            if (!allowRecursiveDelete) {
                return FileResult.Failure(
                    ErrorCode.POLICY_REJECTED,
                    "Recursive delete is disabled by policy"
                )
            }
            if (confirmationRequired.contains(DangerousOp.RECURSIVE_DELETE)) {
                return FileResult.NeedsConfirmation(
                    DangerousOp.RECURSIVE_DELETE,
                    id,
                    "Recursive delete of directory"
                )
            }
        }
        if (confirmationRequired.contains(DangerousOp.DELETE)) {
            return FileResult.NeedsConfirmation(
                DangerousOp.DELETE,
                id,
                if (isDirectory) "Delete directory" else "Delete file"
            )
        }
        return null
    }

    fun checkMove(source: DocumentId, dest: DocumentId, crossingRoots: Boolean): FileResult? {
        if (crossingRoots && confirmationRequired.contains(DangerousOp.MOVE_OUTSIDE)) {
            return FileResult.NeedsConfirmation(
                DangerousOp.MOVE_OUTSIDE,
                source,
                "Move across logical roots: $source → $dest"
            )
        }
        return null
    }

    fun checkCreateDir(id: DocumentId): FileResult? = null

    fun checkCopy(source: DocumentId, dest: DocumentId): FileResult? = null
}
