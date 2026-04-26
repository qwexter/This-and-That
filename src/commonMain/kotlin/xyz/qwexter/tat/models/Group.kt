package xyz.qwexter.tat.models

import kotlinx.datetime.Instant

value class GroupId(val id: String)

value class GroupTitle(val title: String)

data class Group(
    val id: GroupId,
    val ownerId: String,
    val spaceId: SpaceId?,
    val title: GroupTitle,
    val createdAt: Instant,
    val updatedAt: Instant?,
    val deletedAt: Instant?,
)
