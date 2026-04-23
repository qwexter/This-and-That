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

    suspend fun getTaskById(taskId: TaskId): Task?

    suspend fun updateTask(
        taskId: TaskId,
        name: String? = null,
        description: String? = null,
        status: TaskStatus? = null,
        priority: TaskPriority? = null,
        deadline: LocalDateTime? = null,
    ): Task?

    suspend fun deleteTask(taskId: TaskId): Boolean

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

    override suspend fun getTaskById(taskId: TaskId): Task? = withContext(dbDispatcher) {
        val dbItem = db.tatDatabaseQueries.selectTaskById(taskId.id).executeAsOneOrNull() ?: return@withContext null
        Task(
            id = TaskId(dbItem.id),
            name = TaskName(dbItem.name),
            description = dbItem.description,
            status = dbItem.status.toTaskStatus(),
            priority = dbItem.priority.toTaskPriority(),
            deadline = dbItem.deadline?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC) },
            createdAt = Instant.fromEpochMilliseconds(dbItem.created_at),
            updatedAt = dbItem.updated_at?.let { Instant.fromEpochMilliseconds(it) },
            deletedAt = dbItem.task_deleted_at?.let { Instant.fromEpochMilliseconds(it) },
        )
    }

    override suspend fun updateTask(
        taskId: TaskId,
        name: String?,
        description: String?,
        status: TaskStatus?,
        priority: TaskPriority?,
        deadline: LocalDateTime?,
    ): Task? = withContext(dbDispatcher) {
        val current = db.tatDatabaseQueries.selectTaskById(taskId.id).executeAsOneOrNull() ?: return@withContext null
        val updatedAt = Clock.System.now().toEpochMilliseconds()
        db.tatDatabaseQueries.updateTask(
            name = name ?: current.name,
            description = description ?: current.description,
            status = status?.toDb() ?: current.status,
            priority = priority?.toDb() ?: current.priority,
            deadline = deadline?.toInstant(TimeZone.UTC)?.toEpochMilliseconds() ?: current.deadline,
            updated_at = updatedAt,
            id = taskId.id,
        )
        db.tatDatabaseQueries.selectTaskById(taskId.id).executeAsOneOrNull()?.let { dbItem ->
            Task(
                id = TaskId(dbItem.id),
                name = TaskName(dbItem.name),
                description = dbItem.description,
                status = dbItem.status.toTaskStatus(),
                priority = dbItem.priority.toTaskPriority(),
                deadline = dbItem.deadline?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC) },
                createdAt = Instant.fromEpochMilliseconds(dbItem.created_at),
                updatedAt = dbItem.updated_at?.let { Instant.fromEpochMilliseconds(it) },
                deletedAt = dbItem.task_deleted_at?.let { Instant.fromEpochMilliseconds(it) },
            )
        }
    }

    override suspend fun deleteTask(taskId: TaskId): Boolean = withContext(dbDispatcher) {
        val existing = db.tatDatabaseQueries.selectTaskById(taskId.id).executeAsOneOrNull()
        if (existing == null || existing.task_deleted_at != null) return@withContext false
        val now = Clock.System.now().toEpochMilliseconds()
        db.tatDatabaseQueries.softDeleteTask(
            task_deleted_at = now,
            updated_at = now,
            id = taskId.id,
        )
        true
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

    override suspend fun getTaskById(taskId: TaskId): Task? {
        return allTasks.find { it.id == taskId }
    }

    override suspend fun updateTask(
        taskId: TaskId,
        name: String?,
        description: String?,
        status: TaskStatus?,
        priority: TaskPriority?,
        deadline: LocalDateTime?,
    ): Task? {
        val idx = allTasks.indexOfFirst { it.id == taskId && it.deletedAt == null }
        if (idx == -1) return null
        val current = allTasks[idx]
        val updated = current.copy(
            name = if (name != null) TaskName(name) else current.name,
            description = description ?: current.description,
            status = status ?: current.status,
            priority = priority ?: current.priority,
            deadline = deadline ?: current.deadline,
            updatedAt = Clock.System.now(),
        )
        allTasks[idx] = updated
        return updated
    }

    override suspend fun deleteTask(taskId: TaskId): Boolean {
        val idx = allTasks.indexOfFirst { it.id == taskId && it.deletedAt == null }
        if (idx == -1) return false
        val now = Clock.System.now()
        allTasks[idx] = allTasks[idx].copy(deletedAt = now, updatedAt = now)
        return true
    }
}
