package xyz.qwexter.tat.routing

import kotlinx.serialization.Serializable

@Serializable
data class SpaceInviteResponse(val token: String)

@Serializable
data class InviteInfoResponse(val spaceTitle: String)
