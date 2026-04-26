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
import xyz.qwexter.tat.models.GroupId
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

data class TaskUpdateParams(
    val name: String? = null,
    val description: String? = null,
    val status: TaskStatus? = null,
    val priority: TaskPriority? = null,
    val deadline: LocalDateTime? = null,
    val groupId: GroupId? = null,
    val clearGroup: Boolean = false,
)

interface TasksRepository {
    suspend fun allActiveTasks(ownerId: String): List<Task>

    suspend fun createTask(
        ownerId: String,
        name: String,
        description: String?,
        status: TaskStatus = TaskStatus.Todo,
        priority: TaskPriority = TaskPriority.Low,
        deadline: LocalDateTime?,
        groupId: GroupId? = null,
    ): Task

    suspend fun getTaskById(ownerId: String, taskId: TaskId): Task?

    suspend fun updateTask(ownerId: String, taskId: TaskId, params: TaskUpdateParams): Task?

    suspend fun deleteTask(ownerId: String, taskId: TaskId): Boolean

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

    override suspend fun allActiveTasks(ownerId: String): List<Task> = withContext(dbDispatcher) {
        db.tatDatabaseQueries.selectAllActiveByOwner(ownerId).executeAsList().map { it.toModel() }
    }

    override suspend fun createTask(
        ownerId: String,
        name: String,
        description: String?,
        status: TaskStatus,
        priority: TaskPriority,
        deadline: LocalDateTime?,
        groupId: GroupId?,
    ): Task = withContext(dbDispatcher) {
        val newTask = Task(
            id = TaskId(Uuid.random().toString()),
            name = TaskName(name),
            groupId = groupId,
            description = description,
            status = status,
            priority = priority,
            deadline = deadline,
            createdAt = Clock.System.now(),
            updatedAt = null,
            deletedAt = null,
            ownerId = ownerId,
        )
        db.tatDatabaseQueries.insertTask(
            id = newTask.id.id,
            owner_id = ownerId,
            group_id = groupId?.id,
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

    override suspend fun getTaskById(ownerId: String, taskId: TaskId): Task? = withContext(dbDispatcher) {
        val dbItem = db.tatDatabaseQueries.selectTaskById(taskId.id).executeAsOneOrNull() ?: return@withContext null
        if (dbItem.owner_id != ownerId) return@withContext null
        dbItem.toModel()
    }

    override suspend fun updateTask(ownerId: String, taskId: TaskId, params: TaskUpdateParams): Task? =
        withContext(dbDispatcher) {
            val current = db.tatDatabaseQueries.selectTaskById(taskId.id).executeAsOneOrNull()
                ?: return@withContext null
            if (current.owner_id != ownerId) return@withContext null
            val resolvedGroupId = when {
                params.clearGroup -> null
                params.groupId != null -> params.groupId.id
                else -> current.group_id
            }
            db.tatDatabaseQueries.updateTask(
                name = params.name ?: current.name,
                description = params.description ?: current.description,
                status = params.status?.toDb() ?: current.status,
                priority = params.priority?.toDb() ?: current.priority,
                deadline = params.deadline?.toInstant(TimeZone.UTC)?.toEpochMilliseconds() ?: current.deadline,
                group_id = resolvedGroupId,
                updated_at = Clock.System.now().toEpochMilliseconds(),
                id = taskId.id,
                owner_id = ownerId,
            )
            db.tatDatabaseQueries.selectTaskById(taskId.id).executeAsOneOrNull()?.toModel()
        }

    override suspend fun deleteTask(ownerId: String, taskId: TaskId): Boolean = withContext(dbDispatcher) {
        val existing = db.tatDatabaseQueries.selectTaskById(taskId.id).executeAsOneOrNull()
        if (existing == null || existing.task_deleted_at != null || existing.owner_id != ownerId) {
            return@withContext false
        }
        val now = Clock.System.now().toEpochMilliseconds()
        db.tatDatabaseQueries.softDeleteTask(
            task_deleted_at = now,
            updated_at = now,
            id = taskId.id,
            owner_id = ownerId,
        )
        true
    }
}

private fun xyz.qwexter.db.Task.toModel() = Task(
    id = TaskId(id),
    ownerId = owner_id,
    groupId = group_id?.let { GroupId(it) },
    name = TaskName(name),
    description = description,
    status = status.toTaskStatus(),
    priority = priority.toTaskPriority(),
    deadline = deadline?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC) },
    createdAt = Instant.fromEpochMilliseconds(created_at),
    updatedAt = updated_at?.let { Instant.fromEpochMilliseconds(it) },
    deletedAt = task_deleted_at?.let { Instant.fromEpochMilliseconds(it) },
)

private class InMemory(initial: List<Task>) : TasksRepository {

    private val allTasks = initial.toMutableList()

    override suspend fun allActiveTasks(ownerId: String): List<Task> {
        return allTasks.filter { it.ownerId == ownerId && it.deletedAt == null }.toList()
    }

    override suspend fun createTask(
        ownerId: String,
        name: String,
        description: String?,
        status: TaskStatus,
        priority: TaskPriority,
        deadline: LocalDateTime?,
        groupId: GroupId?,
    ): Task {
        val task = Task(
            id = TaskId(Uuid.random().toString()),
            ownerId = ownerId,
            groupId = groupId,
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

    override suspend fun getTaskById(ownerId: String, taskId: TaskId): Task? {
        return allTasks.find { it.id == taskId && it.ownerId == ownerId }
    }

    override suspend fun updateTask(ownerId: String, taskId: TaskId, params: TaskUpdateParams): Task? {
        val idx = allTasks.indexOfFirst { it.id == taskId && it.ownerId == ownerId && it.deletedAt == null }
        if (idx == -1) return null
        val current = allTasks[idx]
        val resolvedGroupId = when {
            params.clearGroup -> null
            params.groupId != null -> params.groupId
            else -> current.groupId
        }
        val updated = current.copy(
            name = if (params.name != null) TaskName(params.name) else current.name,
            description = params.description ?: current.description,
            status = params.status ?: current.status,
            priority = params.priority ?: current.priority,
            deadline = params.deadline ?: current.deadline,
            groupId = resolvedGroupId,
            updatedAt = Clock.System.now(),
        )
        allTasks[idx] = updated
        return updated
    }

    override suspend fun deleteTask(ownerId: String, taskId: TaskId): Boolean {
        val idx = allTasks.indexOfFirst { it.id == taskId && it.ownerId == ownerId && it.deletedAt == null }
        if (idx == -1) return false
        val now = Clock.System.now()
        allTasks[idx] = allTasks[idx].copy(deletedAt = now, updatedAt = now)
        return true
    }
}
