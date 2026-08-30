package com.kayanx.android.fs

import com.kayanx.android.fs.model.DangerousOp
import com.kayanx.android.fs.model.DocumentId
import com.kayanx.android.fs.model.FileResult
import com.kayanx.android.fs.policy.FilePolicy
import org.junit.Assert.*
import org.junit.Test

class FilePolicyTest {

    private val policy = FilePolicy()

    @Test
    fun `overwrite requires confirmation`() {
        val result = policy.checkWrite(DocumentId("root:DOWNLOADS/a.txt"), 100, exists = true)
        assertTrue(result is FileResult.NeedsConfirmation)
        assertEquals(DangerousOp.OVERWRITE, (result as FileResult.NeedsConfirmation).operation)
    }

    @Test
    fun `delete requires confirmation`() {
        val result = policy.checkDelete(DocumentId("root:DOWNLOADS/a.txt"), isDirectory = false, recursive = false)
        assertTrue(result is FileResult.NeedsConfirmation)
    }

    @Test
    fun `recursive delete is rejected by default`() {
        val result = policy.checkDelete(DocumentId("root:DOWNLOADS/dir"), isDirectory = true, recursive = true)
        assertTrue(result is FileResult.Failure)
    }

    @Test
    fun `large read is rejected`() {
        val result = policy.checkRead(DocumentId("root:DOWNLOADS/big.bin"), sizeHint = 10 * 1024 * 1024)
        assertTrue(result is FileResult.Failure)
    }

    @Test
    fun `small write of new file is allowed`() {
        val result = policy.checkWrite(DocumentId("root:DOWNLOADS/new.txt"), 50, exists = false)
        assertNull(result)
    }
}
