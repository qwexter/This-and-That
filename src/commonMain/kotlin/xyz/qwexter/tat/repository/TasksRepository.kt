package xyz.qwexter.tat.repository

import kotlinx.datetime.LocalDateTime
import xyz.qwexter.tat.models.Task
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus

interface TasksRepository {
    suspend fun allActiveTasks(): List<Task>
    suspend fun createTask(
        name: String,
        description: String?,
        status: TaskStatus = TaskStatus.Todo,
        priority: TaskPriority = TaskPriority.Low,
        deadline: LocalDateTime?,
    )

    companion object Mock : TasksRepository {

        private val allTasks = mutableListOf<Task>()

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
}
