package xyz.qwexter.tat.routing

import kotlinx.serialization.Serializable
import xyz.qwexter.tat.models.Task
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus

internal fun Task.toApi(): ActiveTask = ActiveTask(
    id = this.id.id,
    name = this.name.name,
    description = this.description,
    status = this.status.toApi(),
    priority = this.priority.toApi(),
)

internal fun TaskStatus.toApi(): String = when (this) {
    TaskStatus.Todo -> "Todo"
    TaskStatus.Done -> "Done"
}

internal fun TaskPriority.toApi(): String = when (this) {
    TaskPriority.Low -> "Low"
    TaskPriority.Medium -> "Medium"
    TaskPriority.High -> "High"
}

@Serializable
data class ActiveTask(
    val id: String,
    val name: String,
    val description: String?,
    val status: String,
    val priority: String,
)
