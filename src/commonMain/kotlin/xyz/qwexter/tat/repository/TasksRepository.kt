package xyz.qwexter.tat.repository

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import xyz.qwexter.tat.models.Task
import xyz.qwexter.tat.models.TaskId
import xyz.qwexter.tat.models.TaskName
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface TasksRepository {
    suspend fun allActiveTasks(): List<Task>
    suspend fun createTask(
        name: String,
        description: String?,
        status: TaskStatus = TaskStatus.Todo,
        priority: TaskPriority = TaskPriority.Low,
        deadline: LocalDateTime?,
    )

    companion object {
        fun buildInMemory(initial: List<Task>): TasksRepository = InMemory(initial)
    }
}

@OptIn(ExperimentalUuidApi::class)
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
    ) {
        allTasks += Task(
            id = TaskId(Uuid.random().toString()),
            name = TaskName(name),
            description = description,
            status = status,
            priority = priority,
            deadline = deadline,
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
            updatedAt = null,
            deletedAt = null,
        )
    }
}
