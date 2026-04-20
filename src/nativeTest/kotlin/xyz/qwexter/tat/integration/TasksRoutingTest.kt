package xyz.qwexter.tat.integration

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.LocalDateTime
import xyz.qwexter.tat.models.Task
import xyz.qwexter.tat.models.TaskId
import xyz.qwexter.tat.models.TaskName
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus
import xyz.qwexter.tat.repository.TasksRepository
import xyz.qwexter.tat.utils.todoApp
import xyz.qwexter.tat.utils.unimplementedTasksRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TasksRoutingTest {

    private val now = LocalDateTime.parse("2026-01-01T10:00:00")
    private val later = LocalDateTime.parse("2026-01-02T10:00:00")
    private val past = LocalDateTime.parse("2025-12-31T10:00:00")

    private val testTasks = listOf(

        // 1. Basic task (default values)
        Task(
            id = TaskId("task-1"),
            name = TaskName("Buy milk"),
            description = null,
            status = TaskStatus.Todo,
            priority = TaskPriority.Low,
            deadline = null,
            createdAt = now,
            updatedAt = null,
            deletedAt = null,
        ),

        // 2. Task with description + deadline
        Task(
            id = TaskId("task-2"),
            name = TaskName("Write report"),
            description = "Quarterly results",
            status = TaskStatus.Todo,
            priority = TaskPriority.High,
            deadline = later,
            createdAt = now,
            updatedAt = null,
            deletedAt = null,
        ),

        // 3. Completed task
        Task(
            id = TaskId("task-3"),
            name = TaskName("Go to gym"),
            description = null,
            status = TaskStatus.Done,
            priority = TaskPriority.Medium,
            deadline = null,
            createdAt = now,
            updatedAt = later,
            deletedAt = null,
        ),

        // 4. Overdue task (still Todo)
        Task(
            id = TaskId("task-4"),
            name = TaskName("Pay bills"),
            description = null,
            status = TaskStatus.Todo,
            priority = TaskPriority.High,
            deadline = past,
            createdAt = now,
            updatedAt = null,
            deletedAt = null,
        ),

        // 5. Soft-deleted task (should NOT appear in GET)
        Task(
            id = TaskId("task-5"),
            name = TaskName("Old archived task"),
            description = null,
            status = TaskStatus.Done,
            priority = TaskPriority.Low,
            deadline = null,
            createdAt = past,
            updatedAt = past,
            deletedAt = now,
        ),
    )

    @Test
    fun `GET tasks return 200 when empty`() = todoApp {
        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("tasks")
        val actual = response.body<List<Task>>()
        assertTrue(actual.isEmpty())
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType())
    }

    @Test
    fun `GET tasks return 200 and items when non-empty`() = todoApp(
        tasksRepository = TasksRepository.buildInMemory(testTasks),
    ) {
        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("tasks")
        assertEquals(HttpStatusCode.OK, response.status)
        val actual = response.body<List<Task>>()
        assertEquals(
            testTasks.filter { it.deletedAt == null },
            actual,
        )
        assertEquals(ContentType.Application.Json, response.contentType())
    }

    @Test
    fun `GET tasks return 500 when error happened`() = todoApp(
        tasksRepository = unimplementedTasksRepository,
    ) {
        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("tasks")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

}
