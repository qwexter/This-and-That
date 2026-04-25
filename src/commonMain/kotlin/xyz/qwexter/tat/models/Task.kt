package xyz.qwexter.tat.models

import kotlinx.datetime.Instant
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
    val ownerId: String,
    val name: TaskName,
    val description: String?,
    val status: TaskStatus = TaskStatus.Todo,
    val priority: TaskPriority = TaskPriority.Low,
    val deadline: LocalDateTime?,
    val createdAt: Instant,
    val updatedAt: Instant?,
    val deletedAt: Instant?,
)
