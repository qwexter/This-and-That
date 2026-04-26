package xyz.qwexter.tat.routing

import kotlinx.serialization.Serializable
import xyz.qwexter.tat.models.Record

internal fun Record.toApi(): ActiveRecord = ActiveRecord(
    id = this.id.id,
    groupId = this.groupId?.id,
    title = this.title.title,
    content = this.content,
)

@Serializable
data class ActiveRecord(
    val id: String,
    val groupId: String?,
    val title: String,
    val content: String?,
)

@Serializable
data class AddRecord(
    val title: String,
    val content: String? = null,
    val groupId: String? = null,
)

@Serializable
data class UpdateRecord(
    val title: String? = null,
    val content: String? = null,
    val groupId: String? = null,
    val clearGroup: Boolean = false,
)
