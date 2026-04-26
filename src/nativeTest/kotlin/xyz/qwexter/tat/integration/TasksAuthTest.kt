package xyz.qwexter.tat.integration

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import xyz.qwexter.AuthMode
import xyz.qwexter.tat.models.Task
import xyz.qwexter.tat.models.TaskId
import xyz.qwexter.tat.models.TaskName
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus
import xyz.qwexter.tat.routing.ActiveTask
import xyz.qwexter.tat.routing.AddTask
import xyz.qwexter.tat.routing.ApiTaskStatus
import xyz.qwexter.tat.routing.UpdateTask
import xyz.qwexter.tat.utils.createTasksRepositoryInMemoryList
import xyz.qwexter.tat.utils.todoApp
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TasksAuthTest {

    private val now = Instant.parse("2026-01-01T10:00:01Z")

    private val userATask = Task(
        id = TaskId("task-a"),
        ownerId = "user-a",
        name = TaskName("User A task"),
        description = null,
        status = TaskStatus.Todo,
        priority = TaskPriority.Low,
        deadline = null,
        createdAt = now,
        updatedAt = null,
        deletedAt = null,
        groupId = null,
    )

    private val userBTask = Task(
        id = TaskId("task-b"),
        ownerId = "user-b",
        name = TaskName("User B task"),
        description = null,
        status = TaskStatus.Todo,
        priority = TaskPriority.Low,
        deadline = null,
        createdAt = now,
        updatedAt = null,
        deletedAt = null,
        groupId = null,
    )

    // --- Missing header ---

    @Test
    fun `GET tasks returns 401 when X-User-Id header is missing`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(),
        authMode = AuthMode.HEADER,
    ) {
        assertEquals(HttpStatusCode.Unauthorized, client.get("tasks").status)
    }

    @Test
    fun `GET task by id returns 401 when X-User-Id header is missing`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(),
        authMode = AuthMode.HEADER,
    ) {
        assertEquals(HttpStatusCode.Unauthorized, client.get("tasks/task-a").status)
    }

    @Test
    fun `POST tasks returns 401 when X-User-Id header is missing`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(),
        authMode = AuthMode.HEADER,
    ) {
        val response = client.post("tasks") {
            contentType(ContentType.Application.Json)
            setBody(AddTask(name = "Test", description = null, status = null, priority = null, deadline = null))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `PATCH task returns 401 when X-User-Id header is missing`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(listOf(userATask)),
        authMode = AuthMode.HEADER,
    ) {
        val response = client.patch("tasks/task-a") {
            contentType(ContentType.Application.Json)
            setBody(UpdateTask(status = ApiTaskStatus.Done))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `DELETE task returns 401 when X-User-Id header is missing`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(listOf(userATask)),
        authMode = AuthMode.HEADER,
    ) {
        assertEquals(HttpStatusCode.Unauthorized, client.delete("tasks/task-a").status)
    }

    // --- Ownership isolation ---

    @Test
    fun `GET tasks returns only caller's tasks`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(listOf(userATask, userBTask)),
        authMode = AuthMode.HEADER,
    ) {
        val response = client.get("tasks") { header("X-User-Id", "user-a") }
        val tasks = response.body<List<ActiveTask>>()

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, tasks.size)
        assertEquals("task-a", tasks.single().id)
    }

    @Test
    fun `GET tasks returns empty list when caller has no tasks`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(listOf(userATask)),
        authMode = AuthMode.HEADER,
    ) {
        val tasks = client.get("tasks") { header("X-User-Id", "user-b") }.body<List<ActiveTask>>()
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun `GET task by id returns 404 when task belongs to another user`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(listOf(userATask)),
        authMode = AuthMode.HEADER,
    ) {
        val response = client.get("tasks/task-a") { header("X-User-Id", "user-b") }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET task by id returns task when caller is owner`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(listOf(userATask)),
        authMode = AuthMode.HEADER,
    ) {
        val response = client.get("tasks/task-a") { header("X-User-Id", "user-a") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("task-a", response.body<ActiveTask>().id)
    }

    @Test
    fun `PATCH task returns 404 when task belongs to another user`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(listOf(userATask)),
        authMode = AuthMode.HEADER,
    ) {
        val response = client.patch("tasks/task-a") {
            header("X-User-Id", "user-b")
            contentType(ContentType.Application.Json)
            setBody(UpdateTask(status = ApiTaskStatus.Done))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `PATCH task succeeds when caller is owner`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(listOf(userATask)),
        authMode = AuthMode.HEADER,
    ) {
        val response = client.patch("tasks/task-a") {
            header("X-User-Id", "user-a")
            contentType(ContentType.Application.Json)
            setBody(UpdateTask(status = ApiTaskStatus.Done))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ApiTaskStatus.Done, response.body<ActiveTask>().status)
    }

    @Test
    fun `DELETE task returns 404 when task belongs to another user`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(listOf(userATask)),
        authMode = AuthMode.HEADER,
    ) {
        val response = client.delete("tasks/task-a") { header("X-User-Id", "user-b") }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `DELETE task succeeds when caller is owner`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(listOf(userATask)),
        authMode = AuthMode.HEADER,
    ) {
        val response = client.delete("tasks/task-a") { header("X-User-Id", "user-a") }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `POST tasks creates task owned by caller`() = todoApp(
        taskRepositoryFactory = createTasksRepositoryInMemoryList(),
        authMode = AuthMode.HEADER,
    ) {
        client.post("tasks") {
            header("X-User-Id", "user-a")
            contentType(ContentType.Application.Json)
            setBody(AddTask(name = "My task", description = null, status = null, priority = null, deadline = null))
        }

        // user-a sees it
        val aTasks = client.get("tasks") { header("X-User-Id", "user-a") }.body<List<ActiveTask>>()
        assertEquals(1, aTasks.size)

        // user-b does not
        val bTasks = client.get("tasks") { header("X-User-Id", "user-b") }.body<List<ActiveTask>>()
        assertTrue(bTasks.isEmpty())
    }
}
