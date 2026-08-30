package com.kayanx.android.fs.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kayanx.android.fs.model.LogicalRoot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.treeDataStore: DataStore<Preferences> by preferencesDataStore(name = "kayan_trees")

/**
 * Stores persisted URI permissions for LogicalRoot trees.
 * Survives process death and reboots (as long as the system keeps the grant).
 */
class PersistedTreeStore(private val context: Context) {

    private fun keyFor(root: LogicalRoot) = stringPreferencesKey("tree_uri_${root.name}")

    suspend fun saveTree(root: LogicalRoot, uri: Uri, takePersistable: Boolean = true) {
        if (takePersistable) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: SecurityException) {
                // Some providers do not support persistable; we still store the URI for the session.
            }
        }
        context.treeDataStore.edit { prefs ->
            prefs[keyFor(root)] = uri.toString()
        }
    }

    suspend fun getTreeUri(root: LogicalRoot): Uri? {
        val str = context.treeDataStore.data.map { it[keyFor(root)] }.first()
        return str?.let { Uri.parse(it) }
    }

    fun observeTreeUri(root: LogicalRoot): Flow<Uri?> =
        context.treeDataStore.data.map { prefs ->
            prefs[keyFor(root)]?.let { Uri.parse(it) }
        }

    suspend fun clearTree(root: LogicalRoot) {
        val existing = getTreeUri(root)
        if (existing != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    existing,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
        }
        context.treeDataStore.edit { it.remove(keyFor(root)) }
    }

    suspend fun hasTree(root: LogicalRoot): Boolean = getTreeUri(root) != null
}
