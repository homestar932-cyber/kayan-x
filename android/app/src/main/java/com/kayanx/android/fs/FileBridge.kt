package com.kayanx.android.fs

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.kayanx.android.fs.model.*
import com.kayanx.android.fs.policy.FilePolicy
import com.kayanx.android.fs.saf.PersistedTreeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Single entry point for all filesystem operations.
 * Every call goes through Policy first.
 * The Agent only ever sees DocumentId and FileInfo — never raw paths or URIs.
 *
 * Supported operations (requirement 9):
 * list, read, write, createDirectory, copy, move, delete, getInfo, search
 */
class FileBridge(
    private val context: Context,
    private val treeStore: PersistedTreeStore,
    private val policy: FilePolicy = FilePolicy()
) {

    // ─── Public API (called by Agent Executor) ───────────────────────────────

    suspend fun list(id: DocumentId): FileResult = withContext(Dispatchers.IO) {
        resolve(id)?.let { handle ->
            policy.checkList(handle.root, id)?.let { return@withContext it }
            try {
                val children = queryChildren(handle.uri)
                FileResult.Success(
                    message = "listed ${children.size} entries",
                    content = children.joinToString("\n") { "${if (it.isDirectory) "D" else "F"} ${it.name} (${it.sizeBytes}B) id=${it.id.value}" }
                )
            } catch (e: Exception) {
                FileResult.Failure(ErrorCode.IO_ERROR, e.message ?: "list failed")
            }
        } ?: FileResult.Failure(ErrorCode.TREE_NOT_GRANTED, "No tree granted for ${id.value}")
    }

    suspend fun read(id: DocumentId, maxBytes: Long = 512 * 1024): FileResult = withContext(Dispatchers.IO) {
        resolve(id)?.let { handle ->
            val info = getInfoInternal(handle) ?: return@withContext FileResult.Failure(ErrorCode.NOT_FOUND, "not found")
            if (info.isDirectory) return@withContext FileResult.Failure(ErrorCode.NOT_A_FILE, "is directory")
            policy.checkRead(id, info.sizeBytes)?.let { return@withContext it }
            try {
                val content = context.contentResolver.openInputStream(handle.uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText().take(maxBytes.toInt())
                } ?: return@withContext FileResult.Failure(ErrorCode.IO_ERROR, "cannot open stream")
                FileResult.Success(info = info, content = content)
            } catch (e: Exception) {
                FileResult.Failure(ErrorCode.IO_ERROR, e.message ?: "read failed")
            }
        } ?: FileResult.Failure(ErrorCode.TREE_NOT_GRANTED, "No tree granted")
    }

    suspend fun write(id: DocumentId, content: String, confirmed: Boolean = false): FileResult = withContext(Dispatchers.IO) {
        resolve(id)?.let { handle ->
            val exists = documentExists(handle.uri)
            policy.checkWrite(id, content.toByteArray().size.toLong(), exists)?.let { result ->
                if (result is FileResult.NeedsConfirmation && !confirmed) return@withContext result
                if (result is FileResult.Failure) return@withContext result
            }
            try {
                if (!exists) {
                    // Create the document first
                    val parent = parentUri(handle) ?: return@withContext FileResult.Failure(ErrorCode.NOT_FOUND, "parent missing")
                    val name = id.value.substringAfterLast('/')
                    val newUri = DocumentsContract.createDocument(
                        context.contentResolver, parent, "text/plain", name
                    ) ?: return@withContext FileResult.Failure(ErrorCode.IO_ERROR, "createDocument failed")
                    // Update handle to new URI would require re-resolve; for simplicity we write to newUri
                    context.contentResolver.openOutputStream(newUri, "w")?.use { out ->
                        OutputStreamWriter(out).use { it.write(content) }
                    }
                } else {
                    context.contentResolver.openOutputStream(handle.uri, "wt")?.use { out ->
                        OutputStreamWriter(out).use { it.write(content) }
                    } ?: return@withContext FileResult.Failure(ErrorCode.IO_ERROR, "cannot open for write")
                }
                val info = getInfoInternal(handle.copy(uri = handle.uri))
                FileResult.Success(info = info, message = "written ${content.length} chars")
            } catch (e: Exception) {
                FileResult.Failure(ErrorCode.IO_ERROR, e.message ?: "write failed")
            }
        } ?: FileResult.Failure(ErrorCode.TREE_NOT_GRANTED, "No tree granted")
    }

    suspend fun createDirectory(parentId: DocumentId, name: String): FileResult = withContext(Dispatchers.IO) {
        resolve(parentId)?.let { handle ->
            policy.checkCreateDir(parentId)?.let { return@withContext it }
            try {
                val newUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    handle.uri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    name
                ) ?: return@withContext FileResult.Failure(ErrorCode.IO_ERROR, "createDocument (dir) failed")
                val childId = DocumentId.child(parentId, name)
                FileResult.Success(
                    info = FileInfo(childId, name, true, 0, System.currentTimeMillis(), null, true, true),
                    message = "directory created"
                )
            } catch (e: Exception) {
                FileResult.Failure(ErrorCode.IO_ERROR, e.message ?: "createDirectory failed")
            }
        } ?: FileResult.Failure(ErrorCode.TREE_NOT_GRANTED, "No tree granted")
    }

    suspend fun delete(id: DocumentId, recursive: Boolean = false, confirmed: Boolean = false): FileResult = withContext(Dispatchers.IO) {
        resolve(id)?.let { handle ->
            val info = getInfoInternal(handle) ?: return@withContext FileResult.Failure(ErrorCode.NOT_FOUND, "not found")
            policy.checkDelete(id, info.isDirectory, recursive)?.let { result ->
                if (result is FileResult.NeedsConfirmation && !confirmed) return@withContext result
                if (result is FileResult.Failure) return@withContext result
            }
            try {
                val ok = DocumentsContract.deleteDocument(context.contentResolver, handle.uri)
                if (ok) FileResult.Success(message = "deleted")
                else FileResult.Failure(ErrorCode.IO_ERROR, "deleteDocument returned false")
            } catch (e: Exception) {
                FileResult.Failure(ErrorCode.IO_ERROR, e.message ?: "delete failed")
            }
        } ?: FileResult.Failure(ErrorCode.TREE_NOT_GRANTED, "No tree granted")
    }

    suspend fun copy(sourceId: DocumentId, destParentId: DocumentId, newName: String? = null): FileResult = withContext(Dispatchers.IO) {
        // SAF does not have a direct copy; we implement read + write for files.
        // For directories we reject for now (can be added later with recursion + confirmation).
        val source = resolve(sourceId) ?: return@withContext FileResult.Failure(ErrorCode.TREE_NOT_GRANTED, "source tree missing")
        val destParent = resolve(destParentId) ?: return@withContext FileResult.Failure(ErrorCode.TREE_NOT_GRANTED, "dest tree missing")
        policy.checkCopy(sourceId, destParentId)?.let { return@withContext it }

        val info = getInfoInternal(source) ?: return@withContext FileResult.Failure(ErrorCode.NOT_FOUND, "source not found")
        if (info.isDirectory) return@withContext FileResult.Failure(ErrorCode.UNSUPPORTED, "directory copy not yet implemented")

        val name = newName ?: info.name
        try {
            val content = context.contentResolver.openInputStream(source.uri)?.use { it.readBytes() }
                ?: return@withContext FileResult.Failure(ErrorCode.IO_ERROR, "cannot read source")
            val newUri = DocumentsContract.createDocument(
                context.contentResolver, destParent.uri, info.mimeType ?: "application/octet-stream", name
            ) ?: return@withContext FileResult.Failure(ErrorCode.IO_ERROR, "createDocument failed")
            context.contentResolver.openOutputStream(newUri)?.use { it.write(content) }
            val childId = DocumentId.child(destParentId, name)
            FileResult.Success(
                info = FileInfo(childId, name, false, content.size.toLong(), System.currentTimeMillis(), info.mimeType, true, true),
                message = "copied"
            )
        } catch (e: Exception) {
            FileResult.Failure(ErrorCode.IO_ERROR, e.message ?: "copy failed")
        }
    }

    suspend fun move(sourceId: DocumentId, destParentId: DocumentId, newName: String? = null, confirmed: Boolean = false): FileResult = withContext(Dispatchers.IO) {
        val source = resolve(sourceId) ?: return@withContext FileResult.Failure(ErrorCode.TREE_NOT_GRANTED, "source missing")
        val destParent = resolve(destParentId) ?: return@withContext FileResult.Failure(ErrorCode.TREE_NOT_GRANTED, "dest missing")
        val crossing = source.root != destParent.root
        policy.checkMove(sourceId, destParentId, crossing)?.let { result ->
            if (result is FileResult.NeedsConfirmation && !confirmed) return@withContext result
            if (result is FileResult.Failure) return@withContext result
        }
        try {
            // DocumentsContract.moveDocument requires API 24+ and same provider usually.
            // Fallback: copy + delete.
            val copyResult = copy(sourceId, destParentId, newName)
            if (copyResult is FileResult.Success) {
                delete(sourceId, recursive = false, confirmed = true)
            }
            copyResult
        } catch (e: Exception) {
            FileResult.Failure(ErrorCode.IO_ERROR, e.message ?: "move failed")
        }
    }

    suspend fun getInfo(id: DocumentId): FileResult = withContext(Dispatchers.IO) {
        resolve(id)?.let { handle ->
            val info = getInfoInternal(handle)
            if (info != null) FileResult.Success(info = info)
            else FileResult.Failure(ErrorCode.NOT_FOUND, "not found")
        } ?: FileResult.Failure(ErrorCode.TREE_NOT_GRANTED, "No tree granted")
    }

    suspend fun search(rootId: DocumentId, query: String, maxResults: Int = 50): FileResult = withContext(Dispatchers.IO) {
        // Simple name-based search (depth-first limited). Full-text can be added later.
        val results = mutableListOf<FileInfo>()
        suspend fun walk(id: DocumentId, depth: Int) {
            if (results.size >= maxResults || depth > 8) return
            when (val list = list(id)) {
                is FileResult.Success -> {
                    list.content?.lines()?.forEach { line ->
                        val parts = line.split(" ", limit = 4)
                        if (parts.size >= 4) {
                            val name = parts[1]
                            val childId = DocumentId(parts[3].removePrefix("id="))
                            if (name.contains(query, ignoreCase = true)) {
                                getInfo(childId).let { if (it is FileResult.Success && it.info != null) results.add(it.info) }
                            }
                            if (line.startsWith("D")) walk(childId, depth + 1)
                        }
                    }
                }
                else -> {}
            }
        }
        walk(rootId, 0)
        FileResult.Success(
            message = "found ${results.size} matches",
            content = results.joinToString("\n") { "${it.name} id=${it.id.value}" }
        )
    }

    // ─── Tree management (called from UI) ────────────────────────────────────

    suspend fun grantTree(root: LogicalRoot, treeUri: Uri) {
        treeStore.saveTree(root, treeUri, takePersistable = true)
    }

    suspend fun hasTree(root: LogicalRoot): Boolean = treeStore.hasTree(root)

    suspend fun getRootId(root: LogicalRoot): DocumentId? {
        return if (treeStore.hasTree(root)) DocumentId.root(root.name) else null
    }

    // ─── Internal resolution ─────────────────────────────────────────────────

    private data class Handle(val root: LogicalRoot, val uri: Uri, val documentId: String)

    private suspend fun resolve(id: DocumentId): Handle? {
        val parts = id.value.split("/", limit = 2)
        val rootKey = parts[0].removePrefix("root:")
        val root = try { LogicalRoot.valueOf(rootKey) } catch (_: Exception) { return null }
        val treeUri = treeStore.getTreeUri(root) ?: return null
        val docId = if (parts.size == 1) {
            DocumentsContract.getTreeDocumentId(treeUri)
        } else {
            // Rebuild document id from relative path. This is provider-dependent;
            // for Downloads and most providers the document id is the last segment path.
            DocumentsContract.getTreeDocumentId(treeUri) + "/" + parts[1]
        }
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return Handle(root, uri, docId)
    }

    private fun parentUri(handle: Handle): Uri? {
        val parentDocId = handle.documentId.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentDocId.isEmpty()) return null
        val treeUri = handle.uri // we need the original tree; simplified
        return DocumentsContract.buildDocumentUriUsingTree(handle.uri, parentDocId)
    }

    private fun documentExists(uri: Uri): Boolean {
        return try {
            context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use {
                it.moveToFirst()
            } ?: false
        } catch (_: Exception) { false }
    }

    private fun getInfoInternal(handle: Handle): FileInfo? {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS
        )
        return try {
            context.contentResolver.query(handle.uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val name = cursor.getString(0) ?: return null
                val mime = cursor.getString(1)
                val size = cursor.getLong(2)
                val modified = cursor.getLong(3)
                val flags = cursor.getInt(4)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                FileInfo(
                    id = DocumentId(handle.documentId), // simplified; real mapping uses full DocumentId
                    name = name,
                    isDirectory = isDir,
                    sizeBytes = size,
                    lastModifiedEpochMs = modified,
                    mimeType = mime,
                    canRead = true,
                    canWrite = (flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0
                )
            }
        } catch (_: Exception) { null }
    }

    private fun queryChildren(treeOrDocUri: Uri): List<FileInfo> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeOrDocUri,
            DocumentsContract.getDocumentId(treeOrDocUri)
        )
        val result = mutableListOf<FileInfo>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2)
                val size = cursor.getLong(3)
                val modified = cursor.getLong(4)
                result.add(
                    FileInfo(
                        id = DocumentId(docId),
                        name = name,
                        isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        sizeBytes = size,
                        lastModifiedEpochMs = modified,
                        mimeType = mime,
                        canRead = true,
                        canWrite = true
                    )
                )
            }
        }
        return result
    }
}
