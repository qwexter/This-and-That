package xyz.qwexter.tat.routing

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.qwexter.tat.models.Group

internal fun Group.toApi(): ActiveGroup = ActiveGroup(
    id = this.id.id,
    title = this.title.title,
)

@Serializable
data class ActiveGroup(
    val id: String,
    val title: String,
)

@Serializable
data class AddGroup(
    val title: String,
)

@Serializable
data class UpdateGroup(
    val title: String,
)

@Serializable
sealed class AddGroupItem {
    @Serializable
    @SerialName("newTask")
    data class NewTask(
        val name: String,
        val description: String? = null,
        val priority: ApiTaskPriority = ApiTaskPriority.Low,
        val deadline: LocalDateTime? = null,
    ) : AddGroupItem()

    @Serializable
    @SerialName("newRecord")
    data class NewRecord(
        val title: String,
        val content: String? = null,
    ) : AddGroupItem()

    @Serializable
    @SerialName("existingTask")
    data class ExistingTask(val id: String) : AddGroupItem()

    @Serializable
    @SerialName("existingRecord")
    data class ExistingRecord(val id: String) : AddGroupItem()
}

@Serializable
data class AddGroupItemsRequest(val items: List<AddGroupItem>)

@Serializable
sealed class GroupItemResponse {
    @Serializable
    @SerialName("task")
    data class TaskResponse(
        val id: String,
        val groupId: String,
        val name: String,
        val description: String?,
        val status: ApiTaskStatus,
        val priority: ApiTaskPriority,
        val deadline: LocalDateTime?,
    ) : GroupItemResponse()

    @Serializable
    @SerialName("record")
    data class RecordResponse(
        val id: String,
        val groupId: String,
        val title: String,
        val content: String?,
    ) : GroupItemResponse()
}

@Serializable
data class AddGroupItemsResponse(val items: List<GroupItemResponse>)
