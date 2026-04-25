package xyz.qwexter.tat.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import xyz.qwexter.AuthMode

internal const val DEV_OWNER_ID = "dev-user"
internal const val USER_ID_HEADER = "X-User-Id"

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
