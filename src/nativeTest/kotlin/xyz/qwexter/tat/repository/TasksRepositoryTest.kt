package xyz.qwexter.tat.repository

import app.cash.sqldelight.driver.native.inMemoryDriver
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import xyz.qwexter.db.TatDatabase
import xyz.qwexter.tat.models.Task
import xyz.qwexter.tat.models.TaskId
import xyz.qwexter.tat.models.TaskName
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TasksRepositoryTest {

    private fun dbRepo() = TasksRepository.create(db = TatDatabase(inMemoryDriver(TatDatabase.Schema)))

    @Test
    fun `InMemory createTask records createdAt as Instant`() = runBlocking {
        val before = Clock.System.now()
        val repo = TasksRepository.buildInMemory(emptyList())
        val task = repo.createTask(name = "test", description = null, deadline = null)
        val after = Clock.System.now()

        assertTrue(task.createdAt >= before)
        assertTrue(task.createdAt <= after)
    }

    @Test
    fun `Db createTask round-trips createdAt without loss`() = runBlocking {
        val before = Clock.System.now().toEpochMilliseconds().let { Instant.fromEpochMilliseconds(it) }
        val repo = dbRepo()
        repo.createTask(name = "test", description = null, deadline = null)
        val after = Clock.System.now().toEpochMilliseconds().let { Instant.fromEpochMilliseconds(it) }

        val loaded = repo.allActiveTasks().single()
        assertTrue(loaded.createdAt >= before)
        assertTrue(loaded.createdAt <= after)
    }

    @Test
    fun `Db createTask round-trips deadline without TZ shift`() = runBlocking {
        val deadline = LocalDateTime.parse("2026-06-15T23:59:59")
        val repo = dbRepo()
        repo.createTask(name = "test", description = null, deadline = deadline)

        assertEquals(deadline, repo.allActiveTasks().single().deadline)
    }

    @Test
    fun `createTask sets updatedAt and deletedAt to null`() = runBlocking {
        val inMemory = TasksRepository.buildInMemory(emptyList())
        val fromDb = dbRepo()

        val memTask = inMemory.createTask(name = "test", description = null, deadline = null)
        assertNull(memTask.updatedAt)
        assertNull(memTask.deletedAt)

        fromDb.createTask(name = "test", description = null, deadline = null)
        val dbTask = fromDb.allActiveTasks().single()
        assertNull(dbTask.updatedAt)
        assertNull(dbTask.deletedAt)
    }

    @Test
    fun `allActiveTasks returns empty list when no tasks created`() = runBlocking {
        assertTrue(TasksRepository.buildInMemory(emptyList()).allActiveTasks().isEmpty())
        assertTrue(dbRepo().allActiveTasks().isEmpty())
    }

    @Test
    fun `InMemory allActiveTasks excludes soft-deleted tasks`() = runBlocking {
        val active = Task(
            id = TaskId("a"),
            name = TaskName("Active"),
            description = null,
            status = TaskStatus.Todo,
            priority = TaskPriority.Low,
            deadline = null,
            createdAt = Clock.System.now(),
            updatedAt = null,
            deletedAt = null,
        )
        val deleted = active.copy(
            id = TaskId("b"),
            name = TaskName("Deleted"),
            deletedAt = Clock.System.now(),
        )
        val repo = TasksRepository.buildInMemory(listOf(active, deleted))
        val result = repo.allActiveTasks()

        assertEquals(1, result.size)
        assertEquals("a", result.single().id.id)
    }
}
