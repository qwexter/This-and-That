package xyz.qwexter.tat.models

import kotlinx.datetime.Instant

value class SpaceId(val id: String)

value class SpaceTitle(val title: String)

data class Space(
    val id: SpaceId,
    val ownerId: String,
    val title: SpaceTitle,
    val isPrivate: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant?,
    val deletedAt: Instant?,
)

data class SpaceMember(
    val userId: String,
    val createdAt: Instant,
)
