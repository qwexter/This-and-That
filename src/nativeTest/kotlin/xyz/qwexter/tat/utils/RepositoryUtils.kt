package xyz.qwexter.tat.utils

import kotlinx.datetime.LocalDateTime
import xyz.qwexter.tat.models.Task
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus
import xyz.qwexter.tat.repository.TasksRepository

val unimplementedTasksRepository: TasksRepository = object : TasksRepository {
    override suspend fun allActiveTasks(): List<Task> {
        TODO("Not yet implemented")
    }

    override suspend fun createTask(
        name: String,
        description: String?,
        status: TaskStatus,
        priority: TaskPriority,
        deadline: LocalDateTime?,
    ) {
        TODO("Not yet implemented")
    }
}
