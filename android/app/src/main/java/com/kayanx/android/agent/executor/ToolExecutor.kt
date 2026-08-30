package com.kayanx.android.agent.executor

import com.kayanx.android.agent.state.ToolCall
import com.kayanx.android.fs.FileBridge
import com.kayanx.android.fs.model.DocumentId
import com.kayanx.android.fs.model.FileResult

/**
 * Maps tool names to FileBridge calls.
 * All operations pass through the bridge (and therefore through Policy).
 */
class ToolExecutor(private val bridge: FileBridge) {

    suspend fun execute(call: ToolCall, confirmed: Boolean = false): FileResult {
        return when (call.name) {
            "list_files" -> {
                val id = DocumentId(call.args.getValue("id"))
                bridge.list(id)
            }
            "read_file" -> {
                val id = DocumentId(call.args.getValue("id"))
                bridge.read(id)
            }
            "write_file" -> {
                val id = DocumentId(call.args.getValue("id"))
                val content = call.args.getValue("content")
                bridge.write(id, content, confirmed = confirmed)
            }
            "create_directory" -> {
                val parent = DocumentId(call.args.getValue("parent_id"))
                val name = call.args.getValue("name")
                bridge.createDirectory(parent, name)
            }
            "delete" -> {
                val id = DocumentId(call.args.getValue("id"))
                val recursive = call.args["recursive"]?.toBoolean() ?: false
                bridge.delete(id, recursive, confirmed = confirmed)
            }
            "copy" -> {
                val source = DocumentId(call.args.getValue("source_id"))
                val dest = DocumentId(call.args.getValue("dest_parent_id"))
                val newName = call.args["new_name"]
                bridge.copy(source, dest, newName)
            }
            "move" -> {
                val source = DocumentId(call.args.getValue("source_id"))
                val dest = DocumentId(call.args.getValue("dest_parent_id"))
                val newName = call.args["new_name"]
                bridge.move(source, dest, newName, confirmed = confirmed)
            }
            "get_file_info" -> {
                val id = DocumentId(call.args.getValue("id"))
                bridge.getInfo(id)
            }
            "search" -> {
                val root = DocumentId(call.args.getValue("root_id"))
                val query = call.args.getValue("query")
                bridge.search(root, query)
            }
            else -> FileResult.Failure(
                com.kayanx.android.fs.model.ErrorCode.UNSUPPORTED,
                "Unknown tool: ${call.name}"
            )
        }
    }
}
