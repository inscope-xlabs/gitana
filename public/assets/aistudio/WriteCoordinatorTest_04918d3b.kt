package com.inscopexlabs.gitana.gitbridge.core.application

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WriteCoordinatorTest {

    @Test
    fun `same repository and path execute in submission order`() = runTest {
        val coordinator = InMemoryWriteCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val first = launch {
            coordinator.serialize("example-org/sandbox", "src/App.kt") {
                events += "first-start"
                firstEntered.complete(Unit)
                releaseFirst.await()
                events += "first-end"
            }
        }
        firstEntered.await()

        val second = launch {
            coordinator.serialize("example-org/sandbox", "src/App.kt") {
                events += "second"
            }
        }

        yield()
        assertEquals(listOf("first-start"), events)

        releaseFirst.complete(Unit)
        first.join()
        second.join()

        assertEquals(listOf("first-start", "first-end", "second"), events)
    }

    @Test
    fun `different paths can execute concurrently`() = runTest {
        val coordinator = InMemoryWriteCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = launch {
            coordinator.serialize("example-org/sandbox", "src/One.kt") {
                firstEntered.complete(Unit)
                release.await()
            }
        }
        firstEntered.await()

        val second = launch {
            coordinator.serialize("example-org/sandbox", "src/Two.kt") {
                secondEntered.complete(Unit)
            }
        }

        secondEntered.await()
        assertTrue(second.isCompleted)
        release.complete(Unit)
        first.join()
    }

    @Test
    fun `different repositories can execute concurrently`() = runTest {
        val coordinator = InMemoryWriteCoordinator()
        val firstEntered = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = launch {
            coordinator.serialize("example-org/one", "README.md") {
                firstEntered.complete(Unit)
                release.await()
            }
        }
        firstEntered.await()

        val second = launch {
            coordinator.serialize("example-org/two", "README.md") {
                secondEntered.complete(Unit)
            }
        }

        secondEntered.await()
        assertTrue(second.isCompleted)
        release.complete(Unit)
        first.join()
    }

    @Test
    fun `lock is released when operation throws`() = runTest {
        val coordinator = InMemoryWriteCoordinator()

        var caught = false
        try {
            coordinator.serialize("example-org/sandbox", "src/App.kt") {
                throw IllegalStateException("simulated adapter failure")
            }
        } catch (_: IllegalStateException) {
            caught = true
        }
        assertTrue(caught)

        val result = coordinator.serialize("example-org/sandbox", "src/App.kt") { "completed" }
        assertEquals("completed", result)
    }

    @Test
    fun `cancelled waiter does not poison the key`() = runTest {
        val coordinator = InMemoryWriteCoordinator()
        val entered = CompletableDeferred<Unit>()
        val holder = launch {
            coordinator.serialize("example-org/sandbox", "src/App.kt") {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        holder.cancelAndJoin()

        val result = coordinator.serialize("example-org/sandbox", "src/App.kt") { "next" }
        assertEquals("next", result)
    }

    @Test
    fun `blank repository and path are rejected`() = runTest {
        val coordinator = InMemoryWriteCoordinator()

        var blankRepositoryRejected = false
        try {
            coordinator.serialize(" ", "src/App.kt") { Unit }
        } catch (_: IllegalArgumentException) {
            blankRepositoryRejected = true
        }
        assertTrue(blankRepositoryRejected)

        var blankPathRejected = false
        try {
            coordinator.serialize("example-org/sandbox", "  ") { Unit }
        } catch (_: IllegalArgumentException) {
            blankPathRejected = true
        }
        assertTrue(blankPathRejected)
    }
}
