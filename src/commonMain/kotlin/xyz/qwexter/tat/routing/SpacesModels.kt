package xyz.qwexter.tat.routing

import kotlinx.serialization.Serializable
import xyz.qwexter.tat.models.Space
import xyz.qwexter.tat.models.SpaceMember

internal fun Space.toApi() = ActiveSpace(id = id.id, title = title.title, ownerId = ownerId, isPrivate = isPrivate)

internal fun SpaceMember.toApi() = ActiveSpaceMember(userId = userId)

@Serializable
data class ActiveSpace(
    val id: String,
    val title: String,
    val ownerId: String,
    val isPrivate: Boolean,
)

@Serializable
data class AddSpace(val title: String)

@Serializable
data class UpdateSpace(val title: String)

@Serializable
data class ActiveSpaceMember(val userId: String)

@Serializable
data class AddSpaceMember(val userId: String)
