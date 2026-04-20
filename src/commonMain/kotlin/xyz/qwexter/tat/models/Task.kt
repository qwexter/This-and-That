package xyz.qwexter.tat.models

import kotlinx.datetime.LocalDateTime

value class TaskId(val id: String)

value class TaskName(val name: String)

enum class TaskPriority {
    Low,
    Medium,
    High,
}

enum class TaskStatus {
    Todo,
    Done,
}

data class Task(
    val id: TaskId,
    val name: TaskName,
    val description: String?,
    val status: TaskStatus = TaskStatus.Todo,
    val priority: TaskPriority = TaskPriority.Low,
    val deadline: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?,
    val deletedAt: LocalDateTime?,
)
