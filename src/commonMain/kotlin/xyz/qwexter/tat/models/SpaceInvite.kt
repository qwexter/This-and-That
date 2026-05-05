package xyz.qwexter.tat.models

import kotlinx.datetime.Instant

data class SpaceInvite(
    val token: String,
    val spaceId: SpaceId,
    val createdBy: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val maxUses: Int?,
    val useCount: Int,
)
