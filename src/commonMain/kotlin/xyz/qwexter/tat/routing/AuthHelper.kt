package xyz.qwexter.tat.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import xyz.qwexter.AuthMode
import xyz.qwexter.tat.models.SpaceId
import xyz.qwexter.tat.repository.SpacesRepository

internal const val DEV_OWNER_ID = "dev-user"
internal const val DEV_DISPLAY_NAME = "Dev User"
internal const val USER_ID_HEADER = "X-Auth-Request-User"
internal const val USER_NAME_HEADER = "X-Auth-Request-Email"

internal suspend fun ApplicationCall.resolveOwnerId(authMode: AuthMode): String? = when (authMode) {
    AuthMode.NONE -> DEV_OWNER_ID
    AuthMode.HEADER -> {
        val id = request.headers[USER_ID_HEADER]
        if (id.isNullOrBlank()) {
            respond(HttpStatusCode.Unauthorized)
            null
        } else {
            id
        }
    }
}

internal suspend fun ApplicationCall.resolveOwnerIdWithPrivateSpace(
    authMode: AuthMode,
    spacesRepository: SpacesRepository,
): String? {
    val ownerId = resolveOwnerId(authMode) ?: return null
    spacesRepository.getOrCreatePrivateSpace(ownerId)
    return ownerId
}

internal suspend fun ApplicationCall.validateSpaceAccess(
    spacesRepository: SpacesRepository,
    spaceId: SpaceId,
    userId: String,
): Boolean {
    if (!spacesRepository.hasAccess(spaceId, userId)) {
        respond(HttpStatusCode.Forbidden)
        return false
    }
    return true
}

internal fun ApplicationCall.resolveDisplayName(authMode: AuthMode, userId: String): String = when (authMode) {
    AuthMode.NONE -> DEV_DISPLAY_NAME
    AuthMode.HEADER -> request.headers[USER_NAME_HEADER]?.takeIf { it.isNotBlank() } ?: userId
}
