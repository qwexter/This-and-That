package xyz.qwexter.tat.routing

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import xyz.qwexter.tat.models.Task
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus

@Serializable
enum class ApiTaskStatus { Todo, Done }

@Serializable
enum class ApiTaskPriority { Low, Medium, High }

internal fun TaskStatus.toApi(): ApiTaskStatus = when (this) {
    TaskStatus.Todo -> ApiTaskStatus.Todo
    TaskStatus.Done -> ApiTaskStatus.Done
}

internal fun TaskPriority.toApi(): ApiTaskPriority = when (this) {
    TaskPriority.Low -> ApiTaskPriority.Low
    TaskPriority.Medium -> ApiTaskPriority.Medium
    TaskPriority.High -> ApiTaskPriority.High
}

internal fun ApiTaskStatus.toDomain(): TaskStatus = when (this) {
    ApiTaskStatus.Todo -> TaskStatus.Todo
    ApiTaskStatus.Done -> TaskStatus.Done
}

internal fun ApiTaskPriority.toDomain(): TaskPriority = when (this) {
    ApiTaskPriority.Low -> TaskPriority.Low
    ApiTaskPriority.Medium -> TaskPriority.Medium
    ApiTaskPriority.High -> TaskPriority.High
}

internal fun Task.toApi(): ActiveTask = ActiveTask(
    id = this.id.id,
    groupId = this.groupId?.id,
    name = this.name.name,
    description = this.description,
    status = this.status.toApi(),
    priority = this.priority.toApi(),
    deadline = this.deadline,
)

@Serializable
data class ActiveTask(
    val id: String,
    val groupId: String?,
    val name: String,
    val description: String?,
    val status: ApiTaskStatus,
    val priority: ApiTaskPriority,
    val deadline: LocalDateTime?,
)

@Serializable
data class AddTask(
    val name: String,
    val description: String?,
    val status: ApiTaskStatus?,
    val priority: ApiTaskPriority?,
    val deadline: LocalDateTime?,
    val groupId: String? = null,
)

@Serializable
data class UpdateTask(
    val name: String? = null,
    val description: String? = null,
    val status: ApiTaskStatus? = null,
    val priority: ApiTaskPriority? = null,
    val deadline: LocalDateTime? = null,
    val groupId: String? = null,
    val clearGroup: Boolean = false,
)
