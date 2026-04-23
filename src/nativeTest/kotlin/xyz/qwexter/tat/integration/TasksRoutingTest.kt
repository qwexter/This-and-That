package xyz.qwexter.tat.integration

import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import xyz.qwexter.tat.models.Task
import xyz.qwexter.tat.models.TaskId
import xyz.qwexter.tat.models.TaskName
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus
import xyz.qwexter.tat.routing.ActiveTask
import xyz.qwexter.tat.routing.AddTask
import xyz.qwexter.tat.routing.ApiTaskPriority
import xyz.qwexter.tat.routing.ApiTaskStatus
import xyz.qwexter.tat.routing.toApi
import xyz.qwexter.tat.utils.createTasksRepositoryDb
import xyz.qwexter.tat.utils.createTasksRepositoryInMemoryList
import xyz.qwexter.tat.utils.todoApp
import xyz.qwexter.tat.utils.unimplementedTasksRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TasksRoutingTest {

    private val now = Instant.parse("2026-01-01T10:00:00Z")
    private val later = Instant.parse("2026-01-02T10:00:00Z")
    private val past = Instant.parse("2025-12-31T10:00:00Z")

    private val deadlineSoon = LocalDateTime.parse("2026-01-02T10:00:00")
    private val deadlineOverdue = LocalDateTime.parse("2025-12-31T10:00:00")

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
            deadline = deadlineSoon,
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
            deadline = deadlineOverdue,
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
    fun `GET tasks returns empty list when repository is empty`() =
        todoApp(
            taskRepositoryFactory = createTasksRepositoryInMemoryList(),
        ) {
            val response = client.get("tasks")
            val actual = response.body<List<ActiveTask>>()
            assertTrue(actual.isEmpty())

            assertEquals(expected = HttpStatusCode.OK, actual = response.status)
            assertEquals(expected = ContentType.Application.Json, actual = response.contentType())
        }

    @Test
    fun `GET tasks returns only active tasks`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryDb(initial = testTasks),
    ) {
        val response = client.get("tasks")
        val actual = response.body<List<ActiveTask>>()
        assertEquals(
            expected = testTasks.filter { it.deletedAt == null }.map { it.toApi() },
            actual = actual,
        )
        assertTrue { actual.none { it.id == "task-5" } } // soft-deleted task

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType())
    }

    @Test
    fun `GET tasks returns 500 when repository fails`() = todoApp(
        taskRepositoryFactory = unimplementedTasksRepository,
    ) {
        assertEquals(
            expected = HttpStatusCode.InternalServerError,
            actual = client.get("tasks").status,
        )
    }

    @Test
    fun `POST tasks returns Created and valid model`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(),
    ) {
        val input = AddTask(
            name = "Task to add",
            description = "Description for the task",
            status = ApiTaskStatus.Todo,
            priority = ApiTaskPriority.Low,
            deadline = null,
        )
        val response = client.post("tasks") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(input)
        }
        val activeTask = response.body<ActiveTask>()

        assertEquals("Task to add", activeTask.name)
        assertEquals("Description for the task", activeTask.description)
        assertEquals(ApiTaskPriority.Low, activeTask.priority)
        assertEquals(null, activeTask.deadline)

        assertEquals(ContentType.Application.Json, response.contentType())
        assertEquals(HttpStatusCode.Created, response.status)

        val tasks = client.get("tasks").body<List<ActiveTask>>()
        assertTrue(activeTask in tasks)
    }

    @Test
    fun `POST tasks returns 400 when name is empty`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(),
    ) {
        val response = client.post("tasks") {
            contentType(ContentType.Application.Json)
            setBody(AddTask(name = "", description = null, status = null, priority = null, deadline = null))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST tasks returns 400 when name is blank`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(),
    ) {
        val response = client.post("tasks") {
            contentType(ContentType.Application.Json)
            setBody(AddTask(name = "   ", description = null, status = null, priority = null, deadline = null))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST tasks trims leading and trailing whitespace from name`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(),
    ) {
        val response = client.post("tasks") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddTask(name = "  Buy milk  ", description = null, status = null, priority = null, deadline = null))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("Buy milk", response.body<ActiveTask>().name)
    }

    @Test
    fun `POST tasks returns 400 on malformed JSON`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(),
    ) {
        val response = client.post("tasks") {
            contentType(ContentType.Application.Json)
            setBody("{not valid json}")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST tasks returns 500 when repository fails`() = todoApp(
        taskRepositoryFactory = unimplementedTasksRepository,
    ) {
        val response = client.post("tasks") {
            contentType(ContentType.Application.Json)
            setBody(AddTask(name = "Test", description = null, status = null, priority = null, deadline = null))
        }
        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `POST tasks stores explicit status and priority`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(),
    ) {
        val response = client.post("tasks") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(AddTask(name = "Test", description = null, status = ApiTaskStatus.Done, priority = ApiTaskPriority.High, deadline = null))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val task = response.body<ActiveTask>()
        assertEquals(ApiTaskStatus.Done, task.status)
        assertEquals(ApiTaskPriority.High, task.priority)
    }

}
