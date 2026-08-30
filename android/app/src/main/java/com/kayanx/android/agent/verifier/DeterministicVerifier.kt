package com.kayanx.android.agent.verifier

import com.kayanx.android.agent.state.ToolCall
import com.kayanx.android.agent.state.VerificationResult
import com.kayanx.android.fs.FileBridge
import com.kayanx.android.fs.model.DocumentId
import com.kayanx.android.fs.model.FileResult

/**
 * Deterministic verification — never relies on the LLM's claim of success.
 * Requirement 15.
 */
class DeterministicVerifier(private val bridge: FileBridge) {

    suspend fun verify(tool: ToolCall, result: FileResult): VerificationResult {
        if (result is FileResult.Failure) {
            return VerificationResult.Failed("Tool itself reported failure: ${result.message}")
        }
        if (result is FileResult.NeedsConfirmation) {
            return VerificationResult.NotApplicable
        }

        return when (tool.name) {
            "create_directory" -> verifyExists(tool.args["parent_id"], tool.args["name"], expectDir = true)
            "write_file" -> verifyExists(tool.args["id"], null, expectDir = false)
            "delete" -> verifyGone(tool.args["id"])
            "move", "copy" -> verifyExists(tool.args["dest_parent_id"], tool.args["new_name"] ?: tool.args["name"], expectDir = false)
            "read_file" -> {
                if (result is FileResult.Success && result.content != null) {
                    VerificationResult.Passed("content length=${result.content.length}")
                } else VerificationResult.Failed("no content returned")
            }
            else -> VerificationResult.NotApplicable
        }
    }

    private suspend fun verifyExists(parentOrId: String?, name: String?, expectDir: Boolean): VerificationResult {
        if (parentOrId == null) return VerificationResult.Failed("missing id")
        val id = if (name != null) DocumentId.child(DocumentId(parentOrId), name) else DocumentId(parentOrId)
        return when (val info = bridge.getInfo(id)) {
            is FileResult.Success -> {
                if (info.info == null) VerificationResult.Failed("info null")
                else if (info.info.isDirectory == expectDir) VerificationResult.Passed("exists, isDirectory=${info.info.isDirectory}")
                else VerificationResult.Failed("type mismatch: expectedDir=$expectDir actual=${info.info.isDirectory}")
            }
            else -> VerificationResult.Failed("does not exist after operation")
        }
    }

    private suspend fun verifyGone(idStr: String?): VerificationResult {
        if (idStr == null) return VerificationResult.Failed("missing id")
        return when (bridge.getInfo(DocumentId(idStr))) {
            is FileResult.Failure -> VerificationResult.Passed("no longer exists")
            else -> VerificationResult.Failed("still exists after delete")
        }
    }
}
