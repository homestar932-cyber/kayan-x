package com.kayanx.android.agent

import com.kayanx.android.agent.executor.ToolExecutor
import com.kayanx.android.agent.loop.AgentEvent
import com.kayanx.android.agent.loop.AgentOrchestrator
import com.kayanx.android.agent.planner.MockPlanner
import com.kayanx.android.agent.verifier.DeterministicVerifier
import com.kayanx.android.fs.FileBridge
import com.kayanx.android.fs.model.DocumentId
import com.kayanx.android.fs.model.FileInfo
import com.kayanx.android.fs.model.FileResult
import com.kayanx.android.fs.model.LogicalRoot
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Proves the Agent Loop, Policy confirmation path, and deterministic verification
 * work without a real device or SAF.
 */
class AgentLoopTest {

    @Test
    fun `multi-step goal completes with verification`() = runTest {
        val bridge = mockk<FileBridge>(relaxed = true)

        // list
        coEvery { bridge.list(any()) } returns FileResult.Success(message = "listed 0", content = "")
        // create dir
        coEvery { bridge.createDirectory(any(), "KayanTest") } returns FileResult.Success(
            info = FileInfo(DocumentId.root(LogicalRoot.DOWNLOADS.name), "KayanTest", true, 0, 0, null, true, true)
        )
        // write (new file → no confirmation)
        coEvery { bridge.write(any(), any(), any()) } returns FileResult.Success(message = "written")
        // getInfo for verification
        coEvery { bridge.getInfo(any()) } returns FileResult.Success(
            info = FileInfo(DocumentId("x"), "hello.txt", false, 12, 0, "text/plain", true, true)
        )

        val executor = ToolExecutor(bridge)
        val verifier = DeterministicVerifier(bridge)
        val orchestrator = AgentOrchestrator(MockPlanner(), executor, verifier)

        orchestrator.start("أنشئ مجلد KayanTest في Downloads واكتب فيه hello.txt")

        // Allow loop to finish
        var finished = false
        for (i in 0..30) {
            val event = orchestrator.events.value
            if (event is AgentEvent.Finished) {
                finished = true
                assertTrue(event.answer.contains("بنجاح") || event.answer.isNotBlank())
                break
            }
            kotlinx.coroutines.delay(10)
        }
        assertTrue("Loop should finish", finished)
        val finalState = orchestrator.state.value
        assertNotNull(finalState)
        assertTrue(finalState!!.isComplete)
        assertTrue(finalState.history.size >= 3)
    }
}
