package xyz.qwexter.tat.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import xyz.qwexter.db.TatDatabase
import xyz.qwexter.tat.models.Task
import xyz.qwexter.tat.models.TaskId
import xyz.qwexter.tat.models.TaskName
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus
import kotlin.uuid.Uuid

internal fun String.toTaskStatus(): TaskStatus = when (this) {
    "Todo" -> TaskStatus.Todo
    "Done" -> TaskStatus.Done
    else -> error("Unknown task status in DB: '$this'")
}

internal fun String.toTaskPriority(): TaskPriority = when (this) {
    "Low" -> TaskPriority.Low
    "Medium" -> TaskPriority.Medium
    "High" -> TaskPriority.High
    else -> error("Unknown task priority in DB: '$this'")
}

internal fun TaskStatus.toDb(): String = when (this) {
    TaskStatus.Todo -> "Todo"
    TaskStatus.Done -> "Done"
}

internal fun TaskPriority.toDb(): String = when (this) {
    TaskPriority.Low -> "Low"
    TaskPriority.Medium -> "Medium"
    TaskPriority.High -> "High"
}

interface TasksRepository {
    suspend fun allActiveTasks(): List<Task>
    suspend fun createTask(
        name: String,
        description: String?,
        status: TaskStatus = TaskStatus.Todo,
        priority: TaskPriority = TaskPriority.Low,
        deadline: LocalDateTime?,
    ): Task

    companion object {
        fun buildInMemory(initial: List<Task>): TasksRepository = InMemory(initial)

        fun create(
            db: TatDatabase,
            dbDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
        ): TasksRepository = DbTaskRepository(
            db = db,
            dbDispatcher = dbDispatcher,
        )
    }
}

private class DbTaskRepository(
    private val db: TatDatabase,
    private val dbDispatcher: CoroutineDispatcher,
) : TasksRepository {

    override suspend fun allActiveTasks(): List<Task> = withContext(dbDispatcher) {
        db.tatDatabaseQueries.selectAllActive().executeAsList().map { dbTask ->
            Task(
                id = TaskId(dbTask.id),
                name = TaskName(dbTask.name),
                description = dbTask.description,
                status = dbTask.status.toTaskStatus(),
                priority = dbTask.priority.toTaskPriority(),
                deadline = dbTask.deadline?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC) },
                createdAt = Instant.fromEpochMilliseconds(dbTask.created_at),
                updatedAt = dbTask.updated_at?.let { Instant.fromEpochMilliseconds(it) },
                deletedAt = dbTask.task_deleted_at?.let { Instant.fromEpochMilliseconds(it) },
            )
        }
    }

    override suspend fun createTask(
        name: String,
        description: String?,
        status: TaskStatus,
        priority: TaskPriority,
        deadline: LocalDateTime?,
    ): Task = withContext(dbDispatcher) {
        val newTask = Task(
            id = TaskId(Uuid.random().toString()),
            name = TaskName(name),
            description = description,
            status = status,
            priority = priority,
            deadline = deadline,
            createdAt = Clock.System.now(),
            updatedAt = null,
            deletedAt = null,
        )
        db.tatDatabaseQueries.insertTask(
            id = newTask.id.id,
            name = newTask.name.name,
            description = newTask.description,
            priority = newTask.priority.toDb(),
            status = newTask.status.toDb(),
            deadline = newTask.deadline?.toInstant(TimeZone.UTC)?.toEpochMilliseconds(),
            created_at = newTask.createdAt.toEpochMilliseconds(),
            updated_at = newTask.updatedAt?.toEpochMilliseconds(),
        )
        newTask
    }
}

private class InMemory(initial: List<Task>) : TasksRepository {

    private val allTasks = initial.toMutableList()

    override suspend fun allActiveTasks(): List<Task> {
        return allTasks.filter { it.deletedAt == null }.toList()
    }

    override suspend fun createTask(
        name: String,
        description: String?,
        status: TaskStatus,
        priority: TaskPriority,
        deadline: LocalDateTime?,
    ): Task {
        val task = Task(
            id = TaskId(Uuid.random().toString()),
            name = TaskName(name),
            description = description,
            status = status,
            priority = priority,
            deadline = deadline,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.UTC).toInstant(TimeZone.UTC),
            updatedAt = null,
            deletedAt = null,
        )
        allTasks += task
        return task
    }
}
